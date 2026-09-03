android {
    defaultConfig {
        applicationId = "com.asafmah.leantypedual"
        minSdk = 21
        versionCode = 4300
        versionName = "0.3.0"
    }

    productFlavors {
        create("standard") {
            dimension = "privacy"
            minSdk = 23
        }
        create("standardfull") {
            dimension = "privacy"
            minSdk = 23
        }
        create("offline") {
            dimension = "privacy"
            applicationIdSuffix = ".offline"
            minSdk = 26
        }
        create("offlinelite") {
            dimension = "privacy"
            applicationIdSuffix = ".offlinelite"
        }
    }

    androidComponents.onVariants { variant ->
        val patterns = mutableListOf<String>()
        if (variant.flavorName == "standard" || variant.flavorName == "standardfull") {
            val dictsDir = project.file("src/main/assets/dicts")
            dictsDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".dict")) {
                    patterns.add(file.name)
                }
            }
        }
        if (patterns.isNotEmpty()) {
            variant.androidResources.ignoreAssetsPatterns = patterns
        }
    }
}

dependencies {
    "offlineImplementation"("io.github.ljcamargo:llamacpp-kotlin:0.4.0")
}
