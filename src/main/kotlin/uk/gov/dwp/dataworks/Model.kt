package uk.gov.dwp.dataworks

import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import kotlin.reflect.full.declaredMemberProperties


data class DeployRequest @JsonCreator constructor(
        val additionalPermissions: List<String> = emptyList(),
        val screenWidth: Int = 1920,
        val screenHeight: Int = 1080,
)

data class CleanupRequest(
        val activeUsers: List<String>
)

data class JWTObject(val decodedJWT: DecodedJWT, val username: String, val cognitoGroups: List<String>)


data class UserTask(val correlationId: String,
                    val userName: String,
                    val targetGroupArn: String?,
                    val albRoutingRuleArn: String?,
                    val ecsClusterName: String?,
                    val ecsServiceName: String?,
                    val iamRoleName: String?,
                    val iamPolicyUserArn: String?,
                    val iamPolicyTaskArn: String?) {
    companion object {
        fun from(map: Map<String, String>) = object {
            val correlationId: String by map
            val userName: String by map
            val targetGroupArn: String by map
            val albRoutingRuleArn: String by map
            val ecsClusterName: String by map
            val ecsServiceName: String by map
            val iamRoleName: String by map
            val iamPolicyUserArn: String by map
            val iamPolicyTaskArn: String by map
            val data = UserTask(correlationId, userName, targetGroupArn, albRoutingRuleArn, ecsClusterName,
                    ecsServiceName, iamRoleName, iamPolicyUserArn, iamPolicyTaskArn)
        }.data

        fun attributes(): List<AttributeDefinition> {
            return UserTask::class.declaredMemberProperties
                    .map { AttributeDefinition.builder().attributeName(it.name).attributeType(ScalarAttributeType.S).build() }
        }
    }
}

data class UserContainerProperties(
        val cognitoToken: String,
        val userName: String,
        val cognitoGroups: List<String>,
        val emrHostname: String,
        val guacamolePort: Int,
        val userS3Bucket: String,
        val kmsHome: String,
        val kmsShared: String,
        val gitRepo: String,
        val pushHost: String,
        val pushCron: String,
        val s3fsVolumeName: String,
        val packagesVolumeName: String,
        val githubProxyUrl: String,
        val githubUrl: String,
        val livyProxyUrl: String?,
        val screenWidth: Int,
        val screenHeight: Int,
)

data class TextSSHKeyPair(
    val private: String,
    val public: String
)

data class ContainerTab(
    val name: String,
    val url: String,
    val required: Boolean
)

@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy::class)
data class StatementObject(
        @JsonProperty("Sid") var sid: String,
        @JsonProperty("Effect") var effect: String,
        @JsonProperty("Action") var action: MutableList<String>,
        @JsonProperty("Resource") var resource: MutableList<String>
)

@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy::class)
data class AwsIamPolicyJsonObject(
        @JsonProperty("Version") var version: String,
        @JsonProperty("Statement") var statement: List<StatementObject>
)
