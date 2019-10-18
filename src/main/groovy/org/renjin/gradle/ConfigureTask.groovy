package org.renjin.gradle

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

class ConfigureTask extends AbstractTask {

    @InputFile
    @SkipWhenEmpty
    final RegularFileProperty configureFile = project.objects.fileProperty()

    @InputDirectory
    final DirectoryProperty renjinHomeDir = project.objects.directoryProperty()

    @Inject
    ConfigureTask(Project project) {
        group = 'Build'
        description = 'Configure native sources'
        renjinHomeDir.set(new File(project.property("renjinHomeDir")))

        def configureScript = project.file('configure')
        if(configureScript.exists()) {
            configureFile.set(configureScript)
        }

        // Assume configure only has to be run once...
        outputs.upToDateWhen { true }
    }

    @TaskAction
    void configure() {
        def fileLogger = new TaskFileLogger(project.buildDir, this)
        logging.addStandardErrorListener(fileLogger)
        logging.addStandardOutputListener(fileLogger)

        try {
            project.exec {
                executable = 'sh'
                args configureFile.get().asFile.name
                environment 'R_HOME', renjinHomeDir.get().asFile.absolutePath
            }
        } finally {
            fileLogger.close()
        }
    }
}
