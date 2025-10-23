import com.android.build.api.attributes.ProductFlavorAttr

plugins {
    alias(libs.plugins.nowinandroid.android.feature)
    alias(libs.plugins.nowinandroid.android.library.compose)
    id("io.deepmedia.tools.grease") version "0.3.7"
    id("dikiySdk")
    `maven-publish`
}

android {
    namespace = "com.google.samples.apps.nowinandroid.feature.mylibrary"
}

val objs = project.objects

// Настройка для demoDebug
configurations.matching { it.name == "greaseDemoDebug" }.configureEach {
    attributes {
        attribute(ProductFlavorAttr.of("contentType"), objs.named("demo"))
    }
}

// Настройка для prodRelease


dependencies {
    grease(projects.feature.interests)
    grease(projects.feature.foryou)
    grease(projects.feature.bookmarks)
    grease(projects.feature.topic)
    grease(projects.feature.search)
    grease(projects.feature.settings)

    grease(projects.core.common)
    grease(projects.core.ui)
    grease(projects.core.designsystem)
    grease(projects.core.data)
    grease(projects.core.domain)
    grease(projects.core.model)
    grease(projects.core.analytics)
    grease(projects.sync.work)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.runtime.tracing)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.tracing.ktx)
    implementation(libs.androidx.window.core)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.coil.kt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.accompanist.permissions)

    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.testManifest)
    debugImplementation(projects.uiTestHiltManifest)

    kspTest(libs.hilt.compiler)

    testImplementation(projects.core.dataTest)
    testImplementation(projects.core.datastoreTest)
    testImplementation(libs.hilt.android.testing)
    testImplementation(projects.sync.syncTest)
    testImplementation(libs.kotlin.test)

    testDemoImplementation(libs.androidx.navigation.testing)
    testDemoImplementation(libs.robolectric)
    testDemoImplementation(libs.roborazzi)
    testDemoImplementation(projects.core.screenshotTesting)
    testDemoImplementation(projects.core.testing)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(projects.core.dataTest)
    androidTestImplementation(projects.core.datastoreTest)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotlin.test)
}

gradle.projectsEvaluated {
    publishing {
        publications {
            // Публикация demoDebug


            // Публикация prodRelease
            create<MavenPublication>("demoDebug") {
                groupId = "mem.com"
                artifactId = "core"
                version = "0.6.0"
                from(components["sdkDemoDebug"])
            }
        }

        repositories {


            // Оставляем mavenLocal для локального тестирования
            mavenLocal()
        }
    }
}
