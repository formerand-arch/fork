/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

private val DefaultElementsConfigurations = listOf("api", "runtime", "sources")

abstract class SdkPlugin @Inject constructor(
    private val componentFactory: SoftwareComponentFactory
) : Plugin<Project> {

    override fun apply(target: Project) {
        // Применяем плагин только после того как будет применен плагин grease
        target.plugins.withId("io.deepmedia.tools.grease") {
            val libraryExtension = target.extensions.getByType(LibraryExtension::class.java)
            // Производить настройку плагина нужно после того как все проекты были настроены, но
            // до того как перехода в фазу выполнения (Execution phase)
            // Если мы начнем настройку раньше, то зависимости некоторых проектов еще не будут
            // определены
            // Если мы начнем позже, уже в фазе выполнения, то мы не сможем настроить конфигурации,
            // их можно настраивать только в фазе конфигурации (Configuration phase)
            target.gradle.projectsEvaluated {
                // Настраиваем конфигурации для каждого отдельного варианта сборки
                for (variant in libraryExtension.libraryVariants) {
                    setupVariant(target, variant)
                    setupTasks(target, variant)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setupVariant(target: Project, variant: LibraryVariant) {
        // Список проектов необходим для конфигурации grease
        // Список модулей необходим для настройки публикации
        val projects = mutableSetOf<Project>()
        val moduleDependencies = mutableSetOf<ModuleDependency>()
        // Формируем список всех зависимостей данного модуля, включая зависимости дочерних проектов
        for (dependency in target.allDependencies(variant)) {
            when (dependency) {
                is ProjectDependency -> {
                    projects.add(dependency.dependencyProject)
                }
                is ModuleDependency -> {
                    moduleDependencies.add(dependency)
                }
            }
        }

        // Добавляем все проекты в конфигурацию grease, для данного модуля и варианта сборки
        for (project in projects) {
            target.dependencies.add(camelCase("grease", variant.name), project)
        }

        // Составляем список названий конфигураций для публикации для даненого проекта и варианта
        // сборки
        // В ходе работы agp формирует 3 такие конфигурации:
        // - {buildVariant}ApiElements-published - для сборки
        // - {buildVariant}RuntimeElements-published - для рантайма
        // - {buildVariant}SourceElements-published - для исходников
        val publishConfigurationNames = DefaultElementsConfigurations
            .map { configurationName ->
                camelCase(variant.name, configurationName, "elements-published")
            }

        // Находим все конфигурации указанные выше в текущем проекте
        val publishConfigurations = target.configurations
            .filter { it.name in publishConfigurationNames }

        // Начинаем формировать список новых конфигураций для публикации
        val newPublishConfigurations = mutableSetOf<Configuration>()
        for (configuration in publishConfigurations) {
            // Создаем новую конфигурацию с тем же названием, но с приставкой 'sdk'
            val newConfiguration = target.configurations
                .create(camelCase("sdk", configuration.name))
            newPublishConfigurations.add(newConfiguration)

            // Копируем атрибуты из оригинальной конфигурации в новую
            for (key in configuration.attributes.keySet()) {
                @Suppress("UNCHECKED_CAST")
                key as Attribute<Any?>
                val value = configuration.attributes.getAttribute(key)
                if (value != null) {
                    newConfiguration.attributes.attribute(key, value)
                }
            }

            // Добавляем артефакты из оригинальной конфигурации
            newConfiguration.artifacts.addAll(configuration.artifacts)

            // Добавляем все дочерние зависимости в новую конфигурацию
            for (dependency in moduleDependencies) {
                newConfiguration.dependencies.add(dependency)
            }
        }

        // Создаем новый компонент для публикации, и добавляем в него наши новые конфигурации
        // Таким образом, при публикации будет сгенерированы колрректные методанные
        val sdkComponent = componentFactory.adhoc(camelCase("sdk", variant.name))
        println("bbff #new ${sdkComponent.name}")
        for (configuration in newPublishConfigurations) {
            sdkComponent.addVariantsFromConfiguration(configuration) {}
        }
        target.components.add(sdkComponent)
    }

    private fun setupTasks(target: Project, variant: LibraryVariant) {
        target.tasks.register<SdkDependenciesTask>(camelCase("sdkDependencies", variant.name)) {
            group = "sdk"
            description = "Generate sdk`s dependencies for '${variant.name}' build variant"
            buildVariant = variant.name
        }
    }
}

// Функция для получения общего списка всех зависимостей данного и всех дочерних проектов,
// определенных в указанных конфигурациях
fun Project.allDependencies(configurationNames: Set<String>): Set<Dependency> {
    val dependencies = mutableSetOf<Dependency>()
    val visited = mutableSetOf<Project>()
    // Рекурсивно проходимся по всем зависимостям проектов, начиная с текущего
    val queue = ArrayDeque<Project>()
    queue.add(this)
    while (queue.isNotEmpty()) {
        val project = queue.removeFirst()
        val filteredConfigurations = project.configurations
            .filter { it.name in configurationNames }
        for (configuration in filteredConfigurations) {
            for (dependency in configuration.dependencies) {
                if (dependency is ProjectDependency) {
                    if (visited.add(dependency.dependencyProject)) {
                        queue.add(dependency.dependencyProject)
                    }
                }
                dependencies.add(dependency)
            }
        }
    }
    return dependencies
}

private val DefaultConfigurationNames = listOf("api", "implementation")

@Suppress("DEPRECATION")
// Функция для получения общего списка всех зависимостей данного и всех дочерних проектов,
// для выбранного варианта сборки
fun Project.allDependencies(variant: LibraryVariant): Set<Dependency> {
    // Cтандартные конфигурации api и implementation
    val configurationNames = DefaultConfigurationNames.toMutableSet()

    // Добавляем конфигурации для выбранного типа сборки, например для debug:
    // - debugApi
    // - debugImplementation
    val buildType = variant.buildType.name
    for (name in DefaultConfigurationNames) {
        configurationNames.add(camelCase(buildType, name))
    }

    // Добавляем конфигурации для выбранного flavor, например для demo и типа сборки debug:
    // - demoApi
    // - demoImplementation
    // - demoDebugApi
    // - demoDebugImplementation
    val flavorName = variant.flavorName
    if (flavorName != null) {
        for (name in DefaultConfigurationNames) {
            configurationNames.add(camelCase(flavorName, name))
        }

        for (name in DefaultConfigurationNames) {
            configurationNames.add(camelCase(flavorName, buildType, name))
        }
    }

    return allDependencies(configurationNames)
}

private fun camelCase(vararg names: String): String = buildString {
    if (names.isEmpty()) return@buildString
    append(names[0].replaceFirstChar { it.lowercaseChar() })
    for (i in 1 until names.size) {
        append(names[i].replaceFirstChar { it.uppercaseChar() })
    }
}

abstract class SdkDependenciesTask : DefaultTask() {

    @get:Internal
    internal var buildVariant: String = ""

    @TaskAction
    fun action() {
        val logger = project.logger
        val libraryExtension = project.extensions.getByType(LibraryExtension::class.java)
        val variant = libraryExtension.libraryVariants.find { it.name == buildVariant }
            ?: throw IllegalArgumentException("Library variant '$buildVariant' was not found")

        val projectDependencies = sortedSetOf<String>()
        val moduleDependencies = sortedSetOf<String>()
        for (dependency in project.allDependencies(variant)) {
            when (dependency) {
                is ProjectDependency -> {
                    projectDependencies.add(
                        dependency.dependencyProject.path
                    )
                }
                is ModuleDependency -> {
                    moduleDependencies.add(
                        "${dependency.group}:${dependency.name}:${dependency.version}"
                    )
                }
            }
        }

        logger.quiet("Complete projects list:")
        for (dependency in projectDependencies) {
            logger.quiet(dependency)
        }

        logger.quiet("Complete modules list:")
        for (dependency in moduleDependencies) {
            logger.quiet(dependency)
        }
    }
}
