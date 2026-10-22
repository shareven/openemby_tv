// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 内置 Kotlin 编译，不再需要 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose) apply false
}