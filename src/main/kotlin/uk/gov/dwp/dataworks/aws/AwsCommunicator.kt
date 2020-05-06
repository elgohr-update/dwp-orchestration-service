package uk.gov.dwp.dataworks.aws

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest
import software.amazon.awssdk.services.ecs.model.DeleteServiceRequest
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.RunTaskRequest
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.TaskOverride
import software.amazon.awssdk.services.ecs.model.TaskDefinition
import software.amazon.awssdk.services.ecs.model.ContainerDefinition
import software.amazon.awssdk.services.ecs.model.NetworkMode
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action
import software.amazon.awssdk.services.elasticloadbalancingv2.model.CreateRuleRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.CreateTargetGroupRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DeleteRuleRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DeleteTargetGroupRequest
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
import software.amazon.awssdk.services.iam.model.DeletePolicyRequest
import software.amazon.awssdk.services.iam.model.DeleteRoleRequest
import software.amazon.awssdk.services.iam.model.DetachRolePolicyRequest
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
    fun createTargetGroup(correlationId: String, vpcId: String, targetGroupName: String, targetPort: Int): TargetGroup {
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
                "correlation_id" to correlationId,
                "vpc_id" to vpcId,
                "target_group_arn" to targetGroup.targetGroupArn(),
                "protocol" to targetGroup.protocolAsString(),
                "load_balancer_arns" to targetGroup.loadBalancerArns().joinToString(),
                "target_group_name" to targetGroupName,
                "target_port" to targetPort.toString())
        return targetGroup
    }

    /**
     * Deletes the [TargetGroup] with the specified [targetGroupArn]
     */
    fun deleteTargetGroup(correlationId: String, targetGroupArn: String) {
        val deleteRequest = DeleteTargetGroupRequest.builder().targetGroupArn(targetGroupArn).build()
        awsClients.albClient.deleteTargetGroup(deleteRequest)
        logger.info("Deleted target group","correlation_id" to correlationId, "target_group_arn" to targetGroupArn)
    }

    /**
     * Creates a [LoadBalancer] routing rule for the [Listener] with given [listenerArn] and [TargetGroup]
     * of given [targetGroupArn].
     * The rule created will be a path-pattern forwarder based on [pathPattern].
     *
     * **See Also:** [AWS docs](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html)
     */
    fun createAlbRoutingRule(correlationId: String, listenerArn: String, targetGroupArn: String, pathPattern: String): Rule {
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
                "correlation_id" to correlationId,
                "actions" to rule.actions().joinToString(),
                "priority" to rule.priority(),
                "rule_arn" to rule.ruleArn(),
                "conditions" to rule.conditions().joinToString())
        return rule
    }

    /**
     * Deleted the [Rule] with the specified [ruleArn]
     */
    fun deleteAlbRoutingRule(correlationId: String, ruleArn: String) {
        awsClients.albClient.deleteRule(DeleteRuleRequest.builder().ruleArn(ruleArn).build())
        logger.info("Deleted alb routing rule", "correlation_id" to correlationId, "rule_arn" to ruleArn )
    }

    /**
     * Helper method to calculate the lowest non-used priority in a given set of [Rules][Rule]. This excludes
     * the `default` rule and assumes that all rules have come from the same listener.
     */
    fun calculateVacantPriorityValue(rules: Iterable<Rule>): Int {
        val rulePriorities = rules.map { it.priority() }.filter { it != "default" }.map { Integer.parseInt(it) }.toSet()
        for (priority in 1..999) {
            if (!rulePriorities.contains(priority)) return priority
        }
        throw UpperRuleLimitReachedException("The upper limit of 1000 rules has been reached on this listener.")
    }


    /**
     * Creates an ECS service with the name [clusterName], friendly service name of [serviceName] and sits
     * it behind the load balancer [loadBalancer].
     *
     * For ease of use, the task definition is retrieved from the [task definition env var][ConfigKey.USER_CONTAINER_TASK_DEFINITION]
     */
    fun createEcsService(correlationId: String, clusterName: String, serviceName: String, taskDefinitionArn: String, loadBalancer: EcsLoadBalancer): Service {
        // Create ECS service request
        val serviceBuilder = CreateServiceRequest.builder()
                .cluster(clusterName)
                .loadBalancers(loadBalancer)
                .serviceName(serviceName)
                .taskDefinition(taskDefinitionArn)
                .desiredCount(1)
                .build()

        //Create the service
        val ecsService = awsClients.ecsClient.createService(serviceBuilder).service()
        logger.info("Created ECS Service",
                "correlation_id" to correlationId,
                "cluster_name" to clusterName,
                "service_name" to serviceName,
                "cluster_arn" to ecsService.clusterArn(),
                "task_definition" to ecsService.taskDefinition())
        return ecsService
    }

    /**
     * Deletes the [Service] from the cluster [clusterName] with the name [serviceName]
     */
    fun deleteEcsService(correlationId: String, clusterName: String, serviceName: String) {
        awsClients.ecsClient.deleteService(
                DeleteServiceRequest.builder()
                        .cluster(clusterName)
                        .service(serviceName).build())
        logger.info("Deleted ECS Service",
                "correlation_id" to correlationId,
                "cluster_name" to clusterName,
                "service_name" to serviceName)
    }

    /**
     * Registers a new [TaskDefinition] with family name [family], execution role [executionRoleArn],
     * task role [taskRoleArn], networking mode [networkMode] (see [NetworkMode]) and container
     * definitions [containerDefinitions] (see [ContainerDefinition])
     */
    fun registerTaskDefinition(correlationId: String, family: String, executionRoleArn: String, taskRoleArn: String, networkMode: NetworkMode, containerDefinitions: Collection<ContainerDefinition>): TaskDefinition {
        val registerTaskDefinitionRequest = RegisterTaskDefinitionRequest.builder()
                .family(family)
                .executionRoleArn(executionRoleArn)
                .taskRoleArn(taskRoleArn)
                .networkMode(networkMode)
                .containerDefinitions(containerDefinitions)
                .build()

        logger.info("Registering task definition",
                "correlation_id" to correlationId,
                "family" to family,
                "execution_role_arn" to executionRoleArn,
                "task_role_arn" to taskRoleArn,
                "network_mode" to networkMode.toString(),
                "container_definitions" to containerDefinitions.joinToString("; ", transform = { def ->
                    val env = def.environment().joinToString { "${it.name()}:${it.value()}" }
                    "${def.name()}, image=${def.image()}, cpu = ${def.cpu()}, memory= ${def.memory()} env = [${env}]"
                }))

        return awsClients.ecsClient.registerTaskDefinition(registerTaskDefinitionRequest).taskDefinition()
    }

    /**
     * Creates an IAM [Policy] from the name and document provided. [policyDocument] should be in JSON format
     * as per the AWS standards for documents.
     */
    fun createIamPolicy(correlationId: String, policyName: String, policyDocument: String): Policy {
        val policy = awsClients.iamClient.createPolicy(
                CreatePolicyRequest.builder()
                        .policyDocument(policyDocument)
                        .policyName(policyName).build())
                .policy()
        logger.info("Created iam policy",
                "correlation_id" to correlationId,
                "policy_arn" to policy.arn(),
                "policy_path" to policy.path(),
                "policy_name" to policy.policyName(),
                "policy_id" to policy.policyId(),
                "created_date" to policy.createDate().toString())
        return policy
    }

    /**
     * Deletes the [Policy] with the specified [policyArn]
     */
    fun deleteIamPolicy(correlationId: String, policyArn: String) {
        awsClients.iamClient.deletePolicy(DeletePolicyRequest.builder().policyArn(policyArn).build())
        logger.info("Deleted iam policy", "correlation_id" to correlationId, "policy_arn" to policyArn)
    }

    /**
     * Creates an IAM [Role] from the name and role assumption document provided. [assumeRolePolicy] should be
     * in JSON format as per the AWS standards for documents.
     */
    fun createIamRole(correlationId: String, roleName: String, assumeRolePolicy: String): Role {
        val role = awsClients.iamClient.createRole(
                CreateRoleRequest.builder()
                        .assumeRolePolicyDocument(assumeRolePolicy)
                        .roleName(roleName).build())
                .role()
        logger.info("Created iam role",
                "correlation_id" to correlationId,
                "role_arn" to role.arn(),
                "role_path" to role.path(),
                "role_name" to role.roleName(),
                "role_id" to role.roleId(),
                "created_date" to role.createDate().toString())
        return role
    }

    /**
     * Deletes the [Role] with the specified [roleName]
     */
    fun deleteIamRole(correlationId: String, roleName: String) {
        awsClients.iamClient.deleteRole(DeleteRoleRequest.builder().roleName(roleName).build())
        logger.info("Deleted iam role", "correlation_id" to correlationId, "role_name" to roleName)
    }

    /**
     * Attaches [policy] to [role] using the [Policy] ARN and [Role] name.
     */
    fun attachIamPolicyToRole(correlationId: String, policy: Policy, role: Role) {
        awsClients.iamClient.attachRolePolicy(AttachRolePolicyRequest.builder()
                .policyArn(policy.arn())
                .roleName(role.roleName()).build())
        logger.info("Attached policy to role", "correlation_id" to correlationId, "policy_arn" to policy.arn(), "role_name" to role.roleName())
    }

    fun detachIamPolicyFromRole(correlationId: String, roleName: String, policyArn: String) {
        awsClients.iamClient.detachRolePolicy(DetachRolePolicyRequest.builder()
                .roleName(roleName)
                .policyArn(policyArn).build())
        logger.info("Detached policy from role", "correlation_id" to correlationId, "role_name" to roleName, "policy_arn" to policyArn)
    }

    fun createDynamoDbTable(tableName: String, attributes: List<AttributeDefinition>, keyName: String) {
        val tables = awsClients.dynamoDbClient.listTables(ListTablesRequest.builder().build())
        if(!tables.tableNames().contains(tableName)) {
            awsClients.dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(tableName)
                    .attributeDefinitions(attributes)
                    .keySchema(KeySchemaElement.builder().attributeName(keyName).keyType(KeyType.HASH).build())
                    .build())
            logger.info("Created dynamodb table", "table_name" to tableName)
        }
    }

    fun putDynamoDbItem(correlationId: String, dynamoTableName: String, attributes: Map<String, AttributeValue>) {
        awsClients.dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(dynamoTableName)
                .item(attributes).build())
        logger.info("User tasks registered in dynamodb", "correlation_id" to correlationId)
    }

    fun getDynamoDbItem(dynamoTableName: String, dynamoPrimaryKey: String, value: String): GetItemResponse {
        val retrievalKey = mapOf(dynamoPrimaryKey to AttributeValue.builder().s(value).build())
        return awsClients.dynamoDbClient.getItem(
                GetItemRequest.builder()
                        .tableName(dynamoTableName)
                        .key(retrievalKey)
                        .build())
    }

    fun removeDynamoDbItem(correlationId: String, dynamoTableName: String, dynamoPrimaryKey: String, userName: String) {
        awsClients.dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                .tableName(dynamoTableName)
                .key(mapOf(dynamoPrimaryKey to AttributeValue.builder().s(userName).build()))
                .build())
        logger.info("User tasks deregistered in dynamodb",
                "correlation_id" to correlationId,
                "user_name" to userName)
    }
}
