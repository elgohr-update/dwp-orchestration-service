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
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.regions.Region
import uk.gov.dwp.dataworks.Application
import uk.gov.dwp.dataworks.JWTObject
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.aws.AwsParsing
import java.lang.module.ModuleDescriptor.read

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

    @Value("classpath:policyDocuments/taskRolePolicy.json")
    lateinit var taskRoleDocument: Resource
    @Value("classpath:policyDocuments/jupyterBucketAccessPolicy.json")
    lateinit var jupyterBucketAccessDocument: Resource

    @Test
    fun `Reads policy documents from correctly`() {
        val taskRolePolicy = taskRoleDocument.inputStream.bufferedReader()
        assertThat(taskRolePolicy).isNotNull
        assertThat(taskRolePolicy.use { it.readText() }).contains(" \"Sid\": \"ecstaskrolepolicy\",\n")

        val jupyterBucketAccessPolicy = jupyterBucketAccessDocument.inputStream.bufferedReader()
        assertThat(jupyterBucketAccessPolicy).isNotNull
        assertThat(jupyterBucketAccessPolicy.use { it.readText() }).contains("\"Sid\": \"jupyters3accessdocument\",\n")
    }

    @Test
    fun `Single set of additional attributes are replaced appropriately`() {
        val taskRolePolicyString = awsParsing.parsePolicyDocument(taskRoleDocument, mapOf("ecstaskrolepolicy" to listOf("permissionOne", "permissionTwo")), "Action")
        assertThat(taskRolePolicyString).doesNotContain("[]")
        assertThat(taskRolePolicyString).contains("\"permissionOne\",\"permissionTwo\"")
    }

    @Test
    fun `Multiple additional attributes are replaced appropriately`() {
        val taskRolePolicyString = awsParsing.parsePolicyDocument(jupyterBucketAccessDocument, mapOf("jupyters3list" to listOf("permissionOne", "permissionTwo"), "jupyters3accessdocument" to listOf("permissionThree", "permissionFour")), "Resource")
        assertThat(taskRolePolicyString).doesNotContain("[]")
        assertThat(taskRolePolicyString).contains("\"permissionOne\",\"permissionTwo\"")
        assertThat(taskRolePolicyString).contains("\"permissionThree\",\"permissionFour\"")
    }

    @Test
    fun `Wrong key attribute throws correct Exception`() {
        Assertions.assertThatCode { awsParsing.parsePolicyDocument(jupyterBucketAccessDocument, mapOf("jupyters3list" to listOf("permissionOne", "permissionTwo")), "") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("statementKeyToUpdate does not match expected values: \"Resource\" or \"Action\"")
    }

    @Test
    fun `Returns proper case for JSON keys, as required by AWS`() {
        val taskRolePolicyString = awsParsing.parsePolicyDocument(taskRoleDocument, mapOf("ecstaskrolepolicy" to listOf("permissionOne", "permissionTwo")), "Action")
        assertThat(taskRolePolicyString).contains("Statement").contains("Resource").contains("Effect").contains("Version").contains("Action")
    }

    @Test
    fun `Attributes are assigned to the correct key`() {
        val taskRolePolicyString = awsParsing.parsePolicyDocument(jupyterBucketAccessDocument, mapOf("jupyters3list" to listOf("permissionOne")), "Action")
        assertThat(taskRolePolicyString).contains("\"Action\":[\"s3:ListBucket\",\"permissionOne\"]")
    }
}
