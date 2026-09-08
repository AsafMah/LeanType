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
            minSdk = 21
        }
    }

    androidComponents.onVariants { variant ->
        val patterns = mutableListOf<String>()
        val dictsDir = project.file("src/main/assets/dicts")
        if (dictsDir.exists() && dictsDir.isDirectory) {
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
}
