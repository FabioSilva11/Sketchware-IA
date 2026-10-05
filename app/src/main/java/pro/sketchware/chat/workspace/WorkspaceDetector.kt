package pro.sketchware.chat.workspace

/**
 * Detecta tecnologias e stacks presentes no Workspace para fins de metadados e contexto da IA.
 * Não restringe as operações disponíveis no Workspace.
 */
object WorkspaceDetector {

    @JvmStatic
    fun detectTechnologies(fs: WorkspaceFileSystem): String {
        val detected = mutableListOf<String>()

        if (fs.exists("settings.gradle") || fs.exists("settings.gradle.kts") || fs.exists("build.gradle") || fs.exists("build.gradle.kts")) {
            if (fs.exists("app/src/main/AndroidManifest.xml") || fs.exists("src/main/AndroidManifest.xml")) {
                detected.add("Android")
            } else {
                detected.add("Gradle")
            }
        }
        if (fs.exists("package.json")) {
            detected.add("Node.js")
            try {
                val pkgContent = fs.readText("package.json").lowercase()
                if (pkgContent.contains("\"react\"")) detected.add("React")
                if (pkgContent.contains("\"vue\"")) detected.add("Vue")
                if (pkgContent.contains("\"next\"")) detected.add("Next.js")
                if (pkgContent.contains("\"typescript\"")) detected.add("TypeScript")
            } catch (_: Exception) {
            }
        }
        if (fs.exists("pubspec.yaml")) detected.add("Flutter/Dart")
        if (fs.exists("Cargo.toml")) detected.add("Rust")
        if (fs.exists("go.mod")) detected.add("Go")
        if (fs.exists("pyproject.toml") || fs.exists("requirements.txt") || fs.exists("setup.py")) detected.add("Python")
        if (fs.exists("pom.xml")) detected.add("Maven/Java")
        if (fs.exists("composer.json")) detected.add("PHP")
        if (fs.exists("CMakeLists.txt") || fs.exists("Makefile")) detected.add("C/C++")
        if (fs.exists("index.html") && !detected.contains("Node.js")) detected.add("Web/HTML")
        if (fs.exists(".git")) detected.add("Git")

        return if (detected.isEmpty()) "Generic Workspace" else detected.joinToString(", ")
    }
}
