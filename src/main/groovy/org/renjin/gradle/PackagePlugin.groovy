package org.renjin.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy

import java.time.Duration

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
            mavenCentral()
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
            from (project.file('inst')) {
                exclude 'java/*.jar'
            }
            into("${project.buildDir}/inst/${project.group.replace('.', '/')}/${project.name}")
        }

        // If this package includes JAR files, then they are most
        // likely to be used with rJava. We merge them onto our jar
        // so that they are available on the classpath.
        def jarDir = project.file('inst/java')
        if(jarDir.exists()) {
            def mergeJars = project.tasks.register('mergeJars', Copy)
            mergeJars.configure {
                jarDir.eachFile { jarFile ->
                    if(jarFile.name.endsWith('.jar')) {
                        from project.zipTree(jarFile)
                    }
                }
                into "${project.buildDir}/mergedJars"
            }
        }

        project.tasks.register('compileNamespace', CompileNamespaceTask, project).configure {
            dependsOn 'copyPackageResources'
            compileClasspath.from("${project.buildDir}/inst")
        }

        project.sourceSets {
            main {
                java.srcDirs = ['renjin']
                output.dir("${project.buildDir}/inst", builtBy: 'copyPackageResources')
                output.dir("${project.buildDir}/namespace", builtBy: 'compileNamespace')

                if(jarDir.exists()) {
                    output.dir("${project.buildDir}/mergedJars", builtBy: 'mergeJars')
                }
            }
        }

        def testTask = project.tasks.register('testNamespace', TestNamespaceTask, project)
        testTask.configure {
            runtimeClasspath.from(project.sourceSets.main.output)
            runtimeClasspath.from(project.configurations.testRuntime)
            timeout = Duration.ofMinutes(5)
        }

        project.tasks.named('test').configure {
            dependsOn testTask
        }
    }
}
