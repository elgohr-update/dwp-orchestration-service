package uk.gov.dwp.dataworks.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.dwp.dataworks.model.Model
import uk.gov.dwp.dataworks.services.TaskDeploymentService

@RestController
class UserContainerController {
    @Autowired
    lateinit var taskDeploymentService: TaskDeploymentService

    @Operation(summary = "Requests the user containers",
            description = "Provisions the user containers for remote desktops")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "400", description = "Failure, bad request")
    ])
    @PostMapping("/deployusercontainers")
    fun launchTask(@RequestBody requestBody: Model){
        taskDeploymentService.taskDefinitionWithOverride(
                requestBody.ecsClusterName,
                requestBody.emrClusterHostName,
                requestBody.albName ,
                requestBody.userName,
                requestBody.containerPort,
                requestBody.jupyterCpu,
                requestBody.jupyterMemory
        )
    }
}
