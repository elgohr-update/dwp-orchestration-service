package uk.gov.dwp.dataworks

/** Used when create service request cannot be executed. */
class FailedToExecuteCreateServiceRequestException(message: String, throwable: Throwable) : Exception(message, throwable)

/** Used when run task request cannot be executed. */
class FailedToExecuteRunTaskRequestException(message: String, throwable: Throwable) : Exception(message, throwable)

/** Used when Expected System Argument cannot be found. */
class SystemArgumentException(message: String) : Exception(message)

class UpperRuleLimitReachedException(message: String) : Exception(message)
