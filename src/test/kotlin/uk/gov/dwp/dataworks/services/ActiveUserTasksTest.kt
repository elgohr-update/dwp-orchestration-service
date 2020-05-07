package uk.gov.dwp.dataworks.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import uk.gov.dwp.dataworks.UserHasNoTasksException
import uk.gov.dwp.dataworks.aws.AwsCommunicator

@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [ActiveUserTasks::class])
class ActiveUserTasksTest {
    @Autowired
    private lateinit var activeUserTasks: ActiveUserTasks

    @MockBean
    private lateinit var awsCommunicator: AwsCommunicator

    @Before
    fun setup() {
        whenever(awsCommunicator.getDynamoDeploymentEntry(any())).thenAnswer { UserHasNoTasksException("") }
    }

    @Test
    fun `Exception thrown when getting a value not found`() {
        val userName = "non-existent"
        assertThatCode { activeUserTasks.get(userName) }
                .hasMessage("No tasks found for $userName")
                .isInstanceOf(UserHasNoTasksException::class.java)
    }
}
