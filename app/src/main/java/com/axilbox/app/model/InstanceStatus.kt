package com.axilbox.app.model

enum class InstanceStatus(val label: String) {
    STOPPED("Stopped"),
    BOOTING("Booting"),
    RUNNING("Running"),
    PAUSED("Paused"),
    ERROR("Error")
}
