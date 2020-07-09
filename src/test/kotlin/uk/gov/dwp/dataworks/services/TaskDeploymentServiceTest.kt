package uk.gov.dwp.dataworks.services

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule
import uk.gov.dwp.dataworks.Application
import uk.gov.dwp.dataworks.JWTObject
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.aws.AwsParsing

@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [Application::class])
@SpringBootTest(properties = ["orchestrationService.cognito_user_pool_id=id", "orchestrationService.jupyterhub_bucket_arn=testArn", "orchestrationService.aws_account_number=1234", "orchestrationService.aws_region=eu-west-2"])
class TaskDeploymentServiceTest {
    @Autowired
    private lateinit var taskDeploymentService: TaskDeploymentService

    @MockBean
    private lateinit var awsCommunicator: AwsCommunicator

    @MockBean
    private lateinit var activeUserTasks: ActiveUserTasks

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    @Before
    fun setupMocks() {
        whenever(awsCommunicator.getKmsKeyArn(anyString())).doAnswer {
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
}
