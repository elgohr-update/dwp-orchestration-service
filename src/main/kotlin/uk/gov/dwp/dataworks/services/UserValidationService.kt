package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Service
class UserValidationService {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(UserValidationService::class.java))
    }

    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator
    @Autowired
    private lateinit var jwtParsingService: JwtParsingService

    fun checkJwtForAttributes(jwt: String): Boolean {
        try {
            val jwtObject = jwtParsingService.parseToken(jwt)
            return checkForGroupKms(jwtObject.cognitoGroups) && awsCommunicator.checkForExistingEnabledKey("${jwtObject.username}-home")
        } catch (e: IllegalArgumentException){
            logger.error("No cognito groups found in JWT token")
            return false
        }
    }

    fun checkForGroupKms(cognitoGroups: List<String>): Boolean {
        if (cognitoGroups.isNotEmpty()) return awsCommunicator.checkForExistingEnabledKey("${cognitoGroups.first()}-shared")
            return false
    }
}
