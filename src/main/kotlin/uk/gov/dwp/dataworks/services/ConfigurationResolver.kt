package uk.gov.dwp.dataworks.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import software.amazon.awssdk.regions.Region
import uk.gov.dwp.dataworks.SystemArgumentException
import uk.gov.dwp.dataworks.services.ConfigKey.AWS_REGION

/**
 * Class used to source configuration from Java Environment Variables. This allows us to interpolate
 * variables required at runtime without the need for a static application.properties file. Thus,
 * this means we can pass secrets to the API and not commit them to the code base.
 */
@Component
class ConfigurationResolver {
    @Autowired
    private lateinit var env: Environment

    private val stringConfigs: MutableMap<ConfigKey, String> = mutableMapOf()
    private val listConfigs: MutableMap<ConfigKey, List<String>> = mutableMapOf()
    val awsRegion: Region = kotlin.runCatching { Region.of(getStringConfig(AWS_REGION)) }.getOrDefault(Region.EU_WEST_2)

    fun getStringConfig(configKey: ConfigKey): String {
        return stringConfigs.computeIfAbsent(configKey) {
            env.getProperty(configKey.key) ?: throw SystemArgumentException("No value found for ${configKey.key}")
        }
    }

    final fun getListConfig(configKey: ConfigKey): List<String> {
        return listConfigs.computeIfAbsent(configKey) {
            val sysConfig = env.getProperty(configKey.key) ?: throw SystemArgumentException("No value found for ${configKey.key}")
            sysConfig.split(",").toList()
        }
    }

    fun getAllConfig(): Map<ConfigKey, Any> {
        ConfigKey.values().forEach {
            if(it.isList)
                getListConfig(it)
            else
                getStringConfig(it)
        }
        return stringConfigs.plus(listConfigs)
    }

    fun getIfEmpty(value: String, configKey: ConfigKey): String{
        return if(value != "") value else getStringConfig(configKey)
    }

    fun clear() {
        stringConfigs.clear()
        listConfigs.clear()
    }
}

enum class ConfigKey(val key: String, val isList: Boolean) {
    DEBUG("orchestrationService.debug", false),
    AWS_REGION("orchestrationService.aws_region", false),
    COGNITO_USER_POOL_ID("orchestrationService.cognito_user_pool_id", false),
    LOAD_BALANCER_NAME("orchestrationService.load_balancer_name", false),
    LOAD_BALANCER_PORT("orchestrationService.load_balancer_port", false),
    ECS_CLUSTER_NAME("orchestrationService.ecs_cluster_name", false),
    EMR_CLUSTER_HOSTNAME("orchestrationService.emr_cluster_hostname", false),
    USER_CONTAINER_URL("orchestrationService.user_container_url", false),
    USER_CONTAINER_PORT("orchestrationService.user_container_port", false),
    JUPYTER_S3_ARN("orchestrationService.jupyterhub_bucket_arn", false),
    USER_TASK_EXECUTION_ROLE_ARN("orchestrationService.user_task_execution_role_arn", false),
    USER_TASK_VPC_SECURITY_GROUPS("orchestrationService.user_task_security_groups", true),
    USER_TASK_VPC_SUBNETS("orchestrationService.user_task_subnets", true),
    ECR_ENDPOINT("orchestrationService.ecr_endpoint", false),
    AWS_ACCOUNT_NUMBER("orchestrationService.aws_account_number", false),
    CONTAINER_LOG_GROUP("orchestrationService.container_log_group", false),
    TAGS("TAGS", false)
}
