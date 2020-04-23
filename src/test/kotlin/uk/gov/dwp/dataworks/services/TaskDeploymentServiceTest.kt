package uk.gov.dwp.dataworks.services

import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule
import uk.gov.dwp.dataworks.Application
import uk.gov.dwp.dataworks.JWTObject

@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [Application::class])
@SpringBootTest(properties = ["orchestrationService.cognito_user_pool_id=id"])
class TaskDeploymentServiceTest {

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    @Autowired
    private lateinit var authService: AuthenticationService

    @Autowired
    private lateinit var taskDeploymentService: TaskDeploymentService

    private val decodedJWT = mock<DecodedJWT>()

    @BeforeEach
    fun setup() {
        val jwtObject = JWTObject(decodedJWT, "test_user")
        whenever(authService.validate(any())).thenReturn(jwtObject)
        whenever(configurationResolver.awsRegion).thenReturn(Region.EU_WEST_2)
    }

    @Test
    fun `Loads policy documents from classpath correctly`() {
        val taskRolePolicy = taskDeploymentService.taskRolePolicyDocument.inputStream.bufferedReader().use { it.readText() }
        assertThat(taskRolePolicy).isNotNull()

        val taskAssumeRoleDocument = taskDeploymentService.taskAssumeRoleDocument.inputStream.bufferedReader().use { it.readText() }
        assertThat(taskAssumeRoleDocument).isNotNull()
    }

    @Test
    fun `Additional permissions are replaced appropriately`() {
        val parsedDocs = taskDeploymentService.parsePolicyDocuments(listOf("permissionOne", "permissionTwo"))
        val taskRolePolicyString = parsedDocs.first
        assertThat(taskRolePolicyString).doesNotContain("ADDITIONAL_PERMISSIONS")
        assertThat(taskRolePolicyString).contains("\"permissionOne\",\"permissionTwo\"")
    }

    fun createDescribeRulesResponse(array: Collection<Rule>): DescribeRulesResponse {
        val list: Collection<Rule> = array
        val describeRulesResponse: DescribeRulesResponse = DescribeRulesResponse.builder().rules(list).build();
        return describeRulesResponse;
    }

    fun create1000(): Collection<Rule> {
        var oneThousandCol: Collection<Rule> = emptyList()
        var i = 0
        while (i <= 999) {
            oneThousandCol = oneThousandCol.plus(Rule.builder().priority(i.toString()).build())
            i++
        }
        return oneThousandCol
    }
}
