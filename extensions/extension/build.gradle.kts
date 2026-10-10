extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.template.extension"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        // semantic-release updates this property before rebuilding the published bundle.
        buildConfigField("String", "PATCHES_VERSION", "\"${providers.gradleProperty("version").get()}\"")
    }
}
