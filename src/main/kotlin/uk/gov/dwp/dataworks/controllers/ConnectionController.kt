package uk.gov.dwp.dataworks.controllers


import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.exceptions.JWTVerificationException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import uk.gov.dwp.dataworks.services.AuthenticationService


@RestController
class ConnectionController {

    @Autowired
    private lateinit var authService: AuthenticationService

    @Operation(summary = "Connect to Analytical Environment",
            description = "Provisions the Analytical Environment for a user and returns the required information to connect")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "400", description = "Failure, bad request")
    ])
    @PostMapping("/connect")
    @ResponseStatus(HttpStatus.OK)
    fun connect(@RequestHeader("Authorization") token: String) {
        authService.validate(token)
    }

    @Operation(summary = "Disconnect from Analytical Environment",
            description = "Performs clean-up tasks after a user has disconnected")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "401", description = "Failure, unauthorized")
    ])
    @PostMapping("/disconnect")
    @ResponseStatus(HttpStatus.OK)
    fun disconnect(@RequestHeader("Authorization") token: String) {
        authService.validate(token)
    }


    @ExceptionHandler(JWTVerificationException::class, SigningKeyNotFoundException::class)
    @ResponseStatus(value = HttpStatus.UNAUTHORIZED, reason = "Invalid authentication token")
    fun handleInvalidToken() {
        // Do nothing - annotations handle response
    }
}
