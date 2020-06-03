package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
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

    /**
     * Attempt to destroy all the services that are listed in the DynamoDB table for a given
     * [userName]. Each component of the analytical environment is attempted to be destroyed
     * and each destroy method should pass if the resource is not present. This allows us to
     * only capture true errors from these methods and log them appropriately.
     *
     * If all deletions complete successfully the entry will be deleted from DynamoDB.
     */
    fun destroyServices(userName: String) {
        val userTasks = activeUserTasks.get(userName)

        val destroyAttempts = listOf(
            tryDeleteResource("alb_rule", userTasks.albRoutingRuleArn)
                { awsCommunicator.deleteAlbRoutingRule(userTasks.correlationId, userTasks.albRoutingRuleArn!!) },
            tryDeleteResource("target_group", userTasks.targetGroupArn)
                { awsCommunicator.deleteTargetGroup(userTasks.correlationId, userTasks.targetGroupArn!!) },
            tryDeleteResource("ecs_service", userTasks.ecsClusterName, userTasks.ecsServiceName)
                { awsCommunicator.deleteEcsService(userTasks.correlationId, userTasks.ecsClusterName!!, userTasks.ecsServiceName!!) },
            tryDeleteResource("detach_iam_policy", userTasks.iamRoleName, userTasks.iamPolicyUserArn)
                { awsCommunicator.detachIamPolicyFromRole(userTasks.correlationId, userTasks.iamRoleName!!, userTasks.iamPolicyUserArn!!) },
            tryDeleteResource("iam_policy", userTasks.iamPolicyUserArn)
                { awsCommunicator.deleteIamPolicy(userTasks.correlationId, userTasks.iamPolicyUserArn!!) },
            tryDeleteResource("detach_iam_policy", userTasks.iamRoleName, userTasks.iamPolicyTaskArn)
                { awsCommunicator.detachIamPolicyFromRole(userTasks.correlationId, userTasks.iamRoleName!!, userTasks.iamPolicyTaskArn!!) },
            tryDeleteResource("iam_policy", userTasks.iamPolicyTaskArn)
                { awsCommunicator.deleteIamPolicy(userTasks.correlationId, userTasks.iamPolicyTaskArn!!) },
            tryDeleteResource("iam_role", userTasks.iamRoleName)
                { awsCommunicator.deleteIamRole(userTasks.correlationId, userTasks.iamRoleName!!) }
        )

        if (destroyAttempts.none { !it }) {
            activeUserTasks.remove(userTasks.correlationId, userName)
        }
    }

    /**
     * Helper function to attempt to run a deletion request, capturing any errors and logging them.
     *
     * If any of the values in [prerequisiteValues] are null, then deletion will not be attempted.
     * This represents the case where a creation request has not completed successfully and
     * so we need to destroy only the services which succeeded creation.
     *
     * @return true when deletion successful or not applicable. False when deletion fails
     */
    fun tryDeleteResource(resourceName: String, vararg prerequisiteValues: String?, function: () -> Unit): Boolean {
        if (prerequisiteValues.isNotEmpty() && prerequisiteValues.any { it == null || it.isBlank() }) {
            return true
        }

        return try {
            function.invoke()
            true
        } catch (e: Exception) {
            logger.error("Failed to destroy $resourceName", e)
            false
        }
    }

    fun cleanupDestroy(activeUsers: List<String>){
        activeUsers.forEach { user ->
            destroyServices(user)
        }
    }
}
