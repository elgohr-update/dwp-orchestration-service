package uk.gov.dwp.dataworks.services

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
import java.util.ArrayList

@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [Application::class])
@SpringBootTest(properties = ["orchestrationService.cognito_user_pool_id=id"])
class TaskDeploymentServiceTest {

    @Autowired
    private lateinit var configService: ConfigurationService

    @Autowired
    private lateinit var authService: AuthenticationService

    @Autowired
    private lateinit var taskDeploymentService: TaskDeploymentService

    @BeforeEach
    fun setup() {
        whenever(authService.validate(any())).thenReturn(mock())
        whenever(configService.awsRegion).thenReturn(Region.EU_WEST_2)
    }

    val nonConsecutiveCol: Collection<Rule> = listOf(Rule.builder().priority("0").build(), Rule.builder().priority("1").build(), Rule.builder().priority("3").build())

    @Test
    fun `Empty load balancer priorities returns 0`() {
        val actual = taskDeploymentService.getVacantPriorityValue(createDescribeRulesResponse(ArrayList<Rule>()))
        assertThat(actual).isEqualTo(0)
    }

    @Test
    fun `Non-consecutive load balancer priorities sets lowest available value`() {
        val actual = taskDeploymentService.getVacantPriorityValue(createDescribeRulesResponse(nonConsecutiveCol))
        assertThat(actual).isEqualTo(2)
    }

    @Test(expected = Exception::class)
    fun `Load balancer priority of 1000 should error`() {
        taskDeploymentService.getVacantPriorityValue(createDescribeRulesResponse(create1000()))
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
