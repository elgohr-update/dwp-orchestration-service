package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.dwp.dataworks.TaskDestroyException
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Service
class TaskDestroyService {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(TaskDestroyService::class.java))
    }

    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator

    @Autowired
    private lateinit var activeUserTasks: ActiveUserTasks

    fun destroyServices(userName: String) {
        val userTasks = activeUserTasks.get(userName)

        try {
            awsCommunicator.deleteTargetGroup(userTasks.correlationId, userTasks.targetGroupArn)
            awsCommunicator.deleteAlbRoutingRule(userTasks.correlationId, userTasks.albRoutingRuleArn)
            awsCommunicator.deleteEcsService(userTasks.correlationId, userTasks.ecsClusterName, userTasks.ecsServiceName)
            awsCommunicator.detachIamPolicyFromRole(userTasks.correlationId, userTasks.iamRoleName, userTasks.iamPolicyArn)
            awsCommunicator.deleteIamPolicy(userTasks.correlationId, userTasks.iamPolicyArn)
            awsCommunicator.deleteIamRole(userTasks.correlationId, userTasks.iamRoleName)
        } catch (e: Exception) {
            logger.error("One or more resources failed to be removed", e, "correlation_id" to userTasks.correlationId)
            throw TaskDestroyException("One or more resources failed to be removed", e)
        }

        activeUserTasks.remove(userTasks.correlationId, userName)
    }
}
