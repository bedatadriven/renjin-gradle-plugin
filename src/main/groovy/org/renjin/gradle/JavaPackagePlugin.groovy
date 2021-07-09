package org.renjin.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy

class JavaPackagePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(JavaPlugin)


        def extension = project.extensions.create('renjin', RenjinExtension)
        if(System.getenv("RENJIN_RELEASE") != null) {
            // This environment variable is set during the Renjin Release process
            extension.renjinVersion.convention(System.getenv("RENJIN_RELEASE"))
        } else if(project.hasProperty("renjinVersion")) {
            extension.renjinVersion.convention(project.property('renjinVersion'))
        }

        def renjinPackager = project.configurations.create('renjinPackager')

        renjinPackager.incoming.beforeResolve {
            def renjinVersion = extension.resolveRenjinVersion()
            project.dependencies.add(renjinPackager.name, "org.renjin:renjin-packager:${renjinVersion}")
        }

        def copyPackageResourcesTask = project.tasks.register ('copyPackageResources', Copy)
        copyPackageResourcesTask.configure {
            from (project.projectDir) {
                include 'DESCRIPTION'
                include 'NAMESPACE'
            }
            into("${project.buildDir}/resources/${project.group.replace('.', '/')}/${project.name}")
        }

        def compileNamespaceTask = project.tasks.register('compileNamespace', CompileNamespaceTask, project)
        compileNamespaceTask.configure {
            sourceDirectory.set(project.file("src/main/R"))
            compileClasspath.from(project.getTasks().getByName('compileJava').destinationDir)
            compileClasspath.from(project.configurations.compile)
            dependsOn 'compileJava'
        }

        project.sourceSets {
            main {
                output.dir("${project.buildDir}/inst", builtBy: 'copyPackageResources')
                output.dir("${project.buildDir}/namespace", builtBy: 'compileNamespace')
            }
        }

        def testTask = project.tasks.register('testNamespace', TestNamespaceTask, project)
        testTask.configure {
            testsDirectory = project.file("src/test/R")
            runtimeClasspath.from(project.sourceSets.main.output)
            runtimeClasspath.from(project.configurations.testRuntime)
        }

        project.tasks.named('test').configure {
            dependsOn testTask
        }
    }
}
