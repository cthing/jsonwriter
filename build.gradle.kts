import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import org.cthing.projectversion.BuildType
import org.cthing.projectversion.ProjectVersion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

repositories {
    mavenCentral()
}

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    `java-library`
    checkstyle
    jacoco
    `maven-publish`
    signing
    alias(libs.plugins.cthingPublishing)
    alias(libs.plugins.cthingVersioning)
    alias(libs.plugins.dependencyAnalysis)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.versions)
}

version = ProjectVersion("2.0.1", BuildType.snapshot)
group = "org.cthing"
description = "A library that writes JSON with or without pretty printing."

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    }
}

dependencies {
    api(libs.jspecify)

    implementation(libs.escapers)

    compileOnly(libs.cthingAnnots)

    testImplementation(libs.jacksonCore)
    testImplementation(libs.jacksonDatabind)
    testImplementation(libs.junitApi)
    testImplementation(libs.junitParams)
    testImplementation(libs.assertJ)

    testCompileOnly(libs.apiGuardian)

    testRuntimeOnly(libs.junitEngine)
    testRuntimeOnly(libs.junitLauncher)

    spotbugsPlugins(libs.spotbugsContrib)
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    isIgnoreFailures = false
    configFile = file("dev/checkstyle/checkstyle.xml")
    configDirectory = file("dev/checkstyle")
    isShowViolations = true
}

spotbugs {
    toolVersion = libs.versions.spotbugs
    ignoreFailures = false
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    excludeFilter = file("dev/spotbugs/suppressions.xml")
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

dependencyAnalysis {
    issues {
        all {
            onAny {
                severity("fail")
            }
        }
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks {
    withType<JavaCompile> {
        options.release = libs.versions.java.get().toInt()
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-options", "-Werror"))
    }

    withType<Jar> {
        manifest.attributes(mapOf("Implementation-Title" to project.name,
                                  "Implementation-Vendor" to "C Thing Software",
                                  "Implementation-Version" to project.version))
    }

    withType<Javadoc> {
        val year = SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())
        with(options as StandardJavadocDocletOptions) {
            breakIterator(false)
            encoding("UTF-8")
            bottom("Copyright &copy; $year C Thing Software")
            addStringOption("Werror", "-quiet")
            memberLevel = JavadocMemberLevel.PUBLIC
            outputLevel = JavadocOutputLevel.QUIET
        }
    }

    check {
        dependsOn(buildHealth)
    }

    spotbugsMain {
        reports.create("html").required = true
    }

    spotbugsTest {
        isEnabled = false
    }

    withType<JacocoReport> {
        dependsOn("test")
        with(reports) {
            xml.required = false
            csv.required = false
            html.required = true
            html.outputLocation = layout.buildDirectory.dir("reports/jacoco")
        }
    }

    withType<Test> {
        useJUnitPlatform()
    }

    withType<GenerateModuleMetadata> {
        enabled = false
    }

    dependencyUpdates {
        revision = "release"
        gradleReleaseChannel = "current"
        outputFormatter = "plain,xml,html"
        outputDir = layout.buildDirectory.dir("reports/dependencyUpdates").get().asFile.absolutePath

        rejectVersionIf {
            isNonStable(candidate.version)
        }
    }
}

val sourceJar by tasks.registering(Jar::class) {
    from(project.sourceSets["main"].allSource)
    archiveClassifier = "sources"
}

val javadocJar by tasks.registering(Jar::class) {
    from(tasks.getByName("javadoc"))
    archiveClassifier = "javadoc"
}

publishing {
    publications {
        register("jar", MavenPublication::class) {
            from(components["java"])

            artifact(sourceJar)
            artifact(javadocJar)

            pom(cthingPublishing.createPomAction())
        }
    }

    val repoUrl = cthingRepo.repoUrl
    if (repoUrl != null) {
        repositories {
            maven {
                name = "CThingMaven"
                setUrl(repoUrl)
                credentials {
                    username = cthingRepo.user
                    password = cthingRepo.password
                }
            }
        }
    }
}

if (cthingPublishing.canSign()) {
    signing {
        sign(publishing.publications["jar"])
    }
}
