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
configurations.matching { it.name == "greaseProdRelease" }.configureEach {
    attributes {
        attribute(ProductFlavorAttr.of("contentType"), objs.named("prod"))
    }
}

dependencies {
   /* grease(projects.feature.interests)
    grease(projects.feature.foryou)
    grease(projects.feature.bookmarks)
    grease(projects.feature.topic)
    grease(projects.feature.search)
    grease(projects.feature.settings)*/

    grease(projects.core.common)
    grease(projects.core.ui)
    grease(projects.core.designsystem)
    grease(projects.core.data)
    grease(projects.core.domain)
    grease(projects.core.model)
    grease(projects.core.analytics)
    grease(projects.sync.work)
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
