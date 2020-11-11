package uk.gov.dwp.dataworks.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ecs.model.*
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetTypeEnum
import software.amazon.awssdk.services.iam.model.Policy
import uk.gov.dwp.dataworks.UserContainerProperties
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

    fun runContainers(cognitoToken: String, userName: String, cognitoGroups: List<String>, jupyterCpu: Int, jupyterMemory: Int, additionalPermissions: List<String>) {
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
        val userS3Bucket = configurationResolver.getStringConfig(ConfigKey.JUPYTER_S3_ARN)
        val accountNumber = configurationResolver.getStringConfig(ConfigKey.AWS_ACCOUNT_NUMBER)
        val gitRepo = configurationResolver.getStringConfig(ConfigKey.DATA_SCIENCE_GIT_REPO)
        val githubProxyUrl = configurationResolver.getStringConfig(ConfigKey.GITHUB_PROXY_URL)
        val pushHost = configurationResolver.getStringConfig(ConfigKey.PUSH_HOST)
        val pushCron = configurationResolver.getStringConfig(ConfigKey.PUSH_CRON)

        //Create an entry in DynamoDB for current deployment
        activeUserTasks.initialiseDeploymentEntry(correlationId, userName)

        val mapper: ObjectMapper = jacksonObjectMapper()
        val defaultTags: Map<String, String> = mapper.readValue(configurationResolver.getStringConfig(ConfigKey.TAGS))

        val tags: MutableCollection<Tag> = mutableListOf()

        defaultTags.forEach {
            // Remove any existing tags with the same name.
            if (it.key !== "CreatedBy" && it.key !== "Team" && it.key !== "Name") {
                tags += Tag.builder().key(it.key).value(it.value).build()
            }
        }

        tags += Tag.builder().key("CreatedBy").value("Orchestration Service").build()
        tags += Tag.builder().key("Team").value(cognitoGroups.first()).build()
        tags += Tag.builder().key("Name").value("orchestration-service-user-$userName-td").build()

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

            val s3fsVolume = Volume.builder()
                    .name("s3fs")
                    .host(HostVolumeProperties.builder().sourcePath("/opt/$userName").build())
                    .build()

            val userContainerProperties = UserContainerProperties(
                    cognitoToken,
                    userName,
                    cognitoGroups,
                    emrClusterHostname,
                    jupyterCpu,
                    jupyterMemory,
                    containerPort,
                    userS3Bucket,
                    "arn:aws:kms:${configurationResolver.getStringConfig(ConfigKey.AWS_REGION)}:$accountNumber:alias/$userName-home",
                    "arn:aws:kms:${configurationResolver.getStringConfig(ConfigKey.AWS_REGION)}:$accountNumber:alias/${cognitoGroups.first()}-shared",
                    gitRepo,
                    pushHost,
                    pushCron,
                    s3fsVolume.name(),
                    githubProxyUrl,
                    configurationResolver.getStringConfig(ConfigKey.GITHUB_URL).replaceFirst(Regex("^http[s]?://"),""),
                    configurationResolver.getStringConfig(ConfigKey.LIVY_PROXY_URL)
            )

            val containerDefinitions = buildContainerDefinitions(userContainerProperties)

            val taskDefinition = TaskDefinition.builder()
                    .family("orchestration-service-user-$userName-td")
                    .executionRoleArn(taskExecutionRoleArn)
                    .taskRoleArn(iamRole.arn())
                    .networkMode(NetworkMode.AWSVPC)
                    .containerDefinitions(containerDefinitions)
                    .volumes(s3fsVolume)
                    .build()

            val registeredTaskDefinition = awsCommunicator.registerTaskDefinition(correlationId, taskDefinition, tags)

            // ECS
            awsCommunicator.createEcsService(correlationId, userName, ecsClusterName, registeredTaskDefinition, ecsLoadBalancer, taskSubnets, taskSecurityGroups)
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

    private fun buildContainerDefinitions(containerProperties: UserContainerProperties): Collection<ContainerDefinition> {
        val ecrEndpoint = configurationResolver.getStringConfig(ConfigKey.ECR_ENDPOINT)
        val screenSize = 1920 to 1080
        val tabs = mutableMapOf<Int,String>();

        val noProxyList = listOf(
            "git-codecommit.${configurationResolver.getStringConfig(ConfigKey.AWS_REGION)}.amazonaws.com",
            ".${configurationResolver.getStringConfig(ConfigKey.AWS_REGION)}.compute.internal",
            ".dataworks.dwp.gov.uk",
            "localhost",
            "127.0.0.1"
        ).joinToString(",")
        val proxyEnvVariables = arrayOf(
                "HTTP_PROXY" to containerProperties.githubProxyUrl,
                "HTTPS_PROXY" to containerProperties.githubProxyUrl,
                "NO_PROXY" to noProxyList,
                "http_proxy" to containerProperties.githubProxyUrl,
                "https_proxy" to containerProperties.githubProxyUrl,
                "no_proxy" to noProxyList
        )

        val guacdContainerDependency = ContainerDependency.builder()
                .containerName("guacd")
                .condition(ContainerCondition.HEALTHY)
                .build()

        val jupyterhubContainerDependency = ContainerDependency.builder()
                .containerName("jupyterHub")
                .condition(ContainerCondition.HEALTHY)
                .build()

        val rstudioOssContainerDependency = ContainerDependency.builder()
                .containerName("rstudio-oss")
                .condition(ContainerCondition.START)
                .build()

        val hueContainerDependency = ContainerDependency.builder()
                .containerName("hue")
                .condition(ContainerCondition.START)
                .build()

        val s3fsContainerDependency = ContainerDependency.builder()
                .containerName("s3fs")
                .condition(ContainerCondition.HEALTHY)
                .build()

        val headlessChromeDependency = ContainerDependency.builder()
                .containerName("headless_chrome")
                .condition(ContainerCondition.HEALTHY)
                .build()

        val hue = ContainerDefinition.builder()
                .name("hue")
                .image("$ecrEndpoint/aws-analytical-env/hue")
                .cpu(containerProperties.jupyterCpu)
                .memory(containerProperties.jupyterMemory)
                .essential(false)
                .portMappings(PortMapping.builder().containerPort(8888).hostPort(8888).build())
                .environment(pairsToKeyValuePairs(
                        "USER" to containerProperties.userName,
                        "EMR_HOST_NAME" to containerProperties.emrHostname,
                        "S3_BUCKET" to containerProperties.userS3Bucket.substringAfterLast(":"),
                        "KMS_HOME" to containerProperties.kmsHome,
                        "KMS_SHARED" to containerProperties.kmsShared,
                        "GITHUB_URL" to containerProperties.githubUrl,
                        "DISABLE_AUTH" to "true"))
                .volumesFrom(VolumeFrom.builder().sourceContainer("s3fs").build())
                .logConfiguration(buildLogConfiguration(containerProperties.userName, "hue"))
                .dependsOn(s3fsContainerDependency)
                .build()

        tabs.put(30, "https://localhost:8888")

        val rstudioOss = ContainerDefinition.builder()
                .name("rstudio-oss")
                .image("$ecrEndpoint/aws-analytical-env/rstudio-oss")
                .cpu(containerProperties.jupyterCpu)
                .memory(containerProperties.jupyterMemory)
                .essential(false)
                .portMappings(PortMapping.builder().containerPort(7000).hostPort(7000).build())
                .environment(pairsToKeyValuePairs(
                        "USER" to containerProperties.userName,
                        "EMR_URL" to (containerProperties.livyProxyUrl ?: "http://${containerProperties.emrHostname}:8998"),
                        "DISABLE_AUTH" to "true",
                        "GITHUB_URL" to containerProperties.githubUrl,
                        "JWT_TOKEN" to containerProperties.cognitoToken,
                        *proxyEnvVariables
                ))
                .volumesFrom(VolumeFrom.builder().sourceContainer("s3fs").build())
                .logConfiguration(buildLogConfiguration(containerProperties.userName, "rstudio-oss"))
                .dependsOn(s3fsContainerDependency)
                .build()

        tabs.put(20, "https://localhost:7000")

        val jupyterhubHealthCheck = HealthCheck.builder()
                .command("CMD", "curl", "-k", "-o", "/dev/null", "https://localhost:8000/hub/health")
                .interval(12)
                .timeout(12)
                .startPeriod(20)
                .build()

        val jupyterHub = ContainerDefinition.builder()
                .name("jupyterHub")
                .image("$ecrEndpoint/aws-analytical-env/jupyterhub")
                .cpu(containerProperties.jupyterCpu)
                .memory(containerProperties.jupyterMemory)
                .essential(true)
                .portMappings(PortMapping.builder().containerPort(8000).hostPort(8000).build())
                .environment(pairsToKeyValuePairs(
                        "USER" to containerProperties.userName,
                        "EMR_URL" to (containerProperties.livyProxyUrl ?: "http://${containerProperties.emrHostname}:8998"),
                        "GIT_REPO" to containerProperties.gitRepo,
                        "PUSH_HOST" to containerProperties.pushHost,
                        "PUSH_CRON" to containerProperties.pushCron,
                        "GITHUB_URL" to containerProperties.githubUrl,
                        "JWT_TOKEN" to containerProperties.cognitoToken,
                        *proxyEnvVariables
                ))
                .volumesFrom(VolumeFrom.builder().sourceContainer("s3fs").build())
                .logConfiguration(buildLogConfiguration(containerProperties.userName, "jupyterHub"))
                .healthCheck(jupyterhubHealthCheck)
                .dependsOn(s3fsContainerDependency)
                .build()

        tabs.put(10, "https://localhost:8000")

        val headlessChromeHealthCheck = HealthCheck.builder()
                .command("CMD-SHELL", "supervisorctl", "status", "|", "awk", "'BEGIN {c=0} $2 == \"RUNNING\" {c++} END {exit c != 3}'")
                .interval(12)
                .timeout(12)
                .startPeriod(20)
                .build()

        val linuxParameters: LinuxParameters = LinuxParameters.builder().sharedMemorySize(2048).build()

        tabs.put(40,configurationResolver.getStringConfig(ConfigKey.GITHUB_URL))
        
        tabs.put(50, "https://azkaban.workflow-manager.dataworks.dwp.gov.uk?action=login&cognitoToken=" + containerProperties.cognitoToken)

        val headlessChrome = ContainerDefinition.builder()
                .name("headless_chrome")
                .image("$ecrEndpoint/aws-analytical-env/headless-chrome")
                .cpu(512)
                .memory(2048)
                .essential(true)
                .portMappings(PortMapping.builder().containerPort(5900).hostPort(5900).build())
                .linuxParameters(linuxParameters)
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
                                "--test-type ${tabs.toSortedMap().values.joinToString(" ")}",
                                "--host-rules=\"MAP * 127.0.0.1, MAP * localhost, EXCLUDE github.ucds.io, EXCLUDE git.ucd.gpn.gov.uk, EXCLUDE azkaban.workflow-manager.dataworks.dwp.gov.uk\"",
                                "--ignore-certificate-errors",
                                "--enable-auto-reload",
                                "--connectivity-check-url=https://localhost:8000",
                                "--window-size=${screenSize.toList().joinToString(",")}").joinToString(" "),
                        "VNC_SCREEN_SIZE" to screenSize.toList().joinToString("x"),
                        *proxyEnvVariables))
                .logConfiguration(buildLogConfiguration(containerProperties.userName, "headless_chrome"))
                .volumesFrom(VolumeFrom.builder().sourceContainer("s3fs").build())
                .healthCheck(headlessChromeHealthCheck)
                .dependsOn(jupyterhubContainerDependency, rstudioOssContainerDependency, hueContainerDependency)
                .build()

        val guacdHealthCheck = HealthCheck.builder()
                .command("CMD", "nc", "-z", "127.0.0.1", "4822")
                .interval(12)
                .timeout(12)
                .startPeriod(20)
                .build()

        val guacd = ContainerDefinition.builder()
                .name("guacd")
                .image("$ecrEndpoint/aws-analytical-env/guacd")
                .cpu(64)
                .memory(128)
                .essential(true)
                .portMappings(PortMapping.builder().hostPort(4822).containerPort(4822).build())
                .logConfiguration(buildLogConfiguration(containerProperties.userName, "guacd"))
                .healthCheck(guacdHealthCheck)
                .dependsOn(headlessChromeDependency)
                .build()

        val guacamole = ContainerDefinition.builder()
                .name("guacamole")
                .image("$ecrEndpoint/aws-analytical-env/guacamole")
                .cpu(256)
                .memory(896)
                .essential(true)
                .environment(pairsToKeyValuePairs(
                        "GUACD_HOSTNAME" to "localhost",
                        "GUACD_PORT" to "4822",
                        "KEYSTORE_DATA" to authService.getB64KeyStoreData(),
                        "VALIDATE_ISSUER" to "true",
                        "ISSUER" to authService.issuerUrl,
                        "CLIENT_PARAMS" to "hostname=localhost,port=5900,disable-copy=true",
                        "CLIENT_USERNAME" to containerProperties.userName.substring(0, containerProperties.userName.length - 3)))
                .portMappings(PortMapping.builder().hostPort(containerProperties.guacamolePort).containerPort(containerProperties.guacamolePort).build())
                .logConfiguration(buildLogConfiguration(containerProperties.userName, "guacamole"))
                .dependsOn(jupyterhubContainerDependency, guacdContainerDependency)
                .build()

        val s3fsHealthCheck = HealthCheck.builder()
                .command("CMD", "pgrep", "tail")
                .interval(30)
                .timeout(5)
                .build()

        val s3fs = ContainerDefinition.builder()
                .name("s3fs")
                .image("$ecrEndpoint/aws-analytical-env/s3fs")
                .cpu(64)
                .memory(128)
                .essential(true)
                .environment(pairsToKeyValuePairs(
                        "KMS_HOME" to containerProperties.kmsHome,
                        "KMS_SHARED" to containerProperties.kmsShared,
                        "S3_BUCKET" to containerProperties.userS3Bucket.substringAfterLast(":"),
                        "USER" to containerProperties.userName,
                        "TEAM" to containerProperties.cognitoGroups[0]
                ))
                .linuxParameters(
                        LinuxParameters.builder()
                                .capabilities(KernelCapabilities.builder().add("SYS_ADMIN").build())
                                .devices(Device.builder().hostPath("/dev/fuse").build())
                                .build())
                .mountPoints(MountPoint.builder().containerPath("/mnt/s3fs:rshared").sourceVolume(containerProperties.s3fsVolumeName).build())
                .healthCheck(s3fsHealthCheck)
                .logConfiguration(buildLogConfiguration(containerProperties.userName, "s3fs"))
                .build()

        return listOf(jupyterHub, headlessChrome, guacd, guacamole, rstudioOss, hue, s3fs)
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
                .map { awsCommunicator.getKmsKeyArn("arn:aws:kms:${configurationResolver.getStringConfig(ConfigKey.AWS_REGION)}:$accountId:alias/$it-shared") }
                .plus(listOf("$jupyterS3Arn/*", awsCommunicator.getKmsKeyArn("arn:aws:kms:${configurationResolver.getStringConfig(ConfigKey.AWS_REGION)}:$accountId:alias/$userName-home")))
        return mapOf(Pair("jupyters3accessdocument", folderAccess), Pair("jupyterkmsaccessdocument", folderAccess), Pair("jupyters3list", listOf(jupyterS3Arn)))
    }
}
