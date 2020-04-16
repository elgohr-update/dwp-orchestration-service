package uk.gov.dwp.dataworks.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse

@Service
class ExistingUserServiceCheck{
    @Autowired
    private lateinit var configService : ConfigurationService

    fun check(userName: String, ecsClusterName: String):Boolean{
        val listOfService = servicesResponse(ecsClusterName, userName).services()
        return listOfService.map { it.status() }.any { it == "ACTIVE" }
    }

    fun servicesResponse(ecsClusterName: String, userName: String): DescribeServicesResponse {
        val ecsClient = EcsClient.builder().region(configService.awsRegion).build()
        return ecsClient.describeServices(DescribeServicesRequest.builder().cluster(ecsClusterName).services("${userName}-analytical-workspace").build())
    }
}
