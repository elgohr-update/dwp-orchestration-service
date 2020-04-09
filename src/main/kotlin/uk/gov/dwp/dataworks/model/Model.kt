package uk.gov.dwp.dataworks.model

import com.fasterxml.jackson.annotation.JsonCreator

data class Model @JsonCreator constructor(
        val ecsClusterName: String,
        val userName: String,
        val emrClusterHostName: String,
        val albName: String,
        val containerPort: Int = 443,
        val jupyterCpu: Int = 512,
        val jupyterMemory: Int = 512
)
