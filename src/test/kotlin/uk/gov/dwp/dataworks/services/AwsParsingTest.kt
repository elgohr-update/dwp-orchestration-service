package uk.gov.dwp.dataworks.services

import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.regions.Region
import uk.gov.dwp.dataworks.Application
import uk.gov.dwp.dataworks.JWTObject
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.aws.AwsParsing

@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [Application::class])
@SpringBootTest(properties = ["orchestrationService.cognito_user_pool_id=id"])
class AwsParsingTest {

    @Autowired
    private lateinit var awsParsing: AwsParsing

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    @Autowired
    private lateinit var authService: AuthenticationService

    @MockBean
    private lateinit var awsCommunicator: AwsCommunicator

    private val decodedJWT = mock<DecodedJWT>()

    @Test
    fun `Loads policy documents from classpath correctly`() {
        val taskRolePolicy = ClassPathResource("policyDocuments/jupyterBucketAccessPolicy.json")
        assertThat(taskRolePolicy).isNotNull()

        val taskAssumeRoleDocument = ClassPathResource("policyDocuments/taskRolePolicy.json")
        assertThat(taskAssumeRoleDocument).isNotNull()
    }

    @Test
    fun `Single set of additional attributes are replaced appropriately`() {
        val taskRolePolicyString = awsParsing.parsePolicyDocument("policyDocuments/taskRolePolicy.json", mapOf("ecs-task-role-policy" to listOf("permissionOne", "permissionTwo")), "Action")
        assertThat(taskRolePolicyString).doesNotContain("[]")
        assertThat(taskRolePolicyString).contains("\"permissionOne\",\"permissionTwo\"")
    }

    @Test
    fun `Multiple additional attributes are replaced appropriately`() {
        val taskRolePolicyString = awsParsing.parsePolicyDocument("policyDocuments/jupyterBucketAccessPolicy.json", mapOf("jupyter-s3-list" to listOf("permissionOne", "permissionTwo"), "jupyter-s3-access-document" to listOf("permissionThree", "permissionFour")), "Resource")
        assertThat(taskRolePolicyString).doesNotContain("[]")
        assertThat(taskRolePolicyString).contains("\"permissionOne\",\"permissionTwo\"")
        assertThat(taskRolePolicyString).contains("\"permissionThree\",\"permissionFour\"")
    }

    @Test
    fun `Wrong key attribute throws correct Exception`() {
        Assertions.assertThatCode { awsParsing.parsePolicyDocument("policyDocuments/jupyterBucketAccessPolicy.json", mapOf("jupyter-s3-list" to listOf("permissionOne", "permissionTwo")), "") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("statementKeyToUpdate does not match expected values: \"Resource\" or \"Action\"")
    }

    @Test
    fun `Returns proper case for JSON keys, as required by AWS`() {
        val taskRolePolicyString = awsParsing.parsePolicyDocument("policyDocuments/taskRolePolicy.json", mapOf("ecs-task-role-policy" to listOf("permissionOne", "permissionTwo")), "Action")
        assertThat(taskRolePolicyString).contains("Statement").contains("Resource").contains("Effect").contains("Version").contains("Action")
    }

    @Test
    fun `Attributes are assigned to the correct key`() {
        val taskRolePolicyString = awsParsing.parsePolicyDocument("policyDocuments/jupyterBucketAccessPolicy.json", mapOf("jupyter-s3-list" to listOf("permissionOne")), "Action")
        assertThat(taskRolePolicyString).contains("\"Action\":[\"s3:ListBucket\",\"permissionOne\"]")
    }
}
