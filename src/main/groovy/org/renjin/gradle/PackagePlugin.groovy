package org.renjin.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy

class PackagePlugin implements Plugin<Project> {

    static List<String> DEFAULT_PACKAGES = [ 'stats', 'graphics', 'grDevices', 'utils', 'datasets', 'methods' ]
    static List<String> CORE_PACKAGES = DEFAULT_PACKAGES + [ 'splines', 'grid', 'parallel', 'tools', 'tcltk', 'compiler' ]

    @Override
    void apply(Project project) {

        project.pluginManager.apply(JavaPlugin)

        def extension = project.extensions.create('renjin', RenjinExtension)
        if(project.hasProperty("renjinVersion")) {
            extension.renjinVersion.convention(project.property('renjinVersion'))
        }

        project.repositories {
            maven {
                url "https://nexus.bedatadriven.com/content/groups/public"
            }
        }

        def renjinPackager = project.configurations.create('renjinPackager')

        renjinPackager.incoming.beforeResolve {
            def renjinVersion = extension.resolveRenjinVersion()
            project.dependencies.add(renjinPackager.name, "org.renjin:renjin-packager:${renjinVersion}")
            CORE_PACKAGES.forEach {
                project.dependencies.add(renjinPackager.name, "org.renjin:$it:${renjinVersion}")
            }
        }
        def configureTask = project.tasks.register('configure', ConfigureTask, project)
        def copyPackageResourcesTask = project.tasks.register ('copyPackageResources', Copy)
        copyPackageResourcesTask.configure {
            from (project.projectDir) {
                include 'DESCRIPTION'
                include 'NAMESPACE'
            }
            from project.file('inst')
            into("${project.buildDir}/resources/${project.group.replace('.', '/')}/${project.name}")
        }

        project.tasks.register('compileNamespace', CompileNamespaceTask, project)

        project.sourceSets {
            main {
                java.srcDirs = ['renjin']
                output.dir("${project.buildDir}/inst", builtBy: 'copyPackageResources')
                output.dir("${project.buildDir}/namespace", builtBy: 'compileNamespace')
            }
        }

        def testTask = project.tasks.register('testNamespace', TestNamespaceTask, project)
        testTask.configure {
            runtimeClasspath.from(project.sourceSets.main.output)
            runtimeClasspath.from(project.configurations.testRuntime)
        }

        project.tasks.named('test').configure {
            dependsOn testTask
        }
    }
}
