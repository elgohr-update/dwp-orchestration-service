package uk.gov.dwp.dataworks.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule
import uk.gov.dwp.dataworks.MultipleListenersMatchedException
import uk.gov.dwp.dataworks.MultipleLoadBalancersMatchedException
import uk.gov.dwp.dataworks.UpperRuleLimitReachedException
import uk.gov.dwp.dataworks.aws.AwsClients
import uk.gov.dwp.dataworks.aws.AwsCommunicator

@RunWith(SpringRunner::class)
@WebMvcTest(AwsCommunicator::class, ConfigurationResolver::class)
class AwsCommunicatorTest {
    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator
    @MockBean
    private lateinit var awsClients: AwsClients

    private val mockAlbClient = Mockito.mock(ElasticLoadBalancingV2Client::class.java)
    private val emptyAlb = LoadBalancer.builder().build()
    private val emptyListener = Listener.builder().port(0).build()

    @Before
    fun setup() {
        whenever(awsClients.albClient).thenReturn(mockAlbClient)
    }

    @Test
    fun `getLoadBalancerByName Throws Exception when multiple Load Balancers found`() {
        val loadBalancers = DescribeLoadBalancersResponse.builder().loadBalancers(emptyAlb, emptyAlb).build()
        whenever(mockAlbClient.describeLoadBalancers(any<DescribeLoadBalancersRequest>())).thenReturn(loadBalancers)
        assertThatCode { awsCommunicator.getLoadBalancerByName("dummy") }
                .isInstanceOf(MultipleLoadBalancersMatchedException::class.java)
                .hasMessage("Expected to find 1 Load Balancer with name dummy, actually found 2")
        }

    @Test
    fun `getLoadBalancerByName Throws Exception when no Load Balancers found`() {
        val loadBalancers = DescribeLoadBalancersResponse.builder().build()
        whenever(mockAlbClient.describeLoadBalancers(any<DescribeLoadBalancersRequest>())).thenReturn(loadBalancers)
        assertThatCode { awsCommunicator.getLoadBalancerByName("dummy") }
                .isInstanceOf(MultipleLoadBalancersMatchedException::class.java)
                .hasMessage("Expected to find 1 Load Balancer with name dummy, actually found 0")
    }

    @Test
    fun `getLoadBalancerByName Returns correct load balancer when found`() {
        val loadBalancers = DescribeLoadBalancersResponse.builder().loadBalancers(emptyAlb).build()
        whenever(mockAlbClient.describeLoadBalancers(any<DescribeLoadBalancersRequest>())).thenReturn(loadBalancers)
        assertThat(awsCommunicator.getLoadBalancerByName("")).isEqualTo(emptyAlb)
    }

    @Test
    fun `getAlbListenerByPort Throws Exception when alb listener not found`() {
        val listeners = DescribeListenersResponse.builder().listeners(emptyListener).build()
        whenever(mockAlbClient.describeListeners(any<DescribeListenersRequest>())).thenReturn(listeners)
        assertThatCode { awsCommunicator.getAlbListenerByPort("dummy", 400) }
                .isInstanceOf(MultipleListenersMatchedException::class.java)
                .hasMessage("Expected to find 1 Listener with port 400, actually found 0")
    }

    @Test
    fun `getAlbListenerByPort Throws Exception when no listeners found`() {
        val listeners = DescribeListenersResponse.builder().build()
        whenever(mockAlbClient.describeListeners(any<DescribeListenersRequest>())).thenReturn(listeners)
        assertThatCode { awsCommunicator.getAlbListenerByPort("", 400) }
                .isInstanceOf(MultipleListenersMatchedException::class.java)
                .hasMessage("Expected to find 1 Listener with port 400, actually found 0")
    }

    @Test
    fun `getAlbListenerByPort Returns listener when found`() {
        val listeners = DescribeListenersResponse.builder().listeners(emptyListener).build()
        whenever(mockAlbClient.describeListeners(any<DescribeListenersRequest>())).thenReturn(listeners)
        assertThat(awsCommunicator.getAlbListenerByPort("", 0)).isEqualTo(emptyListener)
    }

    @Test
    fun `Empty load balancer priorities returns 1`() {
        assertThat(awsCommunicator.calculateVacantPriorityValue(emptyList())).isEqualTo(1)
    }

    @Test
    fun `Non-consecutive load balancer priorities sets lowest available value`() {
        val actual = awsCommunicator.calculateVacantPriorityValue(listOf(rule("1"), rule("3")))
        assertThat(actual).isEqualTo(2)
    }

    @Test
    fun `Priority calculation does not take default into account`() {
        val actual = awsCommunicator.calculateVacantPriorityValue(listOf(rule("default")))
        assertThat(actual).isEqualTo(1)
    }

    @Test
    fun `Throws Exception when no priorities remain`() {
        val oneThousandRules = mutableListOf<Rule>()
        for (i in 1..999) oneThousandRules.add(rule(i.toString()))

        assertThatCode { awsCommunicator.calculateVacantPriorityValue(oneThousandRules) }
                .isInstanceOf(UpperRuleLimitReachedException::class.java)
                .hasMessage("The upper limit of 1000 rules has been reached on this listener.")
    }

    private fun rule(priority: String): Rule {
        return Rule.builder().priority(priority).build()
    }
}
