package uk.gov.dwp.dataworks.aws

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.iam.IamClient
import uk.gov.dwp.dataworks.services.ConfigurationResolver
import javax.annotation.PostConstruct

/**
 * Class to encapsulate and make available all of the AWS Clients required by the Orchestration Service.
 */
@Configuration
class AwsClients {
    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    lateinit var albClient: ElasticLoadBalancingV2Client
    lateinit var ecsClient: EcsClient
    lateinit var iamClient: IamClient
    lateinit var dynamoDbClient: DynamoDbClient

    @PostConstruct
    fun initialiseClients() {
        albClient = ElasticLoadBalancingV2Client.builder().region(configurationResolver.awsRegion).build()
        ecsClient = EcsClient.builder().region(configurationResolver.awsRegion).build()
        iamClient = IamClient.builder().region(Region.AWS_GLOBAL).build()
        dynamoDbClient = DynamoDbClient.builder().region(configurationResolver.awsRegion).build()
    }
}