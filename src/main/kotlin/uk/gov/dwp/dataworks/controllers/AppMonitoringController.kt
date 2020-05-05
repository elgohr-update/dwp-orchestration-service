package uk.gov.dwp.dataworks.controllers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.dwp.dataworks.services.ConfigurationResolver

@RestController
@CrossOrigin
class AppMonitoringController {
    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    @GetMapping("/app/environment")
    @ResponseStatus(HttpStatus.OK)
    fun listEnvironmentVars(): String {
        return configurationResolver.getAllConfig().map { "\"${it.key}\":\"${it.value}\"" }.joinToString(separator = ",", prefix = "{", postfix = "}")
    }
}
