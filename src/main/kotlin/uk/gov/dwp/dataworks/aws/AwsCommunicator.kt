package uk.gov.dwp.dataworks.aws

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.RunTaskRequest
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.TaskOverride
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action
import software.amazon.awssdk.services.elasticloadbalancingv2.model.CreateRuleRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.CreateTargetGroupRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.model.PathPatternConditionConfig
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule
import software.amazon.awssdk.services.elasticloadbalancingv2.model.RuleCondition
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest
import software.amazon.awssdk.services.iam.model.CreatePolicyRequest
import software.amazon.awssdk.services.iam.model.CreateRoleRequest
import software.amazon.awssdk.services.iam.model.Policy
import software.amazon.awssdk.services.iam.model.Role
import uk.gov.dwp.dataworks.MultipleListenersMatchedException
import uk.gov.dwp.dataworks.MultipleLoadBalancersMatchedException
import uk.gov.dwp.dataworks.UpperRuleLimitReachedException
import uk.gov.dwp.dataworks.logging.DataworksLogger
import uk.gov.dwp.dataworks.services.ConfigKey
import uk.gov.dwp.dataworks.services.ConfigurationResolver
import software.amazon.awssdk.services.ecs.model.LoadBalancer as EcsLoadBalancer

/**
 * Component Class to encapsulate any and all communications with AWS via the SDK API. This class allows separation of
 * duties, where any AWS communications are stored and handled.
 *
 * Ideally this will leave the remaining code in the repo to be clean and concise, as much processing is outsorced to
 * this component.
 *
 * Unit testing also becomes easier, as this component can be mocked and all AWS calls can be synthetically manipulated.
 */
@Component
class AwsCommunicator {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(AwsCommunicator::class.java))
    }

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver
    @Autowired
    private lateinit var awsClients: AwsClients

    /**
     * Retrieve a [LoadBalancer] from AWS given it's name. If multiple LoadBalancers are found with the same
     * name, an exception will be thrown.
     * @throws MultipleLoadBalancersMatchedException when more than one Load balancer is located with name [albName]
     */
    fun getLoadBalancerByName(albName: String): LoadBalancer {
        val albs = awsClients.albClient.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(albName).build())
                .loadBalancers()
        if (albs.size == 1) return albs[0]
        else throw MultipleLoadBalancersMatchedException("Expected to find 1 Load Balancer with name $albName, actually found ${albs.size}")
    }

    /**
     * Retrieves a [Listener] from a [LoadBalancer] based on the name of the ALB and the port of the listener.
     * Note that, for simplicity, this method expects only a single binding on the listener and will always
     * return the first one found.
     * @throws MultipleListenersMatchedException when more than one Listener is located matching port [listenerPort]
     */
    fun getAlbListenerByPort(loadBalancerArn: String, listenerPort: Int): Listener {
        val albListeners = awsClients.albClient.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build())
        val matchedListeners = albListeners.listeners().filter { it.port() == listenerPort }
        return matchedListeners
                .elementAtOrElse(0) { throw MultipleListenersMatchedException("Expected to find 1 Listener with port $listenerPort, actually found ${matchedListeners.size}") }
    }

    /**
     * Creates and returns a [TargetGroup] in the given VPC. This target group can later be assigned to
     * a [LoadBalancer] using it's ARN or [ElasticLoadBalancingV2Client.registerTargets].
     */
    fun createTargetGroup(vpcId: String, targetGroupName: String, targetPort: Int): TargetGroup {
        // Create HTTPS target group in VPC to port containerPort
        val targetGroupResponse = awsClients.albClient.createTargetGroup(
                CreateTargetGroupRequest.builder()
                        .name(targetGroupName)
                        .protocol("HTTPS")
                        .vpcId(vpcId)
                        .port(targetPort)
                        .build())
        val targetGroup = targetGroupResponse.targetGroups().first { it.port() == targetPort }
        logger.info("Created target group",
                "vpc_id" to vpcId,
                "target_group_arn" to targetGroup.targetGroupArn(),
                "protocol" to targetGroup.protocolAsString(),
                "load_balancer_arns" to targetGroup.loadBalancerArns().joinToString(),
                "target_group_name" to targetGroupName,
                "target_port" to targetPort.toString())
        return targetGroup
    }

    /**
     * Creates a [LoadBalancer] routing rule for the [Listener] with given [listenerArn] and [TargetGroup]
     * of given [targetGroupArn].
     * The rule created will be a path-pattern forwarder based on [pathPattern].
     *
     * **See Also:** [AWS docs](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html)
     */
    fun createAlbRoutingRule(listenerArn: String, targetGroupArn: String, pathPattern: String) {
        // Build path pattern condition
        val albRuleCondition = RuleCondition.builder()
                .field("path-pattern")
                .pathPatternConfig(PathPatternConditionConfig.builder().values(pathPattern).build())
                .build()

        //Set up forwarding rule
        val albRuleAction = Action.builder().type("forward").targetGroupArn(targetGroupArn).build()

        //Get rules on listener & calculate vacant priority.
        val albRules = awsClients.albClient.describeRules(DescribeRulesRequest.builder().listenerArn(listenerArn).build())
        val rulePriority = calculateVacantPriorityValue(albRules.rules())

        //Create the complete rule
        val rule = awsClients.albClient.createRule(CreateRuleRequest.builder()
                .listenerArn(listenerArn)
                .priority(rulePriority)
                .conditions(albRuleCondition)
                .actions(albRuleAction)
                .build()).rules()[0]
        logger.info("Created alb routing rule",
                "actions" to rule.actions().joinToString(),
                "priority" to rule.priority(),
                "arn" to rule.ruleArn(),
                "conditions" to rule.conditions().joinToString())
    }

    /**
     * Helper method to calculate the lowest non-used priority in a given set of [Rules][Rule]. This excludes
     * the `default` rule and assumes that all rules have come from the same listener.
     */
    fun calculateVacantPriorityValue(rules: Iterable<Rule>): Int {
        val rulePriorities = rules.map { it.priority() }.filter { it != "default" }.map { Integer.parseInt(it) }.toSet()
        for (priority in 0..999) {
            if (!rulePriorities.contains(priority)) return priority
        }
        throw UpperRuleLimitReachedException("The upper limit of 1000 rules has been reached on this listener.")
    }

    /**
     * Helper method to wrap a container name and set of overrides into an incomplete [ContainerOverride.Builder] for
     * later consumption.
     */
    fun buildContainerOverride(containerName: String, vararg overrides: Pair<String, String>): ContainerOverride.Builder {
        val overrideKeyPairs = overrides.map { KeyValuePair.builder().name(it.first).value(it.second).build() }
        logger.info("Overriding container args",
                "container_name" to containerName,
                "overrides" to overrideKeyPairs.joinToString { "${it.name()}:${it.value()}" })
        return ContainerOverride.builder()
                .name(containerName)
                .environment(overrideKeyPairs)
    }

    /**
     * Creates an ECS service with the name [clusterName], friendly service name of [serviceName] and sits
     * it behind the load balancer [loadBalancer].
     *
     * For ease of use, the task definition is retrieved from the [task definition env var][ConfigKey.USER_CONTAINER_TASK_DEFINITION]
     */
    fun createEcsService(clusterName: String, serviceName: String, loadBalancer: EcsLoadBalancer): Service {
        // Create ECS service request
        val serviceBuilder = CreateServiceRequest.builder()
                .cluster(clusterName)
                .loadBalancers(loadBalancer)
                .serviceName(serviceName)
                .taskDefinition(configurationResolver.getStringConfig(ConfigKey.USER_CONTAINER_TASK_DEFINITION))
                .desiredCount(1)
                .build()

        //Create the service
        val ecsService = awsClients.ecsClient.createService(serviceBuilder).service()
        logger.info("Created ECS Service",
                "cluster_name" to clusterName,
                "service_name" to serviceName,
                "cluster_arn" to ecsService.clusterArn(),
                "task_definition" to ecsService.taskDefinition())
        return ecsService
    }

    /**
     * Constructs a [RunTaskRequest] with Task Overrides using the passed in parameters. [TaskOverrides][TaskOverride]
     * are constructed from the [overrides] and applied to the ECS task definition [taskDefinition].
     * ECS tasks will be run on EC2 instances.
     *
     * @param taskDefinition  The family and revision (family:revision) or full ARN of the task definition to run.
     * If a revision is not specified, the latest ACTIVE revision is used.
     */
    fun buildEcsTask(ecsClusterName: String, taskDefinition: String, taskRoleArn: String, overrides: Collection<ContainerOverride>): RunTaskRequest {
        val taskOverride = TaskOverride.builder()
                .containerOverrides(overrides)
                .taskRoleArn(taskRoleArn)
                .build()

        return RunTaskRequest.builder()
                .cluster(ecsClusterName)
                .launchType("EC2")
                .overrides(taskOverride)
                .taskDefinition(taskDefinition)
                .build()
    }

    /**
     * Runs the specified [RunTaskRequest]
     */
    fun runEcsTask(taskRequest: RunTaskRequest) {
        val task = awsClients.ecsClient.runTask(taskRequest).tasks()[0]
        logger.info("ECS tasks run",
                "instance_arns" to task.containerInstanceArn(),
                "task_groups" to task.group(),
                "cluster_arn" to task.clusterArn(),
                "cpus" to task.cpu(),
                "memory" to task.memory(),
                "platform_version" to task.platformVersion(),
                "started_at" to task.startedAt().toString())
    }

    /**
     * Creates an IAM [Policy] from the name and document provided. [policyDocument] should be in JSON format
     * as per the AWS standards for documents.
     */
    fun createIamPolicy(policyName: String, policyDocument: String): Policy {
        val policy = awsClients.iamClient.createPolicy(
                CreatePolicyRequest.builder()
                        .policyDocument(policyDocument)
                        .policyName(policyName).build())
                .policy()
        logger.info("Created iam policy",
                "policy_arn" to policy.arn(),
                "policy_path" to policy.path(),
                "policy_name" to policy.policyName(),
                "policy_id" to policy.policyId(),
                "created_date" to policy.createDate().toString())
        return policy
    }

    /**
     * Creates an IAM [Role] from the name and role assumption document provided. [assumeRolePolicy] should be
     * in JSON format as per the AWS standards for documents.
     */
    fun createIamRole(roleName: String, assumeRolePolicy: String): Role {
        val role = awsClients.iamClient.createRole(
                CreateRoleRequest.builder()
                        .assumeRolePolicyDocument(assumeRolePolicy)
                        .roleName(roleName).build())
                .role()
        logger.info("Created iam role",
                "role_arn" to role.arn(),
                "role_path" to role.path(),
                "role_name" to role.roleName(),
                "role_id" to role.roleId(),
                "created_date" to role.createDate().toString())
        return role
    }

    /**
     * Attaches [policy] to [role] using the [Policy] ARN and [Role] name.
     */
    fun attachIamPolicyToRole(policy: Policy, role: Role) {
        awsClients.iamClient.attachRolePolicy(AttachRolePolicyRequest.builder()
                .policyArn(policy.arn())
                .roleName(role.roleName()).build())
        logger.info("Attched policy to role", "policy_arn" to policy.arn(), "role_name" to role.roleName())
    }
}
