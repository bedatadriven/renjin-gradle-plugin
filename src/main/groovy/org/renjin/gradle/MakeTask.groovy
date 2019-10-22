package org.renjin.gradle

import groovy.io.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.internal.impldep.com.google.common.io.ByteStreams

import javax.inject.Inject
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Invoke 'make' in order to build native sources included in a package.
 *
 * <p>This task executes 'make' with the required environment variables, and
 * copies the resulting .gimple files to a compressed zip archive.
 *
 * <p>We want to run this as a separate task from compiling the gimple because it's
 * very time consuming and the Gimple compiler changes more often than the sources.
 */
class MakeTask extends DefaultTask {

    @InputDirectory
    final DirectoryProperty renjinHomeDir = project.objects.directoryProperty()

    @SkipWhenEmpty
    @InputDirectory
    final DirectoryProperty sourcesDirectory = project.objects.directoryProperty()

    @InputFile
    final RegularFileProperty pluginLibrary = project.objects.fileProperty()

    @CompileClasspath
    final ConfigurableFileCollection linkClasspath = project.objects.fileCollection()

    @OutputFile
    final RegularFileProperty gimpleArchiveFile = project.objects.fileProperty()

    @InputFile
    final RegularFileProperty descriptionFile = project.objects.fileProperty()

    @Input
    String cxxStandard = "";

    @Inject
    MakeTask(Project project) {
        this.group = 'Build'
        this.description = 'Make native sources'
        renjinHomeDir.set(new File(project.property("renjinHomeDir")))
        pluginLibrary.set(new File(project.property("gccBridgePlugin")))
        sourcesDirectory.convention(project.layout.projectDirectory.dir('src'))
        gimpleArchiveFile.convention(project.layout.buildDirectory.file('gimple.zip'))
        descriptionFile.convention(project.layout.projectDirectory.file("DESCRIPTION"))
    }

    @TaskAction
    void make() {
        // Store output for later
        def fileLogger = new TaskFileLogger(this)
        logging.addStandardOutputListener(fileLogger)

        try {
            // First remove all the existing .o, .so, and .gimple files
            cleanIntermediateGccFiles()

            // Now we can re-run make

            def makeVars = sourcesDirectory.get().file("Makevars.renjin").asFile
            if (!makeVars.exists()) {
                makeVars = sourcesDirectory.get().file("Makevars").asFile
            }
            def homeDir = renjinHomeDir.get().asFile.absolutePath
            def makeconfFile = new File("$homeDir/etc/Makeconf")
            def shlibMk = new File("$homeDir/share/make/shlib.mk")

            project.exec {
                executable = 'make'

                standardOutput = fileLogger.standardOutput
                errorOutput = fileLogger.errorOutput

                if (makeVars.exists()) {
                    args '-f', makeVars.absolutePath
                }

                args '-f', makeconfFile.absolutePath
                args '-f', shlibMk.absolutePath

                args "SHLIB='${project.name}.so'"

                if (!(makeVars.exists() && makeVars.readLines().grep(~/^OBJECTS\s*=.*/))) {
                    def objectFiles = [];
                    sourcesDirectory.get().asFile.eachFileMatch(~/.*\.(c|f|f77|f90|f95|f03|for|cpp|cxx|cc)$/) { file ->
                        objectFiles.add(file.name.replaceFirst(~/\.[^.]+$/, '.o'))
                    }
                    args "OBJECTS=${objectFiles.join(' ')}"
                }

                args "BRIDGE_PLUGIN=${pluginLibrary.get().asFile.absolutePath}"

                if ("C++11".equalsIgnoreCase(cxxStandard)) {
                    args 'CXX=$(CXX11) $(CXX11STD)'
                    args 'CXXFLAGS=$(CXX11FLAGS)'
                    args 'CXXPICFLAGS=$(CXX11PICFLAGS)'
                    args 'SHLIB_LDFLAGS=$(SHLIB_CXX11LDFLAGS)'
                    args 'SHLIB_LD=$(SHLIB_CXX11LD)'
                }

                environment 'R_VERSION', '3.5.3'
                environment 'R_HOME', homeDir
                environment 'R_INCLUDE_DIR', "${homeDir}/include"
                environment 'R_SHARE_DIR', "${homeDir}/share"
                environment 'R_PACKAGE_NAME', project.name
                environment 'R_INSTALL_PACKAGE', project.name
                environment 'MAKE', 'make'
                environment 'R_UNZIPCMD', '/usr/bin/unzip'
                environment 'R_GZIPCMD', '/usr/bin/gzip'

                environment 'CLINK_CPPFLAGS',
                        project.configurations.link.dependencies.collect {
                            "-I\"${it.dependencyProject.file('inst/include')}\""
                        }.join(" ")

                workingDir sourcesDirectory.get().asFile.absolutePath

            }

            // Reset the output directory
            project.delete gimpleArchiveFile

            // Now copy ONLY the gimple into a ZIP file
            // We do this because the raw json is huge and fills up the build disk
            archiveGimple()

            // Clean up intermediate files so we don't interfere with Gradle's
            // incremental build logic
            cleanIntermediateGccFiles()

        } finally {
            fileLogger.close()
        }
    }

    private void archiveGimple() {
        def archiveOut = new ZipOutputStream(new FileOutputStream(gimpleArchiveFile.get().asFile))
        def sourceDir = sourcesDirectory.get().asFile.absoluteFile
        sourceDir.eachFileRecurse(FileType.FILES) {
            if (it.name.endsWith('.gimple')) {
                archiveOut.putNextEntry(new ZipEntry(sourceDir.relativePath(it)))
                it.withInputStream { inputStream ->
                    ByteStreams.copy(inputStream, archiveOut)
                }
            }
        }
        archiveOut.close()
    }

    /**
     * Removes intermediate results produced by GCC. This is necessary because otherwise
     * Gradle cannot properly determine whether the inputs have changed. Also, if we change the plugin
     * after running the build, GCC will not rebuild and regenerate the .gimple files because it only considers
     * the .o files when doing its own dirty-checking.
     */
    private boolean cleanIntermediateGccFiles() {
        project.delete sourcesDirectory.asFileTree.matching {
            include "**/*.o"
            include "**/*.d"
            include "**/*.so"
            include "**/*.gimple"
        }
    }
}
