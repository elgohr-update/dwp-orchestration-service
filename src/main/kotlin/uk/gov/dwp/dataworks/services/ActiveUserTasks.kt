package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import uk.gov.dwp.dataworks.UserHasNoTasksException
import uk.gov.dwp.dataworks.UserTask
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.logging.DataworksLogger
import javax.annotation.PostConstruct

@Component
class ActiveUserTasks {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(ActiveUserTasks::class.java))
    }

    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator

    private val dynamoTableName = "orchestration_service_user_tasks"
    private val dynamoPrimaryKey = "userName"

    @PostConstruct
    fun createDynamoTable() {
        awsCommunicator.createDynamoDbTable(dynamoTableName, UserTask.attributes(), dynamoPrimaryKey)
    }

    fun put(userTask: UserTask) {
        val attributes = userTask.toMap().mapValues { AttributeValue.builder().s(it.value).build() }
        awsCommunicator.putDynamoDbItem(userTask.correlationId, dynamoTableName, attributes)
    }

    fun get(userName: String): UserTask {
        try {
            val item = awsCommunicator.getDynamoDbItem(dynamoTableName, dynamoPrimaryKey, userName).item()
                    .mapValues { it.value.s() }
            return UserTask.from(item)
        } catch (e: Exception) {
            throw UserHasNoTasksException("No tasks found for $userName")
        }
    }

    fun contains(userName: String): Boolean {
        return awsCommunicator.getDynamoDbItem(dynamoTableName, dynamoPrimaryKey, userName).hasItem()
    }

    fun remove(correlationId: String, userName: String) {
        awsCommunicator.removeDynamoDbItem(correlationId, dynamoTableName, dynamoPrimaryKey, userName)
    }
}
