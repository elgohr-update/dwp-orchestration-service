package uk.gov.dwp.dataworks.aws

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.kms.KmsClient
import uk.gov.dwp.dataworks.logging.DataworksLogger
import uk.gov.dwp.dataworks.services.AuthenticationService
import uk.gov.dwp.dataworks.services.ConfigKey
import uk.gov.dwp.dataworks.services.ConfigurationResolver
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

@Component
class AwsHealthIndicator : HealthIndicator {

    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(AwsHealthIndicator::class.java))
    }

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    override fun health(): Health {
        val region = Region.of(configurationResolver.getStringConfig(ConfigKey.AWS_REGION))
        val endpoints = mapOf(
                "kms" to KmsClient.serviceMetadata().endpointFor(region),
                "elb" to ElasticLoadBalancingV2Client.serviceMetadata().endpointFor(region),
                "dynamodb" to DynamoDbClient.serviceMetadata().endpointFor(region),
                "ecs" to EcsClient.serviceMetadata().endpointFor(region),
                "iam" to IamClient.serviceMetadata().endpointFor(Region.AWS_GLOBAL) // IAM does not use regional endpoints
        )

        val endpointStatus = endpoints.map { (name, endpoint) -> name to doEndpointHealthCheck(endpoint) }.toMap()

        val health = if (endpointStatus.values.contains("DOWN")) Health.down() else Health.up()
        return health.withDetails(endpointStatus).build()


    }

    fun doEndpointHealthCheck(endpoint: URI): String {
        try {
            val upResponseCodes = listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_FORBIDDEN)
            val httpConn = URL("https://${endpoint}").openConnection() as HttpURLConnection
            httpConn.requestMethod = "GET"
            httpConn.connect()

            if (upResponseCodes.contains(httpConn.responseCode))
                return "UP"

            logger.error("Failed healthcheck to $endpoint",
                    "responseCode" to httpConn.responseCode.toString(),
                    "responseBody" to httpConn.inputStream.bufferedReader().use(BufferedReader::readText))
            return "DOWN"
        } catch (e: Exception) {
            logger.error("Error while performing healthcheck to $endpoint", e)
            return "DOWN"
        }
    }
}
