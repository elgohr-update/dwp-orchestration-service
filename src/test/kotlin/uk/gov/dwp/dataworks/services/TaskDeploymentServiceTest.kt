package uk.gov.dwp.dataworks.services

import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.services.ecs.model.ContainerDefinition
import software.amazon.awssdk.services.ecs.model.TaskDefinition
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup
import software.amazon.awssdk.services.iam.model.Policy
import software.amazon.awssdk.services.iam.model.Role
import uk.gov.dwp.dataworks.Application
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetTypeEnum

@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [Application::class, TaskDeploymentServiceTest.AwsCommunicatorConfig::class])
@SpringBootTest(properties = ["orchestrationService.cognito_user_pool_id=pool_1",
                              "orchestrationService.aws_account_number=123456789",
                              "orchestrationService.aws_region=eu-west-2",
                              "orchestrationService.user_container_port=1234",
                              "orchestrationService.load_balancer_port=1818",
                              "orchestrationService.load_balancer_name=lbName",
                              "orchestrationService.user_task_subnets=testSubnets",
                              "orchestrationService.user_task_execution_role_arn=taskExecutionARN",
                              "orchestrationService.user_task_security_groups=testSgs",
                              "orchestrationService.user_container_url=www.com",
                              "orchestrationService.emr_cluster_hostname=test_hostname",
                              "orchestrationService.ecs_cluster_name=test_cluster",
                              "orchestrationService.container_log_group=testLog",
                              "orchestrationService.data_science_git_repo=codecommitted",
                              "orchestrationService.ecr_endpoint=endpoint",
                              "orchestrationService.debug=false",
                              "orchestrationService.jupyterhub_bucket_arn=testArn",
                              "orchestrationService.push_gateway_host=testlb",
                              "orchestrationService.push_gateway_cron=*/5 * * * *",
                              "orchestrationService.github_proxy_url=proxy.tld:3128",
                              "orchestrationService.github_url=https://github.com",
                              "orchestrationService.livy_proxy_url=https://livy-proxy.com",
                              "TAGS={}",
                              "spring.main.allow-bean-definition-overriding=true"])
class TaskDeploymentServiceTest {

    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    @Autowired
    private lateinit var taskDeploymentService: TaskDeploymentService

    @Autowired
    private lateinit var userAuthorizationService: UserAuthorizationService

    @Before
    fun setupMocks() {
        clearInvocations(awsCommunicator)
        whenever(awsCommunicator.getKmsKeyArn(any())).doAnswer {
            val alias = it.getArgument<String>(0).split("/").last()
            "arn:aws:kms:${configurationResolver.awsRegion}:000:key/testkeyarn-$alias"
        }
    }

    @Test
    fun `Can work through debug endpoint without cognitoGroups`() {
        val emptyCognitoGroup = taskDeploymentService.parseMap(emptyList(), "testUser", configurationResolver.getStringConfig(ConfigKey.AWS_ACCOUNT_NUMBER))
        assertThat(emptyCognitoGroup)
                .isEqualTo(mapOf(Pair("jupyterkmsaccessdocument", listOf("testArn/*", "arn:aws:kms:${configurationResolver.awsRegion}:000:key/testkeyarn-testUser-home")), Pair("jupyters3accessdocument", listOf("testArn/*", "arn:aws:kms:${configurationResolver.awsRegion}:000:key/testkeyarn-testUser-home")), Pair("jupyters3list", listOf("testArn"))))
    }

    @Test
    fun `Creates correct IAM policy for user`() {
        val returnedIamPolicy = taskDeploymentService.parseMap(listOf("testGroup"), "testUsername", "000")
        assertThat(returnedIamPolicy).isEqualTo(mapOf(
                "jupyterkmsaccessdocument" to listOf(
                        "arn:aws:kms:eu-west-2:000:key/testkeyarn-testGroup-shared",
                        "testArn/*",
                        "arn:aws:kms:eu-west-2:000:key/testkeyarn-testUsername-home"),
                "jupyters3accessdocument" to listOf(
                        "arn:aws:kms:${configurationResolver.awsRegion}:000:key/testkeyarn-testGroup-shared",
                        "testArn/*",
                        "arn:aws:kms:${configurationResolver.awsRegion}:000:key/testkeyarn-testUsername-home"),
                "jupyters3list" to listOf("testArn")))
    }

    @Test
    fun `Task definition has tablist to open`() {
        taskDeploymentService.runContainers("abcde", "username", listOf("team"), 100, 200, emptyList())
        val captor = argumentCaptor<TaskDefinition>()
        verify(awsCommunicator).registerTaskDefinition(any(), captor.capture(), any())
        val def = captor.firstValue
        assertThat(def).isNotNull
        val chromeEnvs = def.containerDefinitions()
                .first { x : ContainerDefinition -> x.name() == "headless_chrome" }
                .environment()
        assertThat(chromeEnvs.first { k -> k.name() == "CHROME_OPTS" }
                .value()).contains(" https://localhost:8000 ",
                                   " https://localhost:7000 ",
                                   " https://localhost:8888 ",
                                   " https://github.com ",
                                   " https://azkaban.workflow-manager.dataworks.dwp.gov.uk?action=login&cognitoToken=abcde ")

    }

    @Test
    fun `Task definition has githubUrl to open for Jupyter`() {
        taskDeploymentService.runContainers("abcde", "username", listOf("team"), 100, 200, emptyList())
        val captor = argumentCaptor<TaskDefinition>()
        verify(awsCommunicator).registerTaskDefinition(any(), captor.capture(), any())
        val def = captor.firstValue
        assertThat(def).isNotNull
        val jupyterEnvs = def.containerDefinitions()
            .first { x: ContainerDefinition -> x.name() == "jupyterHub" }
            .environment()
        assertThat(jupyterEnvs.first { k -> k.name() == "GITHUB_URL" }
            .value()).isEqualTo("github.com")
    }

    @Test
    fun `Task definition configures SFTP correctly if allowed`() {
        reset(userAuthorizationService)
        whenever(
            userAuthorizationService.hasUserToolingPermission(
                "username",
                ToolingPermission.FILE_TRANSFER_DOWNLOAD
            )
        ).doReturn(true)

        taskDeploymentService.runContainers("abcde", "username", listOf("team"), 100, 200, emptyList())
        val captor = argumentCaptor<TaskDefinition>()
        verify(awsCommunicator).registerTaskDefinition(any(), captor.capture(), any())
        val taskDef = captor.firstValue
        assertThat(taskDef).isNotNull
        val chromeSftpPublicKey = taskDef.containerDefinitions().first { it.name() == "headless_chrome" }.environment()
            .firstOrNull { it.name() == "SFTP_PUBLIC_KEY" }
        val guacamoleSftpPrivateKey = taskDef.containerDefinitions().first { it.name() == "guacamole" }.environment()
            .firstOrNull { it.name() == "SFTP_PRIVATE_KEY_B64" }
        assertThat(chromeSftpPublicKey).isNotNull
        assertThat(guacamoleSftpPrivateKey).isNotNull

        val vncOpts = taskDef.containerDefinitions().first { it.name() == "headless_chrome" }.environment()
            .first { it.name() == "VNC_OPTS" }
        assertThat(vncOpts.value()).contains("-noclipboard")

    }

    @Test
    fun `Task definition configures clipboard correctly if allowed`() {
        reset(userAuthorizationService)
        whenever(
            userAuthorizationService.hasUserToolingPermission(
                "username",
                ToolingPermission.CLIPBOARD_OUT
            )
        ).doReturn(true)

        taskDeploymentService.runContainers("abcde", "username", listOf("team"), 100, 200, emptyList())
        val captor = argumentCaptor<TaskDefinition>()
        verify(awsCommunicator).registerTaskDefinition(any(), captor.capture(), any())
        val taskDef = captor.firstValue
        assertThat(taskDef).isNotNull
        val chromeSftpPublicKey = taskDef.containerDefinitions().first { it.name() == "headless_chrome" }.environment()
            .firstOrNull { it.name() == "SFTP_PUBLIC_KEY" }
        val guacamoleSftpPrivateKey = taskDef.containerDefinitions().first { it.name() == "guacamole" }.environment()
            .firstOrNull { it.name() == "SFTP_PRIVATE_KEY_B64" }
        assertThat(chromeSftpPublicKey).isNull()
        assertThat(guacamoleSftpPrivateKey).isNull()

        val vncOpts = taskDef.containerDefinitions().first { it.name() == "headless_chrome" }.environment()
            .first { it.name() == "VNC_OPTS" }
        assertThat(vncOpts.value()).doesNotContain("-noclipboard")

    }

    @Test
    fun `Disables upload and download if user doesn't have permission`(){
        reset(userAuthorizationService)
        whenever(
            userAuthorizationService.hasUserToolingPermission(
                eq("username"),
                any()
            )
        ).then { it.arguments[1] == ToolingPermission.FILE_TRANSFER_DOWNLOAD }

        taskDeploymentService.runContainers("abcde", "username", listOf("team"), 100, 200, emptyList())
        val captor = argumentCaptor<TaskDefinition>()
        verify(awsCommunicator).registerTaskDefinition(any(), captor.capture(), any())
        val taskDef = captor.firstValue
        assertThat(taskDef).isNotNull

        val guacamoleClientParams = taskDef.containerDefinitions().first { it.name() == "guacamole" }.environment()
            .first { it.name() == "CLIENT_PARAMS" }.value()

        assertThat(guacamoleClientParams).contains("sftp-disable-download=false")
        assertThat(guacamoleClientParams).contains("sftp-disable-upload=true")

    }

    @Configuration
    class AwsCommunicatorConfig {

        @Bean
        fun jwtParsingService() : JwtParsingService {
            return mock<JwtParsingService> {
                on { getUsernameFromJwt(any()) }.thenReturn("testUser")
            }
        }

        @Bean
        fun authService() : AuthenticationService {
            return mock<AuthenticationService>{}
        }

        @Bean
        @Primary
        fun awsCommunicator(lb : LoadBalancer, l : Listener, tg : TargetGroup, p : Policy, r : Role): AwsCommunicator {
            return mock<AwsCommunicator>{
                on { getLoadBalancerByName(any())}.thenReturn(lb)
                on { getAlbListenerByPort(any(), any())}.thenReturn(l)
                on { createTargetGroup(any(), any(), any(), any(), eq(TargetTypeEnum.IP))}.thenReturn(tg)
                on { createIamPolicy(any(), any(), any(), any())}.thenReturn(p)
                on { createIamRole(any(), any(), any())}.thenReturn(r)
            }
        }

        @Bean
        fun userAuthorizationService(): UserAuthorizationService {
            return mock<UserAuthorizationService>();
        }

        @Bean
        fun loadBalancer(): LoadBalancer {
            return mock<LoadBalancer> {
                on { loadBalancerName() }.thenReturn("loadBalancerName")
                on { loadBalancerArn() }.thenReturn( "loadBalancerARN")
                on { vpcId() }.thenReturn("vpcId")
            }
        }

        @Bean
        fun listener() : Listener {
            return mock<Listener> {
                on { listenerArn() }.thenReturn("listenerARN")
            }
        }

        @Bean
        fun targetGroup() : TargetGroup {
            return mock<TargetGroup> {
                on { targetGroupArn() }.thenReturn("targetGroupARN")
            }
        }

        @Bean
        fun policy() : Policy {
            return mock<Policy>{}
        }

        @Bean
        fun role() : Role {
            return mock<Role>{}
        }
    }
}
