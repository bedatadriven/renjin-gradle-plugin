package org.renjin.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

class TestNamespaceTask extends DefaultTask {

    @Classpath
    final ConfigurableFileCollection packagerClasspath = project.objects.fileCollection()

    @Classpath
    final ConfigurableFileCollection compileClasspath = project.objects.fileCollection()

    @Classpath
    final ConfigurableFileCollection testRuntimeClasspath = project.objects.fileCollection()


    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    @SkipWhenEmpty
    final DirectoryProperty testsDirectory = project.objects.directoryProperty()


    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    @SkipWhenEmpty
    final DirectoryProperty manDirectory = project.objects.directoryProperty()

    @Input
    final Property<List<String>> defaultPackages = project.objects.property(List.class)

    @InputDirectory
    final DirectoryProperty namespaceDirectory = project.objects.directoryProperty()

    @InputDirectory
    final Property<File> javaClassesDirectory = project.objects.property(File.class)

    @Inject
    TestNamespaceTask(Project project) {
        group = 'Verification'
        description = 'Run package tests'
        packagerClasspath.setFrom(project.configurations.renjinPackager)
        compileClasspath.setFrom(project.configurations.compile)
        testRuntimeClasspath.setFrom(project.configurations.testRuntime)
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

        project.javaexec {
            main = 'org.renjin.packaging.test.TestMain'

            classpath namespaceDirectory
            classpath javaClassesDirectory
            classpath compileClasspath
            classpath testRuntimeClasspath
            classpath packagerClasspath

            args "--name=${project.name}"
            args "--report-dir=${project.buildDir}/renjin-test-reports"
            args "--default-packages=${defaultPackages.get().join(',')}"

            workingDir testsDirectory.get().asFile.absolutePath

            if (testsDirectory.present) {
                args testsDirectory.get().asFile.absolutePath
            }
            if (manDirectory.present) {
                args manDirectory.get().asFile.absolutePath
            }
        }
    }
}
