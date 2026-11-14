package mod.pranav.build

import pro.sketchware.workspace.ProjectWorkspaceManager
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.Diagnostic
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import com.android.tools.r8.origin.Origin
import java.nio.file.Files
import java.nio.file.Paths

class R8Compiler(
    private val rules: MutableList<String>,
    private val configs: Array<String>,
    val libs: Array<String>,
    private val inputs: Array<String>,
    private val minApi: Int,
    val ProjectWorkspaceManager: ProjectWorkspaceManager
) {

    fun compile() {
        val output = Paths.get(ProjectWorkspaceManager.binDirectoryPath, "dex")
        Files.createDirectories(output)
        val command = R8Command.builder(QuietR8DiagnosticsHandler)
            .addProgramFiles(inputs.map { Paths.get(it) })
            .addProguardConfiguration(rules, Origin.unknown())
            .addProguardConfigurationFiles(configs.map { Paths.get(it) })
            .setProguardMapOutputPath(Paths.get(ProjectWorkspaceManager.proguardMappingPath))
            .setMinApiLevel(minApi)
            .addLibraryFiles(libs.map { Paths.get(it) })
            .setOutput(output, OutputMode.DexIndexed)
            .setMode(CompilationMode.RELEASE)
            .build()

        R8.run(command)
    }

    private object QuietR8DiagnosticsHandler : DiagnosticsHandler {
        override fun info(diagnostic: Diagnostic) {
            // R8 verbose output can be enormous on large Sketchware projects and can exhaust the

            // app heap when the compile log UI renders it. Keep warnings/errors, drop info spam.

        }
    }
}
