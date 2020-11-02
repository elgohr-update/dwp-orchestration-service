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
        val jwtObject= jwtParsingService.parseToken(jwt)
        return checkForGroupKms(jwtObject.cognitoGroups) && awsCommunicator.checkForExistingEnabledKey("${jwtObject.username}-home")
    }

    fun checkForGroupKms(cognitoGroups: List<String>): Boolean {
        if (cognitoGroups.size > 0) return awsCommunicator.checkForExistingEnabledKey("${cognitoGroups.first()}-shared")
            return false
    }
}
