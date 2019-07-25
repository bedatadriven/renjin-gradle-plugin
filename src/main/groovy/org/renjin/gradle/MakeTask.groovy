package org.renjin.gradle


import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

import javax.inject.Inject

class MakeTask extends DefaultTask {

    @InputDirectory
    final DirectoryProperty renjinHomeDir = project.objects.directoryProperty()

    @SkipWhenEmpty
    @InputDirectory
    final DirectoryProperty sourcesDirectory = project.objects.directoryProperty()

    @InputFile
    final RegularFileProperty pluginLibrary = project.objects.fileProperty()
//
//    @Input
//    final ConfigurableFileCollection includeDirectories = project.objects.directoryProperty()

    @CompileClasspath
    final ConfigurableFileCollection linkClasspath = project.objects.fileCollection()

    @OutputDirectory
    final DirectoryProperty gimpleDirectory = project.objects.directoryProperty()

    @Inject
    MakeTask(Project project) {
        this.group = 'Build'
        this.description = 'Make native sources'
        renjinHomeDir.set(new File(project.property("renjinHomeDir")))
        pluginLibrary.set(new File(project.property("gccBridgePlugin")))
        sourcesDirectory.convention(project.layout.projectDirectory.dir('src'))
        gimpleDirectory.convention(project.layout.buildDirectory.dir('gimple'))
//
//        def projectIncludeDir = project.file("inst/include")
//        if(projectIncludeDir.exists()) {
//            includeDirectories.from(projectIncludeDir)
//        }
    }

    @TaskAction
    void make() {
        // First remove all the existing .o, .so, and .gimple files
        project.delete sourcesDirectory.asFileTree.matching {
            include "**/*.o"
            include "**/*.d"
            include "**/*.so"
            include "**/*.gimple"
        }

        // Now we can re-run make

        def makeVars = sourcesDirectory.get().file("Makevars.renjin").asFile
        if (!makeVars.exists()) {
            makeVars =  sourcesDirectory.get().file("Makevars").asFile
        }
        def homeDir = renjinHomeDir.get().asFile.absolutePath
        def makeconfFile = new File("$homeDir/etc/Makeconf")
        def shlibMk = new File("$homeDir/share/make/shlib.mk")

        project.exec {
            executable = 'make'

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

            // TODO CXX11
            // TODO include dependency headers

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
                    project.configurations.link.dependencies.collect { "-I\"${it.dependencyProject.file('inst/include')}\"" }.join(" ")

            workingDir sourcesDirectory.get().asFile.absolutePath
        }

        // Reset the output directory
        project.delete gimpleDirectory
        project.mkdir gimpleDirectory

        // Now copy ONLY the gimple into the output directory
        project.copy {
            into gimpleDirectory
            from('src') {
                include '**/*.gimple'
            }
        }

        // Cleanup the leftovers
        project.delete sourcesDirectory.asFileTree.matching {
            include "**/*.o"
            include "**/*.d"
            include "**/*.so"
            include "**/*.gimple"
        }
    }
}
