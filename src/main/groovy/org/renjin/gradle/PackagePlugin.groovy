package org.renjin.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy

class PackagePlugin implements Plugin<Project> {

    static List<String> DEFAULT_PACKAGES = [ 'stats', 'graphics', 'grDevices', 'utils', 'datasets', 'methods' ];
    static List<String> CORE_PACKAGES = DEFAULT_PACKAGES + [ 'splines', 'grid', 'parallel', 'tools', 'tcltk' ];

    @Override
    void apply(Project project) {

        project.pluginManager.apply(JavaPlugin)

        def extension = project.extensions.create('renjin', RenjinExtension)

        project.repositories {
            maven {
                url "https://nexus.bedatadriven.com/content/groups/public"
            }
        }

        def renjinPackager = project.configurations.create('renjinPackager')

//        project.dependencies.add('renjinPackager', extension.renjinVersion.map { "org.renjin:renjin-packager:${it}" })

        project.dependencies.add(renjinPackager.name, "org.renjin:renjin-packager:3.5-beta61")

        CORE_PACKAGES.forEach {
            project.dependencies.add('compile', "org.renjin:$it:3.5-beta61")
        }

        def copyPackageResourcesTask = project.tasks.register ('copyPackageResources', Copy)
        copyPackageResourcesTask.configure {
            from (project.projectDir) {
                include 'DESCRIPTION'
                include 'NAMESPACE'
            }
            from project.file('inst')
            into("${project.buildDir}/resources/${project.group.replace('.', '/')}/${project.name}")
        }

        def compileNamespaceTask = project.tasks.register('compileNamespace', CompileNamespaceTask, project)

        project.sourceSets {
            main {
                java.srcDirs = ['renjin']
                output.dir("${project.buildDir}/resources", builtBy: 'copyPackageResources')
                output.dir("${project.buildDir}/namespace", builtBy: 'compileNamespace')
            }
        }

        def compileJavaTask = project.tasks.named('compileJava')

        def testTask = project.tasks.register('testNamespace', TestNamespaceTask, project)
        testTask.configure {
            namespaceDirectory = compileNamespaceTask.flatMap { it.destinationDir }
            javaClassesDirectory = compileJavaTask.map { it.destinationDir }
        }

        project.tasks.named('test').configure {
            dependsOn testTask
        }
    }
}
