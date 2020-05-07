package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.UserHasNoTasksException
import uk.gov.dwp.dataworks.UserTask
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.logging.DataworksLogger
import javax.annotation.PostConstruct

@Component
class ActiveUserTasks {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(ActiveUserTasks::class.java))
        const val dynamoTableName = "orchestration_service_user_tasks"
        const val dynamoPrimaryKey = "userName"
    }

    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator

    @PostConstruct
    fun createDynamoTable() {
        awsCommunicator.createDynamoDbTable(dynamoTableName, UserTask.attributes(), dynamoPrimaryKey)
    }

    fun initialiseDeploymentEntry(correlationId: String, userName: String) {
        awsCommunicator.createDynamoDeploymentEntry(correlationId, userName)
    }

    fun get(userName: String): UserTask {
        try {
            val item = awsCommunicator.getDynamoDeploymentEntry(userName).item()
                    .mapValues { it.value.s() }
            return UserTask.from(item.withDefault { "" })
        } catch (e: Exception) {
            throw UserHasNoTasksException("No tasks found for $userName")
        }
    }

    fun contains(userName: String): Boolean {
        return awsCommunicator.getDynamoDeploymentEntry(userName).hasItem()
    }

    fun remove(correlationId: String, userName: String) {
        awsCommunicator.removeDynamoDeploymentEntry(correlationId, userName)
    }
}
