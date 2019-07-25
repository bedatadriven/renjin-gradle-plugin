package org.renjin.gradle


import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*

class CompileGimpleTask extends DefaultTask {

    @Classpath
    final ConfigurableFileCollection compilerClasspath = project.objects.fileCollection()

    @CompileClasspath
    final ConfigurableFileCollection compileClasspath = project.objects.fileCollection()

    @CompileClasspath
    final ConfigurableFileCollection linkClasspath = project.objects.fileCollection()

    @SkipWhenEmpty
    @InputDirectory
    final DirectoryProperty gimpleDirectory = project.objects.directoryProperty();

    @OutputDirectory
    final DirectoryProperty destinationDir = project.objects.directoryProperty()

    CompileGimpleTask() {
        destinationDir.convention(project.layout.buildDirectory.dir("gimpleClasses"))
        compilerClasspath.setFrom(project.configurations.gimpleCompiler)
        linkClasspath.setFrom(project.configurations.link)
        compileClasspath.setFrom(project.configurations.compile)
    }

    @TaskAction
    void compile() {

        project.delete destinationDir
        project.mkdir destinationDir

        project.javaexec {

            main = 'org.renjin.gnur.GnurSourcesCompiler'
            classpath compilerClasspath
            classpath linkClasspath
            classpath compileClasspath

            args '--package', "${project.group}.${project.name}"
            args '--class', project.name
            args '--input-dir', "${project.buildDir}/gimple"
            args '--output-dir', "${project.buildDir}/native"
            args '--logging-dir', "${project.buildDir}/gcc-bridge-logs"
//
//        if (project.hasProperty('debugGimple') && project.property("debugGimple") == project.name) {
//            jvmArgs '-Xdebug', '-Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=y'
//        }

        }
    }
}
