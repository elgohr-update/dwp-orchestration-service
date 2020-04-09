package uk.gov.dwp.dataworks.services

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule
import uk.gov.dwp.dataworks.Application
import java.lang.Exception
import java.util.*


@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [Application::class])
@SpringBootTest(properties = ["orchestrationService.cognito_user_pool_id=id"])
class TaskDeploymentServiceTest{

    @Autowired
    private lateinit var configService: ConfigurationService
    @Autowired
    private lateinit var authService: AuthenticationService
    @Autowired
    private lateinit var taskDeploymentService: TaskDeploymentService


    val nonConsecutiveCol : Collection<Rule> = listOf(Rule.builder().priority("0").build(), Rule.builder().priority("1").build(), Rule.builder().priority("3").build())

    @Test
    fun testPriorityNumberForNoRuleSetExpectZero(){
        val actual =  taskDeploymentService.getVacantPriorityValue(createDescribeRulesResponse(ArrayList<Rule>()))
        Assert.assertEquals(0, actual)
    }

    @Test
    fun testPriorityNumberForNonConsecutiveRuleSetExpectTwo(){
        val actual =  taskDeploymentService.getVacantPriorityValue(createDescribeRulesResponse(nonConsecutiveCol))
        Assert.assertEquals(2, actual)
    }

    @Test (expected = Exception::class)
    fun testPriorityNumberFor1000PlusExpectError(){
        val actual =  taskDeploymentService.getVacantPriorityValue(createDescribeRulesResponse(create1000()))
    }

    fun  createDescribeRulesResponse(array: Collection<Rule>): DescribeRulesResponse {
        val  list: Collection<Rule> = array
        val describeRulesResponse: DescribeRulesResponse = DescribeRulesResponse.builder().rules(list).build();
            return describeRulesResponse;
    }

    fun create1000() : Collection<Rule> {
        var oneThousandCol: Collection<Rule> = emptyList()
        var i = 0
        while(i<=999) {
            oneThousandCol = oneThousandCol.plus(Rule.builder().priority(i.toString()).build())
            i++
        }
        return oneThousandCol
    }
}
