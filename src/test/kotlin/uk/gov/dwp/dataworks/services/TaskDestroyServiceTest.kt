package uk.gov.dwp.dataworks.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import uk.gov.dwp.dataworks.Application
import uk.gov.dwp.dataworks.UserTask
import uk.gov.dwp.dataworks.aws.AwsCommunicator

@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [Application::class])
@SpringBootTest(properties = ["orchestrationService.cognito_user_pool_id=id"])
class TaskDestroyServiceTest {
    @Autowired
    private lateinit var taskDestroyService: TaskDestroyService
    @MockBean
    private lateinit var awsCommunicator: AwsCommunicator
    @SpyBean
    private lateinit var activeUserTasks: ActiveUserTasks

    private val successfulFunction = {  }
    private val exceptionFunction = { throw Exception("fail") }

    @Before
    fun setup() {
        doNothing().whenever(awsCommunicator).deleteTargetGroup(any(), any())
        doNothing().whenever(awsCommunicator).deleteAlbRoutingRule(any(), any())
        doNothing().whenever(awsCommunicator).deleteEcsService(any(), any(), any())
        doNothing().whenever(awsCommunicator).detachIamPolicyFromRole(any(), any(), any())
        doNothing().whenever(awsCommunicator).deleteIamRole(any(), any())
        doNothing().whenever(awsCommunicator).deleteIamPolicy(any(), any())
    }

    @Test
    fun `Does not remove entry from DynamoDB when one deletion fails`() {
        val testUserTask = UserTask("correlation","username", "tgArn", "albArn", "ecsCN", "ecsSN", "iamRN", "iamPArn", "iamTArn")
        doReturn(testUserTask).whenever(activeUserTasks).get(any())
        doAnswer { throw Exception("fail") }.whenever(awsCommunicator).deleteTargetGroup(any(), any())

        taskDestroyService.destroyServices("username")
        verify(activeUserTasks, never()).remove("correlation", "username")

        doNothing().whenever(awsCommunicator).deleteTargetGroup(any(), any())
    }

    @Test
    fun `Does not remove entry from DynamoDB when multiple deletions fail`() {
        val testUserTask = UserTask("correlation","username", "tgArn", null, "ecsCN", "ecsSN", "iamRN", "iamPArn", "iamTArn")
        doReturn(testUserTask).whenever(activeUserTasks).get(any())
        doAnswer { throw Exception("fail") }.whenever(awsCommunicator).deleteTargetGroup(any(), any())
        doAnswer { throw Exception("fail") }.whenever(awsCommunicator).deleteAlbRoutingRule(any(), any())

        taskDestroyService.destroyServices("username")
        verify(activeUserTasks, never()).remove("correlation", "username")

        doNothing().whenever(awsCommunicator).deleteTargetGroup(any(), any())
        doNothing().whenever(awsCommunicator).deleteAlbRoutingRule(any(), any())
    }

    @Test
    fun `Removes entry from DynamoDB when no deletions fail`() {
        val testUserTask = UserTask("correlation","username", "tgArn", "albArn", "ecsCN", "ecsSN", "iamRN", "iamPArn", "iamTArn")
        doReturn(testUserTask).whenever(activeUserTasks).get(any())

        taskDestroyService.destroyServices("username")
        verify(activeUserTasks).remove("correlation", "username")
    }

    @Test
    fun `Try delete returns true when one requisite is null`() {
        val actual = taskDestroyService.tryDeleteResource("resource", null, function = successfulFunction)
        assertThat(actual).isTrue()
    }

    @Test
    fun `Try delete returns true when one requisite is Blank`() {
        val actual = taskDestroyService.tryDeleteResource("resource", "", function = successfulFunction)
        assertThat(actual).isTrue()
    }

    @Test
    fun `Try delete returns true when multiple requisites are null`() {
        val actual = taskDestroyService.tryDeleteResource("resource", null, "abc", null, function = successfulFunction)
        assertThat(actual).isTrue()
    }

    @Test
    fun `Try delete continues when no requisites sent`() {
        val actual = taskDestroyService.tryDeleteResource("resource", function = exceptionFunction)
        assertThat(actual).isFalse()
    }

    @Test
    fun `Try delete returns true when function does not throw exception`() {
        val actual = taskDestroyService.tryDeleteResource("resource", "not-null", function = successfulFunction)
        assertThat(actual).isTrue()
    }

    @Test
    fun `Try delete returns false when function throws exception`() {
        val actual = taskDestroyService.tryDeleteResource("resource", "not-null", function = exceptionFunction)
        assertThat(actual).isFalse()
    }
}
