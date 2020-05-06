package uk.gov.dwp.dataworks.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
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
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.DeleteServiceRequest
import software.amazon.awssdk.services.ecs.model.ServiceNotFoundException
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DeleteRuleRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DeleteTargetGroupRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupAttributesRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule
import software.amazon.awssdk.services.elasticloadbalancingv2.model.RuleNotFoundException
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupNotFoundException
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.DeletePolicyRequest
import software.amazon.awssdk.services.iam.model.DeleteRoleRequest
import software.amazon.awssdk.services.iam.model.DetachRolePolicyRequest
import software.amazon.awssdk.services.iam.model.NoSuchEntityException
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
    private val mockEcsClient = Mockito.mock(EcsClient::class.java)
    private val mockIamClient = Mockito.mock(IamClient::class.java)
    private val emptyAlb = LoadBalancer.builder().build()
    private val emptyListener = Listener.builder().port(0).build()

    @Before
    fun setup() {
        whenever(awsClients.albClient).thenReturn(mockAlbClient)
        whenever(awsClients.ecsClient).thenReturn(mockEcsClient)
        whenever(awsClients.iamClient).thenReturn(mockIamClient)
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

    @Test
    fun `Delete target group takes no action when target group not found`() {
        whenever(mockAlbClient.describeTargetGroupAttributes(any<DescribeTargetGroupAttributesRequest>()))
                .thenThrow(TargetGroupNotFoundException.builder().build())
        awsCommunicator.deleteTargetGroup("correlation", "tgArn")
        verify(mockAlbClient, never()).deleteTargetGroup(any<DeleteTargetGroupRequest>())
    }

    @Test
    fun `Delete target group attempts deletion when target group found`() {
        whenever(mockAlbClient.describeTargetGroupAttributes(any<DescribeTargetGroupAttributesRequest>()))
                .thenReturn(null)
        awsCommunicator.deleteTargetGroup("correlation", "tgArn")
        verify(mockAlbClient).deleteTargetGroup(any<DeleteTargetGroupRequest>())
    }

    @Test
    fun `Delete alb routing does not fail when rule not found`() {
        whenever(mockAlbClient.deleteRule(any<DeleteRuleRequest>()))
                .thenThrow(RuleNotFoundException.builder().build())
        assertThatCode { awsCommunicator.deleteAlbRoutingRule("correlation", "ruleArn") }
                .doesNotThrowAnyException()
    }

    @Test
    fun `Delete alb routing rule attempts deletion when target group found`() {
        whenever(mockAlbClient.deleteRule(any<DeleteRuleRequest>()))
                .thenReturn(null)
        awsCommunicator.deleteAlbRoutingRule("correlation", "ruleArn")
        verify(mockAlbClient).deleteRule(any<DeleteRuleRequest>())
    }

    @Test
    fun `Delete ecs service does not fail when service not found`() {
        whenever(mockEcsClient.deleteService(any<DeleteServiceRequest>()))
                .thenThrow(ServiceNotFoundException.builder().build())
        assertThatCode { awsCommunicator.deleteEcsService("correlation", "clusterName", "serviceName") }.doesNotThrowAnyException()
    }

    @Test
    fun `Delete ecs service attempts deletion when service found`() {
        whenever(mockEcsClient.deleteService(any<DeleteServiceRequest>()))
                .thenReturn(null)
        awsCommunicator.deleteEcsService("correlation", "clusterName", "serviceName")
        verify(mockEcsClient).deleteService(any<DeleteServiceRequest>())
    }

    @Test
    fun `Delete iam policy does not fail when iam policy not found`() {
        whenever(mockIamClient.deletePolicy(any<DeletePolicyRequest>()))
                .thenThrow(NoSuchEntityException.builder().build())
        assertThatCode { awsCommunicator.deleteIamPolicy("correlation", "policyArn") }
                .doesNotThrowAnyException()
    }

    @Test
    fun `Delete iam policy attempts deletion when iam policy found`() {
        whenever(mockIamClient.deletePolicy(any<DeletePolicyRequest>()))
                .thenReturn(null)
        awsCommunicator.deleteIamPolicy("correlation", "policyArn")
        verify(mockIamClient).deletePolicy(any<DeletePolicyRequest>())
    }

    @Test
    fun `Delete iam role does not fail when iam role not found`() {
        whenever(mockIamClient.deleteRole(any<DeleteRoleRequest>()))
                .thenThrow(NoSuchEntityException.builder().build())
        assertThatCode { awsCommunicator.deleteIamRole("correlation", "roleName") }
                .doesNotThrowAnyException()
    }

    @Test
    fun `Delete iam role attempts deletion when iam role found`() {
        whenever(mockIamClient.deleteRole(any<DeleteRoleRequest>()))
                .thenReturn(null)
        awsCommunicator.deleteIamRole("correlation", "roleName")
        verify(mockIamClient).deleteRole(any<DeleteRoleRequest>())
    }

    @Test
    fun `Detach iam policy does not fail when resource not found`() {
        whenever(mockIamClient.detachRolePolicy(any<DetachRolePolicyRequest>()))
                .thenThrow(NoSuchEntityException.builder().build())
        assertThatCode { awsCommunicator.detachIamPolicyFromRole("correlation", "roleName", "policyArn") }
                .doesNotThrowAnyException()
    }

    @Test
    fun `Detach iam policy attempts detachment when both resources found`() {
        whenever(mockIamClient.detachRolePolicy(any<DetachRolePolicyRequest>()))
                .thenReturn(null)
        awsCommunicator.detachIamPolicyFromRole("correlation", "roleName", "policyArn")
        verify(mockIamClient).detachRolePolicy(any<DetachRolePolicyRequest>())
    }


    private fun rule(priority: String): Rule {
        return Rule.builder().priority(priority).build()
    }
}
