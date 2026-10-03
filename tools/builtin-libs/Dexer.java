import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dexes every built-in library in a single JVM.
 * <p>
 * Usage: java -cp d8.jar Dexer.java libraries.tsv android.jar outputDir minApi
 * <p>
 * Each line of libraries.tsv is "name\tclasses.jar". Every other library is put on the
 * classpath so that D8 can desugar default and static interface methods correctly.
 */
public class Dexer {
    public static void main(String[] args) throws Exception {
        Path listFile = Path.of(args[0]);
        Path androidJar = Path.of(args[1]);
        Path outputRoot = Path.of(args[2]);
        int minApi = Integer.parseInt(args[3]);

        List<String[]> libraries = new ArrayList<>();
        for (String line : Files.readAllLines(listFile)) {
            if (!line.isBlank()) {
                libraries.add(line.split("\t"));
            }
        }

        int failures = 0;
        for (String[] library : libraries) {
            Path jar = Path.of(library[1]);
            List<Path> classpath = new ArrayList<>();
            for (String[] other : libraries) {
                if (!other[1].equals(library[1])) {
                    classpath.add(Path.of(other[1]));
                }
            }

            Path output = outputRoot.resolve(library[0]);
            Files.createDirectories(output);
            Collector collector = new Collector(library[0]);
            try {
                D8.run(D8Command.builder(collector)
                        .addProgramFiles(jar)
                        .addLibraryFiles(androidJar)
                        .addClasspathFiles(classpath)
                        .setMinApiLevel(minApi)
                        .setMode(CompilationMode.RELEASE)
                        .setOutput(output, OutputMode.DexIndexed)
                        .build());
                System.out.println("OK " + library[0] + (collector.warnings > 0 ? " (" + collector.warnings + " warnings)" : ""));
            } catch (Exception e) {
                failures++;
                System.out.println("FAILED " + library[0] + ": " + e.getMessage());
            }
        }

        if (failures > 0) {
            System.exit(1);
        }
    }

    private static final class Collector implements DiagnosticsHandler {
        private final String library;
        private int warnings;

        Collector(String library) {
            this.library = library;
        }

        @Override
        public void error(Diagnostic diagnostic) {
            System.out.println("  error [" + library + "] " + diagnostic.getDiagnosticMessage());
        }

        @Override
        public void warning(Diagnostic diagnostic) {
            warnings++;
            if (warnings <= 3) {
                System.out.println("  warning [" + library + "] " + diagnostic.getDiagnosticMessage());
            }
        }

        @Override
        public void info(Diagnostic diagnostic) {
        }
    }
}
