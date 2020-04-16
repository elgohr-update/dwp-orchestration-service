package uk.gov.dwp.dataworks.services

import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse
import software.amazon.awssdk.services.ecs.model.Service

@RunWith(SpringRunner::class)
@WebMvcTest(ExistingUserServiceCheck::class)
class ExistingUserServiceCheckTest {
    @SpyBean
    private lateinit var existingUserServiceCheckForTest: ExistingUserServiceCheck

    @MockBean
    private lateinit var authService: AuthenticationService

    @MockBean
    private lateinit var configService: ConfigurationService

    @BeforeEach
    fun setup() {
        whenever(authService.validate(any())).thenReturn(mock<DecodedJWT>())
    }

    private val testUserName = "testUser"
    private fun createTestService(name: String, status: String): Service {
        return Service.builder().serviceName(name).status(status).build()
    }

    private fun createDescribeServiceResponse(name: String, state: String): DescribeServicesResponse {
        return DescribeServicesResponse.builder().services(listOf(createTestService(name, state))).build()
    }

    @Test
    fun `Existing Service prevent duplicates from spinning up`() {
        val fakeResponse = createDescribeServiceResponse("$testUserName-analytical-workspace", "ACTIVE")
        doReturn(fakeResponse).whenever(existingUserServiceCheckForTest).servicesResponse(anyString(), anyString())
        assertThat(existingUserServiceCheckForTest.check(testUserName, "test")).isTrue()
    }

    @Test
    fun `Existing inactive service allows creation`() {
        val fakeResponse = createDescribeServiceResponse("$testUserName-analytical-workspace", "INACTIVE")
        doReturn(fakeResponse).whenever(existingUserServiceCheckForTest).servicesResponse(anyString(), anyString())
        assertThat(existingUserServiceCheckForTest.check(testUserName, "test")).isFalse()
    }

    @Test
    fun `No matching service allows creation`() {
        val fakeResponse = DescribeServicesResponse.builder().build()
        doReturn(fakeResponse).whenever(existingUserServiceCheckForTest).servicesResponse(anyString(), anyString())
        assertThat(existingUserServiceCheckForTest.check(testUserName, "test")).isFalse()
    }
}
