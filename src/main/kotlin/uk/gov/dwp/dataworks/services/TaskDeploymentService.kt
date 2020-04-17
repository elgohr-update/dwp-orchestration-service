package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import software.amazon.awssdk.services.ecs.model.RunTaskRequest
import software.amazon.awssdk.services.ecs.model.TaskOverride
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action
import software.amazon.awssdk.services.elasticloadbalancingv2.model.CreateRuleRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.CreateTargetGroupRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ForwardActionConfig
import software.amazon.awssdk.services.elasticloadbalancingv2.model.PathPatternConditionConfig
import software.amazon.awssdk.services.elasticloadbalancingv2.model.RuleCondition
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupTuple
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest
import software.amazon.awssdk.services.iam.model.CreatePolicyRequest
import software.amazon.awssdk.services.iam.model.CreateRoleRequest
import uk.gov.dwp.dataworks.FailedToExecuteCreateServiceRequestException
import uk.gov.dwp.dataworks.FailedToExecuteRunTaskRequestException
import uk.gov.dwp.dataworks.UpperRuleLimitReachedException
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Service
class TaskDeploymentService {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(TaskDeploymentService::class.java))
    }

    val configurationService = ConfigurationService()

    private fun createService(ecsClusterName: String, userName: String, ecsClient: EcsClient, containerPort: Int, targetGroupArn: String) {
        val alb: LoadBalancer = LoadBalancer.builder().targetGroupArn(targetGroupArn).containerPort(containerPort).build()
        val serviceBuilder = CreateServiceRequest.builder().cluster(ecsClusterName).loadBalancers(alb).serviceName("${userName}-analytical-workspace").taskDefinition(configurationService.getStringConfig(ConfigKey.USER_CONTAINER_TASK_DEFINITION)).loadBalancers(alb).desiredCount(1).build()

        try {
            val service = ecsClient.createService(serviceBuilder).service()
            logger.info("Created ECS Service", "cluster_arn" to  service.clusterArn(), "service_name" to service.serviceName(), "task_definition" to service.taskDefinition())
        } catch (e: Exception) {
            logger.error("Failed to create ECS Service", e, "cluster_arn" to serviceBuilder.cluster(), "service_name" to serviceBuilder.serviceName(), "task_definition" to serviceBuilder.taskDefinition())
            throw FailedToExecuteCreateServiceRequestException("Error while processing the Create Service request", e)
        }
    }

    fun getVacantPriorityValue(rulesResponse: DescribeRulesResponse): Int {
        val rulePriorities = rulesResponse.rules().map { it.priority() }.filter { it != "default" }.map { Integer.parseInt(it) }.toSet()
        for (priority in 0..999) {
            if (!rulePriorities.contains(priority)) return priority
        }
        throw UpperRuleLimitReachedException("The upper limit of 1000 rules has been reached on this listener.")
    }

    fun taskDefinitionWithOverride(ecsClusterName: String, emrClusterHostName: String, albName :String, userName: String, containerPort : Int , jupyterCpu : Int , jupyterMemory: Int, additionalPermissions: List<String>) {

        val ecsClient = EcsClient.builder().region(configurationService.awsRegion).build()
        val albClient = ElasticLoadBalancingV2Client.builder().region(configurationService.awsRegion).build()

        val albRequest = DescribeLoadBalancersRequest.builder().names(albName).build()
        val albResponse = albClient.describeLoadBalancers(albRequest)

        val albListenerRequest = DescribeListenersRequest.builder().loadBalancerArn(albResponse.loadBalancers()[0].loadBalancerArn()).build()
        val albListenerResponse = albClient.describeListeners(albListenerRequest)

        val tgRequest = CreateTargetGroupRequest.builder().name("$userName-target-group").protocol("HTTPS").vpcId(albResponse.loadBalancers()[0].vpcId()).port(containerPort).build()
        val targetGroupResponse = albClient.createTargetGroup(tgRequest)

        logger.info("Created target groups", "target_groups" to targetGroupResponse.targetGroups().joinToString{ it.targetGroupName() })

        val albTargetGroupRequest = albClient.describeTargetGroups(DescribeTargetGroupsRequest.builder().names("$userName-target-group").build())
        val albTargetGroupArn = albTargetGroupRequest.targetGroups()[0].targetGroupArn()

        val pathPattern = PathPatternConditionConfig.builder().values("/$userName/*").build()
        val albRuleCondition = RuleCondition.builder().field("path-pattern").pathPatternConfig(pathPattern).build()
        val userTargetGroup = TargetGroupTuple.builder().targetGroupArn(albTargetGroupArn).build()
        val forwardAction = ForwardActionConfig.builder().targetGroups(userTargetGroup).build()
        val albRuleAction = Action.builder().type("forward").forwardConfig(forwardAction).build()

        val rulesResponse = albClient.describeRules(DescribeRulesRequest.builder().listenerArn(albListenerResponse.listeners()[0].listenerArn()).build())
        albClient.createRule(CreateRuleRequest.builder().listenerArn(albListenerResponse.listeners()[0].listenerArn()).priority(getVacantPriorityValue(rulesResponse)).conditions(albRuleCondition).actions(albRuleAction).build())

        createService(ecsClusterName, userName, ecsClient, containerPort, albTargetGroupArn)

        try {
            val response = ecsClient.runTask(createRunTaskRequestWithOverrides(userName,emrClusterHostName,jupyterMemory,jupyterCpu,ecsClusterName,additionalPermissions))
            logger.info("ECS tasks run", "instance_arns" to response.tasks().joinToString { it.containerInstanceArn() }, "task_groups" to response.tasks().joinToString { it.group() })
        } catch (e: Exception) {
            logger.error("Error running ECS tasks", e)
            throw FailedToExecuteRunTaskRequestException("Error while processing the Run Task request", e)
        }
    }
    
    private fun createRunTaskRequestWithOverrides(userName: String, emrHostname: String, jupyterMemory: Int, jupyterCpu: Int, ecsClusterName: String, additionalPermissions: List<String>): RunTaskRequest {
        val usernamePair = "USER" to userName
        val hostnamePair = "EMR_HOST_NAME" to emrHostname

        logger.info("Overriding container env vars", usernamePair, hostnamePair)

        val chrome = containerOverrideBuilder("headless_chrome", usernamePair).build()
        val guacd = containerOverrideBuilder("guacd", usernamePair).build()
        // Jupyter also has configurable resources
        val jupyter = containerOverrideBuilder("jupyterHub", usernamePair, hostnamePair).cpu(jupyterCpu).memory(jupyterMemory).build()

        val overrides = TaskOverride.builder()
                .containerOverrides(guacd, chrome, jupyter)
                .taskRoleArn(createTaskRoleOverride(additionalPermissions, userName))
                .build()

        return RunTaskRequest.builder()
                .cluster(ecsClusterName)
                .launchType("EC2")
                .overrides(overrides)
                .taskDefinition("orchestration-service-analytical-workspace")
                .build()
    }

    /**
     * Helper method to wrap a container name and set of overrides into an incomplete [ContainerOverride.Builder] for
     * later consumption.
     */
    private fun containerOverrideBuilder(containerName: String, vararg overrides: Pair<String, String>): ContainerOverride.Builder {
        val overrideKeyPairs = overrides.map { KeyValuePair.builder().name(it.first).value(it.second).build() }
        return ContainerOverride.builder()
                .name(containerName)
                .environment(overrideKeyPairs)
    }

    fun createTaskRoleOverride(list: List<String>, userName: String): String{
        val iamClient = IamClient.builder().region(Region.AWS_GLOBAL).build()
        fun additionalPermissions(): String{
            val listToString = StringBuilder()
            if(list.isNotEmpty()) for (i in list) listToString.append(",\"$i\"")
            return listToString.toString()
        }
        val assumeRolePolicyDocument = "{" +
                "  \"Version\": \"2012-10-17\"," +
                "  \"Statement\": [" +
                "    {" +
                "        \"Effect\": \"Allow\"," +
                "        \"Action\": [" +
                "           \"sts:AssumeRole\"" +
                "       ]," +
                "       \"Principal\": {"  +
                "           \"Service\": ["  +
                "           \"ecs-tasks.amazonaws.com\"" +
                "           ]" +
                "        }" +
                "     }" +
                "   ]" +
                "}"

        val taskRolePolicyDocument = "{" +
                "  \"Version\": \"2012-10-17\"," +
                "  \"Statement\": [" +
                "     {" +
                "        \"Effect\": \"Allow\"," +
                "        \"Action\": [" +
                "            \"ecr:BatchCheckLayerAvailability\"," +
                "            \"ecr:GetDownloadUrlForLayer\"," +
                "            \"ecr:BatchGetImage\"," +
                "            \"logs:CreateLogStream\"," +
                "            \"logs:PutLogEvents\"" +
                additionalPermissions() +
                "       ]," +
                "       \"Resource\": \"*\"" +
                "      }" +
                "   ]" +
                "}"

        val userPolicyDocument = CreatePolicyRequest.builder().policyDocument(taskRolePolicyDocument).policyName("$userName-task-role-document").build()
        val userTaskPolicy = iamClient.createPolicy(userPolicyDocument)
        val iamRole = iamClient.createRole(CreateRoleRequest.builder().assumeRolePolicyDocument(assumeRolePolicyDocument).roleName("$userName-iam-role").build())
        iamClient.attachRolePolicy(AttachRolePolicyRequest.builder().policyArn(userTaskPolicy.policy().arn()).roleName(iamRole.role().roleName()).build())
        return iamRole.role().arn()
    }
}
