plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

subprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = false
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
        afterSuite(
            KotlinClosure2<TestDescriptor, TestResult, Unit>({ descriptor, result ->
                if (descriptor.parent == null) {
                    val total = result.testCount
                    val passed = result.successfulTestCount
                    val failed = result.failedTestCount
                    val skipped = result.skippedTestCount
                    println("[$path] $total tests - $passed passed, $failed failed, $skipped skipped")
                }
            }),
        )
    }
}
