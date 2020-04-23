package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Service
class TaskDeploymentService {
    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    @Value("classpath:policyDocuments/taskAssumeRolePolicy.json")
    lateinit var taskAssumeRoleDocument: Resource
    private lateinit var taskAssumeRoleString: String

    @Value("classpath:policyDocuments/taskRolePolicy.json")
    lateinit var taskRolePolicyDocument: Resource
    private lateinit var taskRolePolicyString: String

    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(TaskDeploymentService::class.java))
    }

    fun runContainers(userName: String, jupyterCpu: Int, jupyterMemory: Int, additionalPermissions: List<String>) {
        // Retrieve required params from environment
        val containerPort = Integer.parseInt(configurationResolver.getStringConfig(ConfigKey.USER_CONTAINER_PORT))
        val emrClusterHostName = configurationResolver.getStringConfig(ConfigKey.EMR_CLUSTER_HOST_NAME)
        val albName = configurationResolver.getStringConfig(ConfigKey.LOAD_BALANCER_NAME)
        val ecsClusterName = configurationResolver.getStringConfig(ConfigKey.ECS_CLUSTER_NAME)

        // Load balancer & Routing
        val loadBalancer = awsCommunicator.getLoadBalancerByName(albName)
        val listener = awsCommunicator.getAlbListenerByPort(loadBalancer.loadBalancerArn(), containerPort)
        val targetGroup = awsCommunicator.createTargetGroup(loadBalancer.vpcId(), "$userName-target-group", containerPort)
        // There are 2 distinct LoadBalancer classes in the AWS SDK - ELBV2 and ECS. They represent the same LB but in different ways.
        // The following is the load balancer needed to create an ECS service.
        val ecsLoadBalancer = LoadBalancer.builder()
                .targetGroupArn(targetGroup.targetGroupArn())
                .loadBalancerName(loadBalancer.loadBalancerName())
                .containerName("guacamole")
                .containerPort(containerPort)
                .build()
        awsCommunicator.createAlbRoutingRule(listener.listenerArn(),targetGroup.targetGroupArn(),"/$userName/*")

        // IAM permissions
        parsePolicyDocuments(additionalPermissions)
        val iamPolicy = awsCommunicator.createIamPolicy("$userName-task-role-document", taskRolePolicyString)
        val iamRole = awsCommunicator.createIamRole("$userName-iam-role", taskAssumeRoleString)
        awsCommunicator.attachIamPolicyToRole(iamPolicy, iamRole)

        // ECS
        awsCommunicator.createEcsService(ecsClusterName, "${userName}-analytical-workspace", ecsLoadBalancer)
        val containerOverrides = buildContainerOverrides(userName, emrClusterHostName, jupyterMemory, jupyterCpu)
        val ecsTaskRequest = awsCommunicator.buildEcsTask(ecsClusterName, "orchestration-service-analytical-workspace", iamRole.arn(), containerOverrides)

        awsCommunicator.runEcsTask(ecsTaskRequest)
    }

    private fun buildContainerOverrides(userName: String, emrHostname: String, jupyterMemory: Int, jupyterCpu: Int): List<ContainerOverride> {
        val usernamePair = "USER" to userName
        val hostnamePair = "EMR_HOST_NAME" to emrHostname

        val screenSize = 1920 to 1080
        val chromeOptsPair = "CHROME_OPTS" to arrayOf(
                "--no-sandbox",
                "--window-position=0,0",
                "--force-device-scale-factor=1",
                "--incognito",
                "--noerrdialogs",
                "--disable-translate",
                "--no-first-run",
                "--fast",
                "--fast-start",
                "--disable-infobars",
                "--disable-features=TranslateUI",
                "--disk-cache-dir=/dev/null",
                "--test-type https://jupyterHub:8443",
                "--kiosk",
                "--window-size=${screenSize.toList().joinToString(",")}"
        ).joinToString(" ")
        val vncScreenSizePair = "VNC_SCREEN_SIZE" to screenSize.toList().joinToString("x")

        val chrome = awsCommunicator.buildContainerOverride("headless_chrome", chromeOptsPair, vncScreenSizePair).build()
        val guacd = awsCommunicator.buildContainerOverride("guacd", usernamePair).build()
        val guacamole = awsCommunicator.buildContainerOverride("guacamole", "CLIENT_USERNAME" to userName).build()
        // Jupyter also has configurable resources
        val jupyter = awsCommunicator.buildContainerOverride("jupyterHub", usernamePair, hostnamePair).cpu(jupyterCpu).memory(jupyterMemory).build()

        return listOf(chrome, guacd, guacamole, jupyter)
    }

    /**
     * Helper method to initialise the lateinit vars [taskAssumeRoleString] and [taskRolePolicyString] by
     * converting the associated `@Value` parameters to Strings and replacing `ADDITIONAL_PERMISSIONS` in
     * [taskRolePolicyString] with the provided [additionalPermissions]
     *
     * @return [Pair] of [taskRolePolicyString] to [taskAssumeRoleString] for ease of access.
     */
    fun parsePolicyDocuments(additionalPermissions: List<String>): Pair<String, String> {
        logger.info("Adding permissions to containers", "permissions" to additionalPermissions.joinToString())
        val permissionsJson = additionalPermissions.joinToString(prefix = "\"", separator = "\",\"", postfix = "\"")

        taskAssumeRoleString = taskAssumeRoleDocument.inputStream.bufferedReader().use { it.readText() }
        taskRolePolicyString = taskRolePolicyDocument.inputStream.bufferedReader().use { it.readText() }
                .replace("ADDITIONAL_PERMISSIONS", permissionsJson)
        return taskRolePolicyString to taskAssumeRoleString
    }
}
