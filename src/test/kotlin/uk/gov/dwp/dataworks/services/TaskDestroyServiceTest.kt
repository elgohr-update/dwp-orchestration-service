package uk.gov.dwp.dataworks.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
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

    private val testUserTask = UserTask("","","", "", "", "", "", "")

    @Before
    fun setup() {
        doNothing().whenever(awsCommunicator).deleteTargetGroup(any(), any())
        doNothing().whenever(awsCommunicator).deleteAlbRoutingRule(any(), any())
        doNothing().whenever(awsCommunicator).deleteEcsService(any(), any(), any())
        doNothing().whenever(awsCommunicator).deleteIamRole(any(), any())
        doNothing().whenever(awsCommunicator).deleteIamPolicy(any(), any())
        doReturn(testUserTask).whenever(activeUserTasks).get(any())
    }

    @Test
    fun `Exception thrown when a resource fails to be removed`() {
        val exception = Exception("boom")
        doAnswer { throw exception }.whenever(awsCommunicator).deleteTargetGroup(any(), any())

        assertThatCode { taskDestroyService.destroyServices("testUsername") }
                .hasMessage("One or more resources failed to be removed")
                .hasCause(exception)
    }
}
