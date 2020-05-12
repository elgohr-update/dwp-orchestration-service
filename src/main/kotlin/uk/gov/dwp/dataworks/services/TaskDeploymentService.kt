package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ecs.model.ContainerDefinition
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import software.amazon.awssdk.services.ecs.model.NetworkMode
import software.amazon.awssdk.services.ecs.model.PortMapping
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetTypeEnum
import uk.gov.dwp.dataworks.UserTask
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.logging.DataworksLogger
import java.util.UUID

@Service
class TaskDeploymentService {
    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator
    @Autowired
    private lateinit var activeUserTasks: ActiveUserTasks
    @Autowired
    private lateinit var taskDestroyService: TaskDestroyService

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    @Autowired
    private lateinit var authService: AuthenticationService

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
        val correlationId = "$userName-${UUID.randomUUID()}"
        // Retrieve required params from environment
        val containerPort = Integer.parseInt(configurationResolver.getStringConfig(ConfigKey.USER_CONTAINER_PORT))
        val taskExecutionRoleArn = configurationResolver.getStringConfig(ConfigKey.USER_TASK_EXECUTION_ROLE_ARN)
        val taskRoleArn = configurationResolver.getStringConfig(ConfigKey.USER_TASK_ROLE_ARN)
        val taskSubnets = configurationResolver.getListConfig(ConfigKey.USER_TASK_VPC_SUBNETS)
        val taskSecurityGroups = configurationResolver.getListConfig(ConfigKey.USER_TASK_VPC_SECURITY_GROUPS)
        val albPort = Integer.parseInt(configurationResolver.getStringConfig(ConfigKey.LOAD_BALANCER_PORT))
        val albName = configurationResolver.getStringConfig(ConfigKey.LOAD_BALANCER_NAME)
        val ecsClusterName = configurationResolver.getStringConfig(ConfigKey.ECS_CLUSTER_NAME)
        val emrClusterHostname = configurationResolver.getStringConfig(ConfigKey.EMR_CLUSTER_HOSTNAME)

        //Create an entry in DynamoDB for current deployment
        activeUserTasks.initialiseDeploymentEntry(correlationId, userName)

        try {
            // Load balancer & Routing
            val loadBalancer = awsCommunicator.getLoadBalancerByName(albName)
            val listener = awsCommunicator.getAlbListenerByPort(loadBalancer.loadBalancerArn(), albPort)
            val targetGroup = awsCommunicator.createTargetGroup(correlationId, userName, loadBalancer.vpcId(), containerPort, TargetTypeEnum.IP)

            // There are 2 distinct LoadBalancer classes in the AWS SDK - ELBV2 and ECS. They represent the same LB but in different ways.
            // The following is the load balancer needed to create an ECS service.
            val ecsLoadBalancer = LoadBalancer.builder()
                    .targetGroupArn(targetGroup.targetGroupArn())
                    .containerName("guacamole")
                    .containerPort(containerPort)
                    .build()
            awsCommunicator.createAlbRoutingRule(correlationId, userName, listener.listenerArn(), targetGroup.targetGroupArn())

            // IAM permissions
            parsePolicyDocuments(additionalPermissions)
            val iamPolicy = awsCommunicator.createIamPolicy(correlationId, userName, taskRolePolicyString)
            val iamRole = awsCommunicator.createIamRole(correlationId, userName, taskAssumeRoleString)
            awsCommunicator.attachIamPolicyToRole(correlationId, iamPolicy, iamRole)

            val containerDefinitions = buildContainerDefinitions(userName, emrClusterHostname, jupyterMemory, jupyterCpu, containerPort)
            val taskDefinition = awsCommunicator.registerTaskDefinition(correlationId,"orchestration-service-user-$userName-td", taskExecutionRoleArn , taskRoleArn, NetworkMode.AWSVPC, containerDefinitions)

            // ECS
            awsCommunicator.createEcsService(correlationId, userName, ecsClusterName, taskDefinition, ecsLoadBalancer, taskSubnets, taskSecurityGroups)
        } catch (e: Exception) {
            logger.error("Failed to create resources for user", e, "correlation_id" to correlationId, "user_name" to userName)
            // Pause to allow eventual consistency
            Thread.sleep(5000)
            taskDestroyService.destroyServices(userName)
            throw e
        }
    }

    private fun buildContainerDefinitions(userName: String, emrHostname: String, jupyterMemory: Int, jupyterCpu: Int, guacamolePort: Int): Collection<ContainerDefinition> {
        val ecrEndpoint = configurationResolver.getStringConfig(ConfigKey.ECR_ENDPOINT)
        val screenSize = 1920 to 1080

        val jupyterHub = ContainerDefinition.builder()
                .name("jupyterHub")
                .image("$ecrEndpoint/aws-analytical-env/jupyterhub")
                .cpu(jupyterCpu)
                .memory(jupyterMemory)
                .essential(true)
                .portMappings(PortMapping.builder().containerPort(8000).hostPort(8000).build())
                .environment(pairsToKeyValuePairs("USER" to userName, "EMR_HOST_NAME" to emrHostname))
                .build()

        val headlessChrome = ContainerDefinition.builder()
                .name("headless_chrome")
                .image("$ecrEndpoint/aws-analytical-env/headless-chrome")
                .cpu(256)
                .memory(256)
                .essential(true)
                .portMappings(PortMapping.builder().containerPort(5900).hostPort(5900).build())
                .environment(pairsToKeyValuePairs(
                        "VNC_OPTS" to "-rfbport 5900 -xkb -noxrecord -noxfixes -noxdamage -display :1 -nopw -wait 5 -shared -permitfiletransfer -tightfilexfer -noclipboard -nosetclipboard",
                        "CHROME_OPTS" to arrayOf(
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
                                "--test-type https://localhost:8000",
                                "--kiosk",
                                "--window-size=${screenSize.toList().joinToString(",")}").joinToString(" "),
                        "VNC_SCREEN_SIZE" to screenSize.toList().joinToString("x")))
                .build()

        val guacd = ContainerDefinition.builder()
                .name("guacd")
                .image("$ecrEndpoint/aws-analytical-env/guacd")
                .cpu(128)
                .memory(128)
                .essential(true)
                .portMappings(PortMapping.builder().hostPort(4822).containerPort(4822).build())
                .build()

        val guacamole = ContainerDefinition.builder()
                .name("guacamole")
                .image("$ecrEndpoint/aws-analytical-env/guacamole")
                .cpu(256)
                .memory(256)
                .essential(true)
                .environment(pairsToKeyValuePairs(
                        "GUACD_HOSTNAME" to "localhost",
                        "GUACD_PORT" to "4822",
                        "KEYSTORE_DATA" to authService.getB64KeyStoreData(),
                        "VALIDATE_ISSUER" to "true",
                        "ISSUER" to authService.issuerUrl,
                        "CLIENT_PARAMS" to "hostname=localhost,port=5900,disable-copy=true",
                        "CLIENT_USERNAME" to userName))
                .portMappings(PortMapping.builder().hostPort(guacamolePort).containerPort(guacamolePort).build())
                .build()

        return listOf(jupyterHub, headlessChrome, guacd, guacamole)
    }

    private fun pairsToKeyValuePairs(vararg pairs: Pair<String, String>): Collection<KeyValuePair> {
        return pairs.map { KeyValuePair.builder().name(it.first).value(it.second).build() }
    }

    /**
     * Helper method to initialise the lateinit vars [taskAssumeRoleString] and [taskRolePolicyString] by
     * converting the associated `@Value` parameters to Strings and replacing `ADDITIONAL_PERMISSIONS` in
     * [taskRolePolicyString] with the provided [additionalPermissions]
     *
     * @return [Pair] of [taskRolePolicyString] to [taskAssumeRoleString] for ease of access.
     */
    fun parsePolicyDocuments(additionalPermissions: List<String>): Pair<String, String> {
        var replaceString = ""
        if (additionalPermissions.isNotEmpty()) {
            logger.info("Adding permissions to containers", "permissions" to additionalPermissions.joinToString())
            val permissionsJson = additionalPermissions.joinToString(prefix = "\"", separator = "\",\"", postfix = "\"")
            replaceString = "$permissionsJson,"
        }
        taskAssumeRoleString = taskAssumeRoleDocument.inputStream.bufferedReader().use { it.readText() }
        taskRolePolicyString = taskRolePolicyDocument.inputStream.bufferedReader().use { it.readText() }
                .replace("ADDITIONAL_PERMISSIONS", replaceString)
        return taskRolePolicyString to taskAssumeRoleString
    }
}
