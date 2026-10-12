package pro.sketchware.supabase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class SupabaseClientSourceTest {

    private static final String SOURCE = SupabaseClientSource.generate("com.example.notas",
            "https://abcd.supabase.co/", "eyJ\"anon\\key");

    @Test
    public void projectValuesAreInsertedAsJavaStrings() {
        assertTrue(SOURCE.startsWith("package com.example.notas;"));
        // The URL is kept as typed; the client trims the trailing slash itself
        assertTrue(SOURCE.contains("public static final String URL = \"https://abcd.supabase.co/\";"));
        assertTrue(SOURCE.contains("public static final String ANON_KEY = \"eyJ\\\"anon\\\\key\";"));
        assertFalse(SOURCE.contains("__SUPABASE"));
        assertFalse(SOURCE.contains("__PACKAGE__"));
    }

    @Test
    public void javaStringEscapesWhatWouldEndTheLiteral() {
        assertEquals("a\\\"b\\\\c\\nd", SupabaseClientSource.javaString("a\"b\\c\nd"));
        assertEquals("todos", SupabaseClientSource.javaString("todos"));
    }

    /**
     * The generated client compiles with Java 8 against the same OkHttp, Gson and Android APIs an app gets, so a
     * project using a Supabase component builds.
     */
    @Test
    public void generatedClientCompiles() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) return; // a JRE without the compiler can't run this check
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///com/example/notas/SupabaseClient.java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return SOURCE;
            }
        };
        File out = Files.createTempDirectory("supabase-client").toFile();
        List<String> options = new ArrayList<>();
        Collections.addAll(options, "-source", "8", "-target", "8", "-Xlint:-options", "-proc:none",
                "-classpath", System.getProperty("java.class.path"), "-d", out.getPath());
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean ok = compiler.getTask(null, null, diagnostics, options, null, Collections.singletonList(file)).call();
        StringBuilder errors = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) errors.append(d.getLineNumber()).append(": ").append(d.getMessage(null)).append('\n');
        }
        assertTrue(errors.toString(), ok);
        assertTrue(new File(out, "com/example/notas/SupabaseClient$Database.class").isFile());
        assertTrue(new File(out, "com/example/notas/SupabaseClient$Auth.class").isFile());
        assertTrue(new File(out, "com/example/notas/SupabaseClient$Storage.class").isFile());
    }
}
