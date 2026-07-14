pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Tokn"
include(":app")
include(":core:domain")
include(":core:data")
include(":core:security")
include(":core:audit")
include(":core:import")
include(":core:backup")
include(":core:ui")
include(":feature:home")
include(":feature:add")
include(":feature:settings")
include(":feature:backup")
include(":feature:onboarding")
include(":feature:sync")
include(":feature:passwordreminder")
include(":feature:rating")
