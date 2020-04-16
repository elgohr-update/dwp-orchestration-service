package uk.gov.dwp.dataworks.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.dwp.dataworks.model.Model
import uk.gov.dwp.dataworks.services.ExistingUserServiceCheck
import uk.gov.dwp.dataworks.services.TaskDeploymentService
import uk.gov.dwp.dataworks.services.TaskDeploymentService.Companion.logger

@RestController
class UserContainerController {
    @Autowired
    lateinit var taskDeploymentService: TaskDeploymentService
    @Autowired
    lateinit var existingUserServiceCheck: ExistingUserServiceCheck

    @Operation(summary = "Requests the user containers",
            description = "Provisions the user containers for remote desktops")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "400", description = "Failure, bad request")
    ])
    @PostMapping("/deployusercontainers")
    fun launchTask(@RequestBody requestBody: Model){
        if (existingUserServiceCheck.check(requestBody.userName, requestBody.ecsClusterName)){
            logger.info("Redirecting user to running containers, as they exist")
            return
        }
        taskDeploymentService.taskDefinitionWithOverride(
                requestBody.ecsClusterName,
                requestBody.emrClusterHostName,
                requestBody.albName ,
                requestBody.userName,
                requestBody.containerPort,
                requestBody.jupyterCpu,
                requestBody.jupyterMemory,
                requestBody.additionalPermissions
        )
    }
}
