package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ecs.model.ContainerCondition
import software.amazon.awssdk.services.ecs.model.ContainerDefinition
import software.amazon.awssdk.services.ecs.model.ContainerDependency
import software.amazon.awssdk.services.ecs.model.HealthCheck
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import software.amazon.awssdk.services.ecs.model.NetworkMode
import software.amazon.awssdk.services.ecs.model.PortMapping
import software.amazon.awssdk.services.ecs.model.LogConfiguration
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetTypeEnum
import software.amazon.awssdk.services.iam.model.Policy
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.aws.AwsParsing
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
    private lateinit var awsParsing: AwsParsing

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    @Autowired
    private lateinit var authService: AuthenticationService

    @Value("classpath:policyDocuments/taskAssumeRolePolicy.json")
    lateinit var taskAssumeRoleDocument: Resource

    @Value("classpath:policyDocuments/taskRolePolicy.json")
    lateinit var taskRoleDocument: Resource

    @Value("classpath:policyDocuments/jupyterBucketAccessPolicy.json")
    lateinit var jupyterBucketAccessDocument: Resource

    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(TaskDeploymentService::class.java))
    }

    fun runContainers(userName: String, cognitoGroups: List<String>, jupyterCpu: Int, jupyterMemory: Int, additionalPermissions: List<String>) {
        val correlationId = "$userName-${UUID.randomUUID()}"
        // Retrieve required params from environment
        val containerPort = Integer.parseInt(configurationResolver.getStringConfig(ConfigKey.USER_CONTAINER_PORT))
        val taskExecutionRoleArn = configurationResolver.getStringConfig(ConfigKey.USER_TASK_EXECUTION_ROLE_ARN)
        val taskSubnets = configurationResolver.getListConfig(ConfigKey.USER_TASK_VPC_SUBNETS)
        val taskSecurityGroups = configurationResolver.getListConfig(ConfigKey.USER_TASK_VPC_SECURITY_GROUPS)
        val albPort = Integer.parseInt(configurationResolver.getStringConfig(ConfigKey.LOAD_BALANCER_PORT))
        val albName = configurationResolver.getStringConfig(ConfigKey.LOAD_BALANCER_NAME)
        val ecsClusterName = configurationResolver.getStringConfig(ConfigKey.ECS_CLUSTER_NAME)
        val emrClusterHostname = configurationResolver.getStringConfig(ConfigKey.EMR_CLUSTER_HOSTNAME)
        val jupyterS3Bucket = configurationResolver.getStringConfig(ConfigKey.JUPYTER_S3_ARN)
        val accountNumber = configurationResolver.getStringConfig(ConfigKey.AWS_ACCOUNT_NUMBER)

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

            //IAM Permissions
            val taskRolePolicyString = awsParsing.parsePolicyDocument(taskRoleDocument, mapOf("ecstaskrolepolicy" to additionalPermissions), "Action")
            val taskAssumeRoleString = taskAssumeRoleDocument.inputStream.bufferedReader().use { it.readText() }
            val iamPolicy = awsCommunicator.createIamPolicy(correlationId, userName, taskRolePolicyString, "iamPolicyUserArn")
            val iamRole = awsCommunicator.createIamRole(correlationId, userName, taskAssumeRoleString)
            awsCommunicator.attachIamPolicyToRole(correlationId, iamPolicy, iamRole)
            awsCommunicator.attachIamPolicyToRole(correlationId, setupJupyterIam(cognitoGroups, userName, correlationId, accountNumber), iamRole)

            val containerDefinitions = buildContainerDefinitions(userName, emrClusterHostname, jupyterMemory, jupyterCpu, containerPort, jupyterS3Bucket, "arn:aws:kms:${configurationResolver.awsRegion}:$accountNumber:alias/$userName-home", "arn:aws:kms:${configurationResolver.awsRegion}:$accountNumber:alias/${cognitoGroups.first()}-shared")
            val taskDefinition = awsCommunicator.registerTaskDefinition(correlationId, "orchestration-service-user-$userName-td", taskExecutionRoleArn, iamRole.arn(), NetworkMode.AWSVPC, containerDefinitions)

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

    private fun buildLogConfiguration(userName: String, containerName: String): LogConfiguration {
        val logConfig = LogConfiguration.builder()
                .logDriver("awslogs")
                .options(mapOf(
                        "awslogs-group" to configurationResolver.getStringConfig(ConfigKey.CONTAINER_LOG_GROUP),
                        "awslogs-region" to configurationResolver.getStringConfig(ConfigKey.AWS_REGION),
                        "awslogs-stream-prefix" to "${userName}_${containerName}"
                ))
                .build()
        return logConfig
    }

    private fun buildContainerDefinitions(userName: String, emrHostname: String, jupyterMemory: Int, jupyterCpu: Int, guacamolePort: Int, jupyterS3Bucket: String, kmsHome: String, kmsShared: String): Collection<ContainerDefinition> {
        val ecrEndpoint = configurationResolver.getStringConfig(ConfigKey.ECR_ENDPOINT)
        val screenSize = 1920 to 1080

        val jupyterhubContainerDependency = ContainerDependency.builder()
                .containerName("jupyterHub")
                .condition(ContainerCondition.HEALTHY)
                .build()
        val jupyterhubHealthCheck = HealthCheck.builder()
                .command("CMD", "wget", "-O-", "-S", "--no-check-certificate", "-q", "https://localhost:8000/hub/health")
                .interval(12)
                .timeout(12)
                .startPeriod(20)
                .build()
        val jupyterHub = ContainerDefinition.builder()
                .name("jupyterHub")
                .image("$ecrEndpoint/aws-analytical-env/jupyterhub")
                .cpu(jupyterCpu)
                .memory(jupyterMemory)
                .essential(true)
                .portMappings(PortMapping.builder().containerPort(8000).hostPort(8000).build())
                .environment(pairsToKeyValuePairs("USER" to userName, "EMR_HOST_NAME" to emrHostname, "S3_BUCKET" to jupyterS3Bucket.substringAfterLast(":"), "KMS_HOME" to kmsHome, "KMS_SHARED" to kmsShared))
                .logConfiguration(buildLogConfiguration(userName, "jupyterHub"))
                .healthCheck(jupyterhubHealthCheck)
                .build()

        val headlessChrome = ContainerDefinition.builder()
                .name("headless_chrome")
                .image("$ecrEndpoint/aws-analytical-env/headless-chrome")
                .cpu(512)
                .memory(1024)
                .essential(true)
                .portMappings(PortMapping.builder().containerPort(5900).hostPort(5900).build())
                .environment(pairsToKeyValuePairs(
                        "VNC_OPTS" to "-rfbport 5900 -xkb -noxrecord -noxfixes -noxdamage -display :1 -nopw -wait 5 -noclipboard",
                        "CHROME_OPTS" to arrayOf(
                                "--no-sandbox",
                                "--window-position=0,0",
                                "--force-device-scale-factor=1",
                                "--incognito",
                                "--noerrdialogs",
                                "--disable-translate",
                                "--no-first-run",
                                "--disable-infobars",
                                "--disable-features=TranslateUI",
                                "--disk-cache-dir=/dev/null",
                                "--test-type https://localhost:8000",
                                "--kiosk",
                                "--ignore-certificate-errors",
                                "--enable-auto-reload",
                                "--connectivity-check-url=https://localhost:8000",
                                "--window-size=${screenSize.toList().joinToString(",")}").joinToString(" "),
                        "VNC_SCREEN_SIZE" to screenSize.toList().joinToString("x")))
                .logConfiguration(buildLogConfiguration(userName, "headless_chrome"))
                .dependsOn(jupyterhubContainerDependency)
                .build()

        val guacd = ContainerDefinition.builder()
                .name("guacd")
                .image("$ecrEndpoint/aws-analytical-env/guacd")
                .cpu(64)
                .memory(128)
                .essential(true)
                .portMappings(PortMapping.builder().hostPort(4822).containerPort(4822).build())
                .logConfiguration(buildLogConfiguration(userName, "guacd"))
                .build()

        val guacamole = ContainerDefinition.builder()
                .name("guacamole")
                .image("$ecrEndpoint/aws-analytical-env/guacamole")
                .cpu(256)
                .memory(512)
                .essential(true)
                .environment(pairsToKeyValuePairs(
                        "GUACD_HOSTNAME" to "localhost",
                        "GUACD_PORT" to "4822",
                        "KEYSTORE_DATA" to authService.getB64KeyStoreData(),
                        "VALIDATE_ISSUER" to "true",
                        "ISSUER" to authService.issuerUrl,
                        "CLIENT_PARAMS" to "hostname=localhost,port=5900,disable-copy=true",
                        "CLIENT_USERNAME" to userName.substring(0, userName.length - 3)))
                .portMappings(PortMapping.builder().hostPort(guacamolePort).containerPort(guacamolePort).build())
                .logConfiguration(buildLogConfiguration(userName, "guacamole"))
                .dependsOn(jupyterhubContainerDependency)
                .build()

        return listOf(jupyterHub, headlessChrome, guacd, guacamole)
    }

    private fun pairsToKeyValuePairs(vararg pairs: Pair<String, String>): Collection<KeyValuePair> {
        return pairs.map { KeyValuePair.builder().name(it.first).value(it.second).build() }
    }

    fun setupJupyterIam(cognitoGroups: List<String>, userName: String, correlationId: String, accountId: String): Policy {
        val jupyterBucketAccessRolePolicyString = awsParsing.parsePolicyDocument(jupyterBucketAccessDocument, parseMap(cognitoGroups, userName, accountId), "Resource")
        return awsCommunicator.createIamPolicy(correlationId, userName, jupyterBucketAccessRolePolicyString, "iamPolicyTaskArn")
    }

    /*
    *   Helper method to parse environment variables into arn strings and return lists of the values paired
    *   with the relevant SID of the IAM statement
     */
    fun parseMap(cognitoGroups: List<String>, userName: String, accountId: String): Map<String, List<String>> {
        val jupyterS3Arn = configurationResolver.getStringConfig(ConfigKey.JUPYTER_S3_ARN)
        val folderAccess = cognitoGroups
                .map { awsCommunicator.getKmsKeyArn("arn:aws:kms:${configurationResolver.awsRegion}:$accountId:alias/$it-shared") }
                .plus(listOf("$jupyterS3Arn/*", awsCommunicator.getKmsKeyArn("arn:aws:kms:${configurationResolver.awsRegion}:$accountId:alias/$userName-home")))
        return mapOf(Pair("jupyters3accessdocument", folderAccess), Pair("jupyterkmsaccessdocument", folderAccess), Pair("jupyters3list", listOf(jupyterS3Arn)))
    }
}
