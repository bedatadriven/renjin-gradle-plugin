package org.renjin.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

import javax.inject.Inject

class CompileNamespaceTask extends DefaultTask {

    @Classpath
    final ConfigurableFileCollection packagerClasspath = project.objects.fileCollection()

    @Classpath
    final ConfigurableFileCollection compileClasspath = project.objects.fileCollection()

    @Input
    final Property<List<String>> defaultPackages = project.objects.property(List.class)

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    final DirectoryProperty sourceDirectory = project.objects.directoryProperty()

    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    final DirectoryProperty dataDirectory = project.objects.directoryProperty();

    @OutputDirectory
    final DirectoryProperty destinationDir = project.objects.directoryProperty()

    @Inject
    CompileNamespaceTask(Project project) {
        group = 'Build'
        description = 'Compile R sources'
        packagerClasspath.setFrom(project.configurations.renjinPackager)
        compileClasspath.from(project.configurations.compile)
        sourceDirectory.convention(project.layout.projectDirectory.dir('R'))
        if(project.file('data').exists()) {
            dataDirectory.convention(project.layout.projectDirectory.dir('data'))
        }
        destinationDir.convention(project.layout.buildDirectory.dir('namespace'))
        defaultPackages.convention(PackagePlugin.DEFAULT_PACKAGES)

        inputs.file(project.file('DESCRIPTION'))
        inputs.file(project.file('NAMESPACE'))
    }

    @TaskAction
    void compile() {

        if(!project.group) {
            throw new RuntimeException("The project group must be specified")
        }

        project.delete destinationDir
        project.mkdir destinationDir

        project.javaexec {
            main = 'org.renjin.packaging.GnurPackageBuilder'
            classpath destinationDir
            classpath compileClasspath
            classpath packagerClasspath
            args '--groupId', project.group
            args '--name', project.name
            args '--home', 'foo'
            args "--default-packages=${defaultPackages.get().join(',')}"
        }
    }
}
