package uk.gov.dwp.dataworks.controllers

import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.exceptions.JWTVerificationException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpClientErrorException
import uk.gov.dwp.dataworks.CleanupRequest
import uk.gov.dwp.dataworks.DeployRequest
import uk.gov.dwp.dataworks.ForbiddenException
import uk.gov.dwp.dataworks.logging.DataworksLogger
import uk.gov.dwp.dataworks.services.ActiveUserTasks
import uk.gov.dwp.dataworks.services.AuthenticationService
import uk.gov.dwp.dataworks.services.ConfigKey
import uk.gov.dwp.dataworks.services.ConfigurationResolver
import uk.gov.dwp.dataworks.services.TaskDeploymentService
import uk.gov.dwp.dataworks.services.TaskDestroyService
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
@CrossOrigin
class ConnectionController {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(ConnectionController::class.java))
    }

    @Autowired
    private lateinit var authService: AuthenticationService
    @Autowired
    private lateinit var taskDeploymentService: TaskDeploymentService
    @Autowired
    private lateinit var taskDestroyService: TaskDestroyService
    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver
    @Autowired
    private lateinit var activeUserTasks: ActiveUserTasks

    @Operation(summary = "Connect to Analytical Environment",
            description = "Provisions the Analytical Environment for a user and returns the required information to connect")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "400", description = "Failure, bad request")
    ])
    @PostMapping("/connect", produces = ["text/plain"])
    @ResponseStatus(HttpStatus.OK)
    fun connect(@RequestHeader("Authorisation") token: String, @RequestBody requestBody: DeployRequest): String {
        val jwtObject = authService.validate(token)
        return handleConnectionRequest(jwtObject.userName, jwtObject.cognitoGroup, requestBody)
    }

    @Operation(summary = "Requests the user containers",
            description = "Provisions the user containers for remote desktops")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "400", description = "Failure, bad request")
    ])
    @PostMapping("/debug/deploy")
    fun launchTask(@RequestHeader("Authorisation") userName: String, @RequestHeader("Authorisation") cognitoGroups: List<String>, @RequestBody requestBody: DeployRequest): String {
        if (configurationResolver.getStringConfig(ConfigKey.DEBUG) != "true" )
            throw ForbiddenException("Debug routes not enabled")
        return handleConnectionRequest(userName,cognitoGroups, requestBody)
    }

    @PostMapping("/debug/destroy")
    @ResponseStatus(HttpStatus.OK)
    fun destroyTask(@RequestHeader("Authorisation") userName: String, @RequestBody requestBody: DeployRequest) {
        if (configurationResolver.getStringConfig(ConfigKey.DEBUG) != "true" )
            throw ForbiddenException("Debug routes not enabled")
        taskDestroyService.destroyServices(userName)
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
        val jwtObject = authService.validate(token)
        taskDestroyService.destroyServices(jwtObject.userName)
    }

    @PostMapping("/cleanup")
    @ResponseStatus(HttpStatus.OK)
    fun cleanup(@RequestBody request: CleanupRequest) {
        taskDestroyService.cleanupDestroy(request.activeUsers)
    }

    @ExceptionHandler(JWTVerificationException::class, SigningKeyNotFoundException::class)
    fun handleInvalidToken(res: HttpServletResponse, ex: Exception) {
        logger.warn("Failed to verify JWT token $ex")
        res.setHeader("Content-Type", "text/plain");
        res.sendError(HttpStatus.UNAUTHORIZED.value(), "Failed to verify JWT token")
    }

    fun handleConnectionRequest(userName: String, cognitoGroups: List<String>, requestBody: DeployRequest):String {
        if (activeUserTasks.contains(userName)) {
            logger.info("Redirecting user to running containers, as they exist")
        } else {
            taskDeploymentService.runContainers(
                    userName,
                    cognitoGroups,
                    requestBody.jupyterCpu,
                    requestBody.jupyterMemory,
                    requestBody.additionalPermissions)
        }
        return "${configurationResolver.getStringConfig(ConfigKey.USER_CONTAINER_URL)}/${userName}/"
    }
}
