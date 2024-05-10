pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        //maven(url = "https://github.com/ToanMobile/SDKAds/raw/main/sdk_ads/libs")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://github.com/ToanMobile/SDKAds/raw/main/sdk_ads/libs")
        maven(url = "https://github.com/thainguyen2303/base-library/row/main/score/libs")
    }
}

rootProject.name = "SDK_Ads"
include(":app")
include(":sdk_ads")
