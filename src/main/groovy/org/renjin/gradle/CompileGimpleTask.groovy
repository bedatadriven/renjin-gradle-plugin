package org.renjin.gradle


import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option

class CompileGimpleTask extends DefaultTask {

    @Classpath
    final ConfigurableFileCollection compilerClasspath = project.objects.fileCollection()

    @CompileClasspath
    final ConfigurableFileCollection compileClasspath = project.objects.fileCollection()

    @CompileClasspath
    final ConfigurableFileCollection linkClasspath = project.objects.fileCollection()

    @SkipWhenEmpty
    @Optional
    @InputDirectory
    final DirectoryProperty gimpleDirectory = project.objects.directoryProperty()

    @OutputDirectory
    final DirectoryProperty destinationDir = project.objects.directoryProperty()

    @Input
    final Property<Boolean> ignoreErrors = project.objects.property(Boolean.class);


    CompileGimpleTask() {
        destinationDir.convention(project.layout.buildDirectory.dir("gimpleClasses"))
        compilerClasspath.setFrom(project.configurations.gimpleCompiler)
        linkClasspath.setFrom(project.configurations.link)
        compileClasspath.setFrom(project.configurations.compile)
        ignoreErrors.convention("true".equalsIgnoreCase(project.findProperty("ignoreGccBridgeErrors")))
    }

    @TaskAction
    void compile() {

        project.delete destinationDir
        project.mkdir destinationDir

        def fileLogger = new TaskFileLogger(this)

        try {
            project.javaexec {

                main = 'org.renjin.gnur.GnurSourcesCompiler'

                standardOutput = fileLogger.standardOutput
                errorOutput = fileLogger.errorOutput

                classpath compilerClasspath
                classpath linkClasspath
                classpath project.configurations.compile

                environment "GCC_BRIDGE_IGNORE_ERRORS", ignoreErrors.get() ? "TRUE" : "FALSE"

                args '--package', "${project.group}.${project.name}"
                args '--class', project.name
                args '--input-dir', gimpleDirectory.get().asFile.absolutePath
                args '--output-dir', destinationDir.get().asFile.absolutePath

                jvmArgs "-Xmx8G"

                if(project.hasProperty("enableGccBridgeLogging")) {
                    args '--logging-dir', "${project.buildDir}/gcc-bridge-logs"
                }

                if (project.hasProperty('debugGimple') && project.property("debugGimple") == project.name) {
                    jvmArgs '-Xdebug', '-Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=y'
                }
            }
        } finally {
            fileLogger.close()
        }
    }
}
