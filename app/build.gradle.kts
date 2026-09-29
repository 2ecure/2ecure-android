/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.tasks.GenerateBuildConfig
import config.BuildTimeConfig
import extension.AssetCopyTask
import extension.GitBranchNameValueSource
import extension.GitRevisionValueSource
import extension.allEnterpriseImpl
import extension.allFeaturesImpl
import extension.allLibrariesImpl
import extension.allServicesImpl
import extension.buildConfigFieldStr
import extension.setupDependencyInjection
import extension.testCommonDependencies
import org.sonarqube.gradle.SonarResolverTask
import java.util.Locale

plugins {
    id("io.element.android-compose-application")
    id("kotlin-parcelize")
    alias(libs.plugins.licensee)
    alias(libs.plugins.kotlin.serialization)
    // To be able to update the firebase.xml files, uncomment and build the project
    // alias(libs.plugins.gms.google.services)
}

// 2ecure: every upload to Google Play needs a higher version code, but all our builds of one Element X release share
// upstream's. versionCode = upstream code * 100 + 2ecure build number (Gradle property twoecure.buildNumber, 0..99,
// set by the CI workflow), the same for the App Bundle and every APK: Play only receives the bundle. 26.09.2 build 1
// is 2_026_090_201, above every upstream-style code (202_609_02x) already installed; Play's maximum of 2_100_000_000
// is reached in the year 2100.
val twoecureBuildNumber = (findProperty("twoecure.buildNumber") as String?)?.toInt() ?: 0
require(twoecureBuildNumber in 0..99) { "twoecure.buildNumber must be in 0..99, is $twoecureBuildNumber" }
val twoecureVersionCode = Versions.VERSION_CODE * 100 + twoecureBuildNumber

android {
    namespace = "io.element.android.x"

    defaultConfig {
        applicationId = BuildTimeConfig.APPLICATION_ID
        targetSdk = Versions.TARGET_SDK
        versionCode = twoecureVersionCode
        versionName = Versions.VERSION_NAME

        // Keep abiFilter for the universalApk
        ndk {
            abiFilters += listOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64")
        }

        // Ref: https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split
        splits {
            // Configures multiple APKs based on ABI.
            abi {
                val buildingAppBundle = gradle.startParameter.taskNames.any { it.contains("bundle") }

                // Enables building multiple APKs per ABI. This should be disabled when building an AAB.
                isEnable = !buildingAppBundle

                // By default all ABIs are included, so use reset() and include to specify that we only
                // want APKs for armeabi-v7a, x86, arm64-v8a and x86_64.
                // Resets the list of ABIs that Gradle should create APKs for to none.
                reset()

                if (!buildingAppBundle) {
                    // Specifies a list of ABIs that Gradle should create APKs for.
                    include("armeabi-v7a", "x86", "arm64-v8a", "x86_64")
                    // Generate a universal APK that includes all ABIs, so user who installs from CI tool can use this one by default.
                    isUniversalApk = true
                }
            }
        }

        androidResources {
            localeFilters += listOf("en", "de", "fr", "it")
        }
    }

    signingConfigs {
        create("upload") {
            storeFile = providers.environmentVariable("TWOECURE_UPLOAD_STORE_FILE").orNull?.let { file(it) }
            storePassword = providers.environmentVariable("TWOECURE_UPLOAD_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("TWOECURE_UPLOAD_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("TWOECURE_UPLOAD_KEY_PASSWORD").orNull
        }
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeFile = file("./signature/debug.keystore")
            storePassword = "android"
        }
        register("nightly") {
            keyAlias = System.getenv("ELEMENT_ANDROID_NIGHTLY_KEYID")
                ?: project.property("signing.element.nightly.keyId") as? String?
            keyPassword = System.getenv("ELEMENT_ANDROID_NIGHTLY_KEYPASSWORD")
                ?: project.property("signing.element.nightly.keyPassword") as? String?
            storeFile = file("./signature/nightly.keystore")
            storePassword = System.getenv("ELEMENT_ANDROID_NIGHTLY_STOREPASSWORD")
                ?: project.property("signing.element.nightly.storePassword") as? String?
        }
    }

    val baseAppName = BuildTimeConfig.APPLICATION_NAME
    val buildType = if (isEnterpriseBuild) "Enterprise" else "FOSS"
    logger.warnInBox("Building ${defaultConfig.applicationId} ($baseAppName) [$buildType]")

    buildTypes {
        val oAuthRedirectSchemeBase = BuildTimeConfig.METADATA_HOST_REVERSED ?: "io.element.android"
        getByName("debug") {
            resValue("string", "app_name", "$baseAppName dbg")
            resValue(
                "string",
                "login_redirect_scheme",
                "$oAuthRedirectSchemeBase.debug",
            )
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }

        getByName("release") {
            resValue("string", "app_name", baseAppName)
            resValue(
                "string",
                "login_redirect_scheme",
                oAuthRedirectSchemeBase,
            )
            signingConfig = signingConfigs.getByName("upload")

            optimization {
                enable = true
                keepRules {
                    // Equivalent of adding `getDefaultProguardFile("proguard-android-optimize.txt")` (this is the default value).
                    includeDefault = true
                }
                // Our custom keep rules are registered as `keepRules` source folders in the `androidComponents` block below,
                // as the former `keepRules.files` DSL is deprecated since AGP 9.
            }
        }

        register("nightly") {
            val release = getByName("release")
            initWith(release)
            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-nightly"
            resValue("string", "app_name", "$baseAppName nightly")
            resValue(
                "string",
                "login_redirect_scheme",
                "$oAuthRedirectSchemeBase.nightly",
            )
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("nightly")


        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }
    flavorDimensions += "store"
    productFlavors {
        create("gplay") {
            dimension = "store"
            isDefault = true
            buildConfigFieldStr("SHORT_FLAVOR_DESCRIPTION", "G")
            buildConfigFieldStr("FLAVOR_DESCRIPTION", "GooglePlay")
        }
        create("fdroid") {
            dimension = "store"
            buildConfigFieldStr("SHORT_FLAVOR_DESCRIPTION", "F")
            buildConfigFieldStr("FLAVOR_DESCRIPTION", "FDroid")
        }
    }

    packaging {
        resources.pickFirsts += setOf(
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )

        jniLibs {
            useLegacyPackaging = project.findProperty("useLegacyPackaging")?.toString()?.toBoolean()
        }
    }
}

// Release tasks must never fall back to the public upstream debug key.
val validateTwoecureUploadSigning = tasks.register("validateTwoecureUploadSigning") {
    doLast {
        val required = listOf(
            "TWOECURE_UPLOAD_STORE_FILE",
            "TWOECURE_UPLOAD_STORE_PASSWORD",
            "TWOECURE_UPLOAD_KEY_ALIAS",
            "TWOECURE_UPLOAD_KEY_PASSWORD",
        )
        val missing = required.filter { System.getenv(it).isNullOrBlank() }
        check(missing.isEmpty()) { "Missing release signing environment variables: ${missing.joinToString()}" }
    }
}
tasks.matching {
    (it.name.startsWith("pre") && it.name.endsWith("ReleaseBuild")) ||
        (it.name.startsWith("validateSigning") && it.name.endsWith("Release"))
}.configureEach {
    dependsOn(validateTwoecureUploadSigning)
}

androidComponents {
    onVariants { variant ->
        // Register the R8 keep rules source folders for optimized build types (release, nightly).
        // Replaces the deprecated `optimization.keepRules.files` DSL (AGP 9+).
        if (variant.buildType != "debug") {
            variant.sources.keepRules?.let { keepRules ->
                // Common rules, always applied.
                keepRules.addStaticSourceDirectory("proguard/common")

                // Depending on whether the app flavor is enterprise or not we want to use different proguard rules.
                val flavorProguardDir = if (isEnterpriseBuild) {
                    // Custom rules for enterprise builds
                    "../enterprise/proguard"
                } else {
                    // Custom fules for FOSS builds
                    "proguard/foss"
                }

                if (File(projectDir, flavorProguardDir).exists()) {
                    keepRules.addStaticSourceDirectory(flavorProguardDir)
                } else {
                    logger.warn("Proguard folder ${File(projectDir, flavorProguardDir).absolutePath} does not exist")
                }
            }
        }

        // 2ecure: one version code for the bundle and every APK (see twoecureVersionCode), no ABI digit.
        variant.outputs.forEach { output ->
            output.versionCode.set(twoecureVersionCode)
        }
    }

    val reportingExtension: ReportingExtension = project.extensions.getByType(ReportingExtension::class.java)
    configureLicensesTasks(reportingExtension)
}

// Configure the SonarQube plugin to wait for the resource generation tasks to complete before running the analysis.
tasks.withType<SonarResolverTask>().configureEach {
    dependsOn("generateGplayDebugResValues", "generateGplayDebugAndroidTestResValues")
}

setupDependencyInjection()

dependencies {
    allLibrariesImpl()
    allServicesImpl()
    if (isEnterpriseBuild) {
        allEnterpriseImpl(project)
        implementation(projects.appicon.enterprise)
    } else {
        implementation(projects.features.enterprise.implFoss)
        implementation(projects.appicon.element)
    }
    allFeaturesImpl(project)
    implementation(projects.features.migration.api)
    implementation(projects.appnav)
    implementation(projects.appconfig)
    implementation(projects.libraries.uiStrings)
    implementation(projects.services.analytics.compose)

    if (ModulesConfig.pushProvidersConfig.includeFirebase) {
        "gplayImplementation"(projects.libraries.pushproviders.firebase)
    }
    if (ModulesConfig.pushProvidersConfig.includeUnifiedPush) {
        implementation(projects.libraries.pushproviders.unifiedpush)
    }

    // Google Play Services fused location backend. Discovered at runtime via ServiceLoader by the
    // location feature.
    // No fused-location backend in 2ecure v1.

    implementation(libs.appyx.core)
    implementation(libs.androidx.splash)
    implementation(libs.androidx.core)
    implementation(libs.androidx.corektx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.preference)
    implementation(libs.coil)

    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp.logging)
    implementation(libs.serialization.json)

    implementation(libs.matrix.emojibase.bindings)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.services.toolbox.test)
}

tasks.withType<GenerateBuildConfig>().configureEach {
    outputs.upToDateWhen { false }
    val gitRevision = providers.of(GitRevisionValueSource::class.java) {}.get()
    val gitBranchName = providers.of(GitBranchNameValueSource::class.java) {}.get()
    android.defaultConfig.buildConfigFieldStr("GIT_REVISION", gitRevision)
    android.defaultConfig.buildConfigFieldStr("GIT_BRANCH_NAME", gitBranchName)
}

licensee {
    allow("Apache-2.0")
    allow("MIT")
    allow("BSD-2-Clause")
    allow("BSD-3-Clause")
    allow("EPL-1.0")
    allowUrl("https://opensource.org/license/bsd-3-clause")
    allowUrl("https://opensource.org/license/bsd-2-clause")
    allowUrl("https://opensource.org/licenses/MIT")
    allowUrl("https://developer.android.com/studio/terms.html")
    allowUrl("https://www.zetetic.net/sqlcipher/license/")
    allowUrl("https://jsoup.org/license")
    allowUrl("https://asm.ow2.io/license.html")
    allowUrl("https://www.gnu.org/licenses/agpl-3.0.txt")
    allowUrl("https://github.com/mhssn95/compose-color-picker/blob/main/LICENSE")
    ignoreDependencies("com.github.matrix-org", "matrix-analytics-events")
    // Ignore dependency that are not third-party licenses to us.
    ignoreDependencies(groupId = "io.element.android")
}

fun Project.configureLicensesTasks(reportingExtension: ReportingExtension) {
    androidComponents {
        onVariants { variant ->
            val capitalizedVariantName = variant.name.replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(Locale.getDefault())
                } else {
                    it.toString()
                }
            }
            val artifactsFile = reportingExtension.baseDirectory.file("licensee/android$capitalizedVariantName/artifacts.json")

            val copyArtifactsTask =
                project.tasks.register<AssetCopyTask>("copy${capitalizedVariantName}LicenseeReportToAssets") {
                    inputFile.set(artifactsFile)
                    targetFileName.set("licensee-artifacts.json")
                }
            variant.sources.assets?.addGeneratedSourceDirectory(
                copyArtifactsTask,
                AssetCopyTask::outputDirectory,
            )
            copyArtifactsTask.dependsOn("licenseeAndroid$capitalizedVariantName")
        }
    }
}

configurations.all {
    resolutionStrategy {
        dependencySubstitution {
            val tink = libs.google.tink.get()
            substitute(module("com.google.crypto.tink:tink")).using(module("${tink.group}:${tink.name}:${tink.version}"))
        }
    }
}
