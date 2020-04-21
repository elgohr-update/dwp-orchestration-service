package uk.gov.dwp.dataworks.controllers

import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.exceptions.JWTVerificationException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import uk.gov.dwp.dataworks.controllers.ConnectionController.Companion.logger
import uk.gov.dwp.dataworks.logging.DataworksLogger
import uk.gov.dwp.dataworks.model.Model
import uk.gov.dwp.dataworks.services.ConfigurationService
import uk.gov.dwp.dataworks.services.TaskDeploymentService
import uk.gov.dwp.dataworks.services.ExistingUserServiceCheck
import uk.gov.dwp.dataworks.services.AuthenticationService
import uk.gov.dwp.dataworks.services.ConfigKey

@RestController
class ConnectionController {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(ConnectionController::class.java))
    }

    @Autowired
    private lateinit var authService: AuthenticationService
    @Autowired
    private lateinit var existingUserServiceCheck: ExistingUserServiceCheck
    @Autowired
    private lateinit var taskDeploymentService: TaskDeploymentService
    @Autowired
    private lateinit var configService: ConfigurationService

    @Operation(summary = "Connect to Analytical Environment",
            description = "Provisions the Analytical Environment for a user and returns the required information to connect")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "400", description = "Failure, bad request")
    ])
    @PostMapping("/connect")
    @ResponseStatus(HttpStatus.OK)
    fun connect(@RequestHeader("Authorisation") token: String, @RequestBody requestBody: uk.gov.dwp.dataworks.model.Model): String {
        val jwtObject = authService.validate(token)
        return handleRequest(jwtObject.userName, requestBody)
    }

    @Operation(summary = "Requests the user containers",
            description = "Provisions the user containers for remote desktops")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "400", description = "Failure, bad request")
    ])

    @PostMapping("/deployusercontainers")
    fun launchTask(@RequestHeader("Authorisation") userName: String, @RequestBody requestBody: Model): String {
        return handleRequest(userName, requestBody)
    }

    @Operation(summary = "Disconnect from Analytical Environment",
            description = "Performs clean-up tasks after a user has disconnected")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "401", description = "Failure, unauthorized")
    ])
    @PostMapping("/disconnect")
    @ResponseStatus(HttpStatus.OK)
    fun disconnect(@RequestHeader("Authorisation") token: String) {
        authService.validate(token)
    }

    @ExceptionHandler(JWTVerificationException::class, SigningKeyNotFoundException::class)
    @ResponseStatus(value = HttpStatus.UNAUTHORIZED, reason = "Invalid authentication token")
    fun handleInvalidToken() {
        // Do nothing - annotations handle response
    }

    fun handleRequest(userName: String, requestBody: Model):String {
        if (existingUserServiceCheck.check(userName, configService.getStringConfig(ConfigKey.ECS_CLUSTER_NAME))){
            ConnectionController.logger.info("Redirecting user to running containers, as they exist")
        } else {
            taskDeploymentService.taskDefinitionWithOverride(
                    userName,
                    requestBody.jupyterCpu,
                    requestBody.jupyterMemory,
                    requestBody.additionalPermissions
            )
            ConnectionController.logger.info("Submitted request", "cluster_name" to configService.getStringConfig(ConfigKey.ECS_CLUSTER_NAME), "user_name" to userName)
        }
        return "${configService.getStringConfig(ConfigKey.USER_CONTAINER_URL)}/${userName}"
    }
}
