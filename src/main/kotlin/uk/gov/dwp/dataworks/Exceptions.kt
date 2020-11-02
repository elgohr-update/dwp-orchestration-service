package uk.gov.dwp.dataworks

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** Used when Expected System Argument cannot be found. */
class SystemArgumentException(message: String) : Exception(message)

class UpperRuleLimitReachedException(message: String) : Exception(message)

/** Used when multiple Load Balancers are unexpectedly matched in the AWS environment */
class MultipleLoadBalancersMatchedException(message: String): Exception(message)

/** Used when multiple Listeners are unexpectedly matched in the AWS environment */
class MultipleListenersMatchedException(message: String): Exception(message)

/** Used when multiple Target Groups are unexpectedly matched in the AWS environment */
class MultipleTargetGroupsMatchedException(message: String): Exception(message)

/** Used when Tasks for a given user cannot be found in [ActiveUserTasks] */
class UserHasNoTasksException(message: String): Exception(message)

/** Used when Tasks failed to be destroyed */
class TaskDestroyException(message: String, throwable: Throwable) : Exception(message, throwable)

/** Used when JWK Keystore data cannot be accessed */
class JWKKeystoreDataException(message: String): Exception(message)

/** Used when network configuration is missing for AWSVPC netowrking mode */
class NetworkConfigurationMissingException(message: String): Exception(message)

@ResponseStatus(HttpStatus.FORBIDDEN)
class ForbiddenException(message: String): Exception(message)

@ResponseStatus(HttpStatus.NOT_FOUND)
class AttributesNotFoundException(message: String): Exception(message)
