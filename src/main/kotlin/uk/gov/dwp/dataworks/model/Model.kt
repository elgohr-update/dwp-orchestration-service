package uk.gov.dwp.dataworks.model

import com.fasterxml.jackson.annotation.JsonCreator

data class Model @JsonCreator constructor(
        val jupyterCpu: Int = 512,
        val jupyterMemory: Int = 512,
        val additionalPermissions: List<String> = emptyList()
)
