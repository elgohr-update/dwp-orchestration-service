package uk.gov.dwp.dataworks.services

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.Resource
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import uk.gov.dwp.dataworks.Application
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.aws.AwsParsing

@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [Application::class])
@SpringBootTest(properties = ["orchestrationService.cognito_user_pool_id=id"])
class AwsParsingTest {

    @Autowired
    private lateinit var awsParsing: AwsParsing

    @InjectMocks
    private lateinit var configurationResolver: ConfigurationResolver

    @MockBean
    private lateinit var taskDeploymentService: TaskDeploymentService

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
        assertThat(taskRolePolicy.use { it.readText() }).contains(" \"Sid\": \"ecstaskrolepolicy\",")

        val jupyterBucketAccessPolicy = jupyterBucketAccessDocument.inputStream.bufferedReader()
        assertThat(jupyterBucketAccessPolicy).isNotNull
        assertThat(jupyterBucketAccessPolicy.use { it.readText() }).contains("\"Sid\": \"jupyters3accessdocument\",")
    }

    @Test
    fun `Single set of additional attributes are replaced appropriately`() {
        val taskRolePolicyString = awsParsing.parsePolicyDocument(taskRoleDocument, mapOf("ecstaskrolepolicy" to listOf("permissionOne", "permissionTwo")), "Action")
        assertThat(taskRolePolicyString).doesNotContain("[]")
        assertThat(taskRolePolicyString).contains("\"permissionOne\",\"permissionTwo\"")
    }

    @Test
    fun `Multiple additional attributes are replaced appropriately`() {
        val taskRolePolicyString = awsParsing.parsePolicyDocument(jupyterBucketAccessDocument, mapOf("jupyterkmsaccessdocument" to listOf("permissionOne", "permissionTwo"), "jupyters3list" to listOf("permissionThree", "permissionFour"), "jupyters3accessdocument" to listOf("permissionFive", "permissionSix")), "Resource")
        assertThat(taskRolePolicyString).doesNotContain("[]")
        assertThat(taskRolePolicyString).contains("\"permissionOne\",\"permissionTwo\"")
        assertThat(taskRolePolicyString).contains("\"permissionThree\",\"permissionFour\"")
        assertThat(taskRolePolicyString).contains("\"permissionFive\",\"permissionSix\"")
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
