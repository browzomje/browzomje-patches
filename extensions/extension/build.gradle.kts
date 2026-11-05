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

// The extension is Java-only. Pinterest supplies the Kotlin runtime that its own
// models use, so merging a second runtime can collide with host DEX classes.
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
}

abstract class GeneratePinterestCatalog : DefaultTask() {
    @get:InputFile
    abstract val script: RegularFileProperty

    @get:InputDirectory
    abstract val catalog: DirectoryProperty

    @get:InputDirectory
    abstract val extensionSources: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:javax.inject.Inject
    abstract val exec: org.gradle.process.ExecOperations

    @TaskAction
    fun generate() {
        exec.exec {
            commandLine(
                if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
                script.get().asFile,
                "--output",
                outputDirectory.get().asFile,
            )
        }
    }
}

val generatePinterestCatalog by tasks.registering(GeneratePinterestCatalog::class) {
    script.set(rootProject.layout.projectDirectory.file("scripts/pinterest_l10n.py"))
    catalog.set(layout.projectDirectory.dir("src/main/l10n"))
    extensionSources.set(layout.projectDirectory.dir("src/main/java/app/template/extension/pinterest"))
    outputDirectory.set(layout.buildDirectory.dir("generated/pinterestCatalog"))
}

androidComponents.onVariants { variant ->
    variant.sources.java?.addGeneratedSourceDirectory(generatePinterestCatalog, GeneratePinterestCatalog::outputDirectory)
}
