package org.renjin.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

class NativeSourcePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(PackagePlugin)

        def configureTask = project.tasks.register('configure', ConfigureTask, project)
        def makeTask = project.tasks.register('make', MakeTask, project)

        def linkConfiguration = project.configurations.create('link')

        def gimpleCompilerConfig = project.configurations.create('gimpleCompiler')
        project.dependencies.add('gimpleCompiler', "org.renjin:renjin-gnur-compiler:3.5-beta61")

        makeTask.configure {
            dependsOn configureTask

            // If we are 'LinkingTo' any projects, then make sure
            // that those projects run 'configure' first, as they may generate
            // sources
            project.configurations.link.dependencies.forEach {


                if(it instanceof ProjectDependency) {
                    def linkingTo = it.dependencyProject;

                    def includeDir = linkingTo.file("inst/include")
                    if (includeDir.exists()) {
                        inputs.dir includeDir
                    }

                    dependsOn ":${linkingTo.path}:configure"
                }
            }
        }

        def compileGimpleTask = project.tasks.register('compileGimple', CompileGimpleTask)
        compileGimpleTask.configure {
            gimpleDirectory = makeTask.flatMap { it.gimpleDirectory }
        }

        project.tasks.named('compileNamespace').configure {
            compileClasspath.from(compileGimpleTask.flatMap { it.destinationDir })
        }
    }
}
