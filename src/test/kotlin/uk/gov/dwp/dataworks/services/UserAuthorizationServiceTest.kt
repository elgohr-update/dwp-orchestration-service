package uk.gov.dwp.dataworks.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.services.rdsdata.model.ExecuteStatementResponse
import software.amazon.awssdk.services.rdsdata.model.Field
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import java.time.LocalDateTime

import software.amazon.awssdk.services.rdsdata.model.BadRequestException

@RunWith(SpringRunner::class)
class UserAuthorizationServiceTest {
    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun configurationResolver(): ConfigurationResolver {
            return ConfigurationResolver()
        }
    }

    @InjectMocks
    private lateinit var userAuthorizationService: UserAuthorizationService

    @Mock
    private lateinit var configurationResolver: ConfigurationResolver

    @Mock
    private lateinit var awsCommunicator: AwsCommunicator

    @Test
    fun `returns true when user has permission entry with allow effect and not expired`() {
        val record = mutableListOf<Field>(
            Field.builder().stringValue(Effect.ALLOW.action).build(),
            Field.builder().stringValue(LocalDateTime.now().plusDays(1).format(mySQLTimestampFormatter)).build()
        )
        val rdsDataResponse = ExecuteStatementResponse.builder()
            .records(mutableListOf(record))
            .build()
        whenever(awsCommunicator.rdsExecuteStatement(any(), anyOrNull())).doReturn(rdsDataResponse)
        assertTrue(userAuthorizationService.hasUserToolingPermission("testuser", ToolingPermission.FILE_TRANSFER_DOWNLOAD))
    }

    @Test
    fun `returns false when user has permission entry with allow effect and expired`() {
        val record = mutableListOf<Field>(
            Field.builder().stringValue(Effect.ALLOW.action).build(),
            Field.builder().stringValue(LocalDateTime.now().minusDays(1).format(mySQLTimestampFormatter)).build()
        )
        val rdsDataResponse = ExecuteStatementResponse.builder()
            .records(mutableListOf(record))
            .build()
        whenever(awsCommunicator.rdsExecuteStatement(any(), anyOrNull())).doReturn(rdsDataResponse)
        assertFalse(userAuthorizationService.hasUserToolingPermission("testuser", ToolingPermission.FILE_TRANSFER_DOWNLOAD))
    }

    @Test
    fun `returns false when user has permission entry with deny effect and expired`() {
        val record = mutableListOf<Field>(
            Field.builder().stringValue(Effect.DENY.action).build(),
            Field.builder().stringValue(LocalDateTime.now().minusDays(1).format(mySQLTimestampFormatter)).build()
        )
        val rdsDataResponse = ExecuteStatementResponse.builder()
            .records(mutableListOf(record))
            .build()
        whenever(awsCommunicator.rdsExecuteStatement(any(), anyOrNull())).doReturn(rdsDataResponse)
        assertFalse(userAuthorizationService.hasUserToolingPermission("testuser", ToolingPermission.FILE_TRANSFER_DOWNLOAD))
    }

    @Test
    fun `returns false when user has permission entry with deny effect and not expired`() {
        val record = mutableListOf<Field>(
            Field.builder().stringValue(Effect.DENY.action).build(),
            Field.builder().stringValue(LocalDateTime.now().plusDays(1).format(mySQLTimestampFormatter)).build()
        )
        val rdsDataResponse = ExecuteStatementResponse.builder()
            .records(mutableListOf(record))
            .build()
        whenever(awsCommunicator.rdsExecuteStatement(any(), anyOrNull())).doReturn(rdsDataResponse)
        assertFalse(userAuthorizationService.hasUserToolingPermission("testuser", ToolingPermission.FILE_TRANSFER_DOWNLOAD))
    }

    @Test
    fun `returns false when no permission record`() {
        val rdsDataResponse = ExecuteStatementResponse.builder().build()
        whenever(awsCommunicator.rdsExecuteStatement(any(), anyOrNull())).doReturn(rdsDataResponse)
        assertFalse(userAuthorizationService.hasUserToolingPermission("testuser", ToolingPermission.FILE_TRANSFER_DOWNLOAD))
    }

    @Test
    fun `return true when permission is overridden`() {
        whenever(configurationResolver.getListConfigOrDefault(eq(ConfigKey.TOOLING_PERMISSION_OVERRIDES), any())).doReturn(
            listOf(ToolingPermission.FILE_TRANSFER_DOWNLOAD.permissionName, ToolingPermission.CLIPBOARD_OUT.permissionName))

        val rdsDataResponse = ExecuteStatementResponse.builder().build()
        whenever(awsCommunicator.rdsExecuteStatement(any(), anyOrNull())).doReturn(rdsDataResponse)
        assertTrue(userAuthorizationService.hasUserToolingPermission("testuser", ToolingPermission.FILE_TRANSFER_DOWNLOAD))

    }

    @Test
    fun `returns false when ExecuteStatement throws`() {
        whenever(awsCommunicator.rdsExecuteStatement(any(), anyOrNull())).doThrow(BadRequestException.builder().build())
        assertFalse(userAuthorizationService.hasUserToolingPermission("testuser", ToolingPermission.FILE_TRANSFER_DOWNLOAD))
    }

    @Test
    fun `returns false when no validUntil returned`() {
        val record = mutableListOf<Field>(
            Field.builder().stringValue(Effect.DENY.action).build(),
            Field.builder().isNull(true).stringValue(null).build()
        )
        val rdsDataResponse = ExecuteStatementResponse.builder()
            .records(mutableListOf(record))
            .build()

        whenever(awsCommunicator.rdsExecuteStatement(any(), anyOrNull())).doReturn(rdsDataResponse)
        assertFalse(userAuthorizationService.hasUserToolingPermission("testuser", ToolingPermission.FILE_TRANSFER_DOWNLOAD))
    }

}
