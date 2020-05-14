package uk.gov.dwp.dataworks.aws

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.AwsIamPolicyJsonObject
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Component
class AwsParsing(){
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(AwsParsing::class.java))
    }
    private val mapper = ObjectMapper()
    /**
     * Helper method converts JSON IAM Policy to an instance of `AwsIamPolicyJsonObject` data class
     * and inserts extra values before serialising back to JSON string.
     */
    fun parsePolicyDocument(resource: Resource, sidAndAdditions: Map<String, List<String>>, statementKeyToUpdate: String): String {
        val obj = mapper.readValue(resource.inputStream.bufferedReader(), AwsIamPolicyJsonObject::class.java)
        obj.statement.filter { sidAndAdditions.containsKey(it.sid) }
                .forEach{ statement ->
                when(statementKeyToUpdate) {
                    "Resource" -> statement.resource.addAll(sidAndAdditions.getValue(statement.sid))
                    "Action" -> statement.action.addAll(sidAndAdditions.getValue(statement.sid))
                    else -> throw IllegalArgumentException("statementKeyToUpdate does not match expected values: \"Resource\" or \"Action\"")
            }
        }
        return mapper.writeValueAsString(obj)
    }
}
