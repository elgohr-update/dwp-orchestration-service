package uk.gov.dwp.dataworks

/** Used when create service request cannot be executed. */
class FailedToExecuteCreateServiceRequestException(message: String, throwable: Throwable) : Exception(message, throwable)

/** Used when run task request cannot be executed. */
class FailedToExecuteRunTaskRequestException(message: String, throwable: Throwable) : Exception(message, throwable)

/** Used when Expected System Argument cannot be found. */
class SystemArgumentException(message: String) : Exception(message)

class UpperRuleLimitReachedException(message: String) : Exception(message)

/** Used when multiple Load Balancers are unexpectedly matched in the AWS environment */
class MultipleLoadBalancersMatchedException(message: String): Exception(message)

/** Used when multiple Listeners are unexpectedly matched in the AWS environment */
class MultipleListenersMatchedException(message: String): Exception(message)
