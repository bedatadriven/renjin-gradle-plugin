package org.renjin.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

class TestNamespaceTask extends DefaultTask {

    /**
     * Classpath for org.renjin.packaging.test.TestMain itself.
     */
    @Classpath
    final ConfigurableFileCollection packagerClasspath = project.objects.fileCollection()

    /**
     * Classpath that includes everything required to load the namespace and run the tests.
     */
    @Classpath
    final ConfigurableFileCollection runtimeClasspath = project.objects.fileCollection()

    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    @SkipWhenEmpty
    final DirectoryProperty testsDirectory = project.objects.directoryProperty()

    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    @SkipWhenEmpty
    final DirectoryProperty manDirectory = project.objects.directoryProperty()

    @Input
    final ListProperty<String> defaultPackages = project.objects.listProperty(String.class)

    @Input
    boolean ignoreFailures = false;

    @Inject
    TestNamespaceTask(Project project) {
        group = 'Verification'
        description = 'Run package tests'
        packagerClasspath.setFrom(project.configurations.renjinPackager)
        if(project.file('tests').exists()) {
            testsDirectory.convention(project.layout.projectDirectory.dir('tests'))
        }
        if(project.file('man').exists()) {
            manDirectory.convention(project.layout.projectDirectory.dir('man'))
        }
        defaultPackages.convention(PackagePlugin.DEFAULT_PACKAGES)
        outputs.upToDateWhen { true }
    }

    @TaskAction
    void run() {
        def fileLogger = new TaskFileLogger(this)
        logger.info("defaultPackages = ${defaultPackages.get()}")

        project.javaexec {
            main = 'org.renjin.packaging.test.TestMain'

            standardOutput = fileLogger.standardOutput
            errorOutput = fileLogger.errorOutput

            classpath packagerClasspath
            classpath runtimeClasspath

            jvmArgs '-ea'

            args "--name=${project.name}"
            args "--report-dir=${project.buildDir}/renjin-test-reports"
            args "--default-packages=${defaultPackages.get().join(',')}"

            ignoreExitValue = ignoreFailures

            if (testsDirectory.present) {
                workingDir testsDirectory.get().asFile.absolutePath
                args testsDirectory.get().asFile.absolutePath
            }
            if (manDirectory.present) {
                args manDirectory.get().asFile.absolutePath
            }
            if (project.hasProperty('debugTests')) {
                jvmArgs '-Xdebug', '-Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=y'
            }
        }
    }
}
