package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.*
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*
import uk.gov.dwp.dataworks.exceptions.FailedToExecuteCreateServiceRequestException
import uk.gov.dwp.dataworks.exceptions.FailedToExecuteRunTaskRequestException
import uk.gov.dwp.dataworks.exceptions.UpperRuleLimitReachedException
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Service
class TaskDeploymentService {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(TaskDeploymentService::class.java))
    }

    val configurationService = ConfigurationService()

    private fun createService(ecsClusterName: String, userName: String, ecsClient: EcsClient, containerPort: Int, targetGroupArn: String) {

        val alb: LoadBalancer = LoadBalancer.builder().targetGroupArn(targetGroupArn).containerPort(containerPort).build()

        val serviceBuilder = CreateServiceRequest.builder().cluster(ecsClusterName).loadBalancers(alb).serviceName(userName).taskDefinition(configurationService.getStringConfig(ConfigKey.USER_CONTAINER_TASK_DEFINITION)).loadBalancers(alb).desiredCount(1).build()
        logger.info("Creating Service...")

        try {
            val service = ecsClient.createService(serviceBuilder)
            logger.info("service.responseMetadata = ${service.responseMetadata()}")
        } catch (e: Exception) {
            logger.error("Error while creating the service", e)
            throw FailedToExecuteCreateServiceRequestException()
        }
    }

    fun getVacantPriorityValue(rulesResponse: DescribeRulesResponse): Int {

        val rulePriorities = rulesResponse.rules().map { it.priority() }.filter { it != "default" }.map { Integer.parseInt(it) }.toSet()
        if (rulePriorities.size >= 1000) throw UpperRuleLimitReachedException()
        for (priority in 0..1000) {
            if (!rulePriorities.contains(priority)) return priority
        }
        throw UpperRuleLimitReachedException()
    }

    fun taskDefinitionWithOverride(ecsClusterName: String, emrClusterHostName: String, albName: String, userName: String, containerPort: Int, jupyterCpu: Int, jupyterMemory: Int) {

        val ecsClient = EcsClient.builder().region(configurationService.awsRegion).build()
        val albClient = ElasticLoadBalancingV2Client.builder().region(configurationService.awsRegion).build()

        logger.info("Getting alb information...")

        val albRequest = DescribeLoadBalancersRequest.builder().names(albName).build()
        val albResponse = albClient.describeLoadBalancers(albRequest)

        logger.info("Getting Listener information...")

        val albListenerRequest = DescribeListenersRequest.builder().loadBalancerArn(albResponse.loadBalancers()[0].loadBalancerArn()).build()
        val albListenerResponse = albClient.describeListeners(albListenerRequest)

        logger.info("Creating target group...")

        val tgRequest = CreateTargetGroupRequest.builder().name("$userName-target-group").protocol("HTTPS").vpcId(albResponse.loadBalancers()[0].vpcId()).port(containerPort).build()
        albClient.createTargetGroup(tgRequest)

        logger.info("Getting target group arn...")

        val albTargetGroupRequest = albClient.describeTargetGroups(DescribeTargetGroupsRequest.builder().names("$userName-target-group").build())
        val albTargetGroupArn = albTargetGroupRequest.targetGroups()[0].targetGroupArn()

        val pathPattern = PathPatternConditionConfig.builder().values("/$userName/*").build()
        val albRuleCondition = RuleCondition.builder().field("path-pattern").pathPatternConfig(pathPattern).build()
        val userTargetGroup = TargetGroupTuple.builder().targetGroupArn(albTargetGroupArn).build()
        val forwardAction = ForwardActionConfig.builder().targetGroups(userTargetGroup).build()
        val albRuleAction = Action.builder().type("forward").forwardConfig(forwardAction).build()

        logger.info("Creating listener rule...")

        val rulesResponse = albClient.describeRules(DescribeRulesRequest.builder().listenerArn(albListenerResponse.listeners()[0].listenerArn()).build())
        albClient.createRule(CreateRuleRequest.builder().listenerArn(albListenerResponse.listeners()[0].listenerArn()).priority(getVacantPriorityValue(rulesResponse)).conditions(albRuleCondition).actions(albRuleAction).build())

        createService(ecsClusterName, userName, ecsClient, containerPort, albTargetGroupArn)

        logger.info("Starting Task...")
        try {
            val response = ecsClient.runTask(createRunTaskRequestWithOverrides(userName, emrClusterHostName, jupyterMemory, jupyterCpu, ecsClusterName))
            logger.info("response.tasks = ${response.tasks()}")
        } catch (e: Exception) {
            logger.error("Error while processing the run task request", e)
            throw FailedToExecuteRunTaskRequestException()
        }
    }

    private fun createRunTaskRequestWithOverrides(username: String, emrHostname: String, jupyterMemory: Int, jupyterCpu: Int, ecsClusterName: String): RunTaskRequest {
        val usernamePair = "USER" to username
        val hostnamePair = "EMR_HOST_NAME" to emrHostname

        val chrome = containerOverrideBuilder("headless_chrome", usernamePair).build()
        val guacd = containerOverrideBuilder("guacd", usernamePair).build()
        // Jupyter also has configurable resources
        val jupyter = containerOverrideBuilder("jupyterHub", usernamePair, hostnamePair).cpu(jupyterCpu).memory(jupyterMemory).build()

        val overrides = TaskOverride.builder()
                .containerOverrides(guacd, chrome, jupyter)
                .build()

        return RunTaskRequest.builder()
                .cluster(ecsClusterName)
                .launchType("EC2")
                .overrides(overrides)
                .taskDefinition("orchestration-service-ui-service")
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
}

