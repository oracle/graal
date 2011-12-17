/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.max.ide;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import javax.tools.*;
import javax.tools.JavaCompiler.*;

import com.sun.max.program.*;

/**
 * Provides an interface for invoking a Java source compiler (e.g. javac) and a C header and stub generator (i.e.
 * javah). The Java source compiler used is determined the <a
 * href="http://java.sun.com/javase/6/docs/api/java/util/ServiceLoader.html">service provider mechanism</a>. For
 * example, if you want to use the Eclipse batch compiler, then simply place the stand alone ecj.jar JAR file on the
 * classpath.
 */
public final class ToolChain {

    private ToolChain() {
    }

    private static JavaCompiler javaCompiler;

    private static JavaCompiler javaCompiler() {
        if (javaCompiler == null) {
            final Iterator<JavaCompiler> iterator = ServiceLoader.load(JavaCompiler.class).iterator();
            if (iterator.hasNext()) {
                javaCompiler = iterator.next();
            } else {
                javaCompiler = ToolProvider.getSystemJavaCompiler();
            }
            ProgramError.check(javaCompiler != null, "Cannot find a Java compiler");
        }
        return javaCompiler;
    }

    /**
     * Compiles the source for a given class. The location of the source file to be compiled and the directory to which
     * the output class files are to be written are determined by the current {@link JavaProject} context.
     * <p>
     * The supported {@code options} are:
     * <p>
     *
     * <pre>
     *     -noinlinejsr    implement {@code finally} clauses using the {@link Bytecode#JSR} and {@link Bytecode#RET} bytecodes
     * </pre>
     *
     *
     * @param projClass a class denoting a project (i.e. any class in the project)
     * @param className the name of the class to be compiled
     * @param options options for modifying the compilation
     * @return true if the compilation succeeded without any errors, false otherwise
     */
    public static boolean compile(Class projClass, String className, String... options) {
        return compile(projClass, new String[] {className}, options);
    }

    /**
     * Compiles the source for one or more given classes. The location of the source files to be compiled and the
     * directory to which the output class files are to be written are determined by a given project
     * context.
     * <p>
     * The supported {@code options} are:
     * <p>
     *
     * <pre>
     *     -noinlinejsr    implement {@code finally} clauses using the {@link Bytecode#JSR} and {@link Bytecode#RET} bytecodes
     * </pre>
     *
     *
     * @param projClass a class denoting a project (i.e. any class in the project)
     * @param className the name of the class to be compiled
     * @param options options for modifying the compilation
     * @return true if the compilation succeeded without any errors, false otherwise
     */
    public static boolean compile(Class projClass, String[] classNames, String... options) {

        final Classpath classPath = JavaProject.getClassPath(projClass, true);
        final Classpath sourcePath = JavaProject.getSourcePath(projClass, true);
        final String outputDirectory = classPath.entries().get(0).toString();

        final ArrayList<File> sourceFiles = new ArrayList<File>(classNames.length);
        for (String className : classNames) {
            final String sourceFilePathSuffix = className.replace('.', File.separatorChar) + ".java";
            final File sourceFile = sourcePath.findFile(sourceFilePathSuffix);
            if (sourceFile == null) {
                ProgramWarning.message("Could not find source file for " + className);
                return false;
            }
            sourceFiles.add(sourceFile);
        }

        final JavaCompiler compiler = javaCompiler();
        final String compilerName = compiler.getClass().getName();
        final List<String> opts = new ArrayList<String>(Arrays.asList(new String[] {"-cp", classPath.toString(), "-d", outputDirectory}));
        if (compilerName.equals("com.sun.tools.javac.api.JavacTool")) {
            opts.add("-cp");
            opts.add(classPath.toString());
            opts.add("-d");
            opts.add(outputDirectory);
            for (String option : options) {
                if (option.equals("-noinlinejsr")) {
                    opts.add("-source");
                    opts.add("1.4");
                    opts.add("-target");
                    opts.add("1.4");
                    opts.add("-XDjsrlimit=0");
                } else {
                    throw new IllegalArgumentException("Unsupported compiler option " + option);
                }
            }
        } else if (compiler.getClass().getName().equals("org.eclipse.jdt.internal.compiler.tool.EclipseCompiler")) {
            opts.add("-cp");
            opts.add(classPath.toString());
            opts.add("-d");
            opts.add(outputDirectory);
            opts.add("-noExit");
            boolean inlineJSR = true;
            for (String option : options) {
                if (option.equals("-noinlinejsr")) {
                    inlineJSR = false;
                } else {
                    throw new IllegalArgumentException("Unsupported compiler option " + option);
                }
            }
            if (inlineJSR) {
                opts.add("-inlineJSR");
            } else {
                opts.add("-source");
                opts.add("1.4");
                opts.add("-target");
                opts.add("1.4");
            }
        } else {
            ProgramWarning.message("Unknown Java compiler may not accept same command line options as javac: " + compilerName);
            opts.add("-cp");
            opts.add(classPath.toString());
            opts.add("-d");
            opts.add(outputDirectory);
        }

        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        final StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        final Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles);
        final CompilationTask task = compiler.getTask(null, fileManager, diagnostics, opts, null, compilationUnits);
        final boolean result = task.call();
        final Set<String> reportedDiagnostics = new HashSet<String>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            final String message = diagnostic.getMessage(Locale.getDefault());
            if (!reportedDiagnostics.contains(message)) {
                reportedDiagnostics.add(message);
                if (!message.contains("is Sun proprietary API and may be removed in a future release")) {
                    System.err.println(message);
                }
            }
        }
        try {
            fileManager.close();
        } catch (IOException e) {
            ProgramWarning.message("Error closing file manager: " + e);
        }
        return result;
    }

    private static Method javahMainMethod;

    private static Method javah() {
        if (javahMainMethod == null) {
            Class<?> javahMainClass = null;
            try {
                // On the Apple JDKs, there is no tools.jar: all the tools are in classes.jar
                javahMainClass = Class.forName("com.sun.tools.javah.Main");
            } catch (ClassNotFoundException classNotFoundException) {
                // This is expected on non-Apple JDKs
                final ClassLoader systemToolClassLoader = ToolProvider.getSystemToolClassLoader();
                ProgramError.check(systemToolClassLoader != null, "Cannot find the standard system tools class loader");
                try {
                    javahMainClass = Class.forName("com.sun.tools.javah.Main", true, systemToolClassLoader);
                    final URLClassLoader urlClassLoader = (URLClassLoader) javahMainClass.getClassLoader();
                    updateJavaClassPath(urlClassLoader);
                } catch (Exception exception) {
                    ProgramWarning.message("Cannot find or initialize javah: " + exception);
                }
            }
            try {
                if (javahMainClass != null) {
                    javahMainMethod = javahMainClass.getDeclaredMethod("main", String[].class);
                }
            } catch (Exception exception) {
                ProgramWarning.message("Cannot find or initialize javah: " + exception);
            }
        }
        return javahMainMethod;
    }

    /**
     * This hack is necessary as javah uses a doclet to do its actual work. The doclet class is found by creating a URL class loader
     * from the system property "java.class.path" which does not include the path to tools.jar for
     * standard JDK installations.
     */
    private static void updateJavaClassPath(final URLClassLoader urlClassLoader) {
        String javaClassPath = System.getProperty("java.class.path", ".");
        for (URL url : urlClassLoader.getURLs()) {
            final String path = url.getPath();
            if (path != null && !path.isEmpty()) {
                final File file = new File(path);
                if (file.exists()) {
                    javaClassPath += File.pathSeparator + file.getAbsolutePath();
                }
            }
        }
        System.setProperty("java.class.path", javaClassPath);
    }

    public static boolean javah(String[] args) {
        try {
            javah().invoke(null, (Object) args);
            return true;
        } catch (InvocationTargetException e) {
            ProgramWarning.message("Error invoking javah: " + e.getTargetException());
        } catch (Exception e) {
            ProgramWarning.message("Error invoking javah: " + e);
        }
        return false;
    }

    private static Method javapMainMethod;

    private static Method javap() {
        if (javapMainMethod == null) {
            Class<?> javapMainClass = null;
            try {
                // On the Apple JDKs, there is no tools.jar: all the tools are in classes.jar
                javapMainClass = Class.forName("sun.tools.javap.Main");
            } catch (ClassNotFoundException classNotFoundException) {
                // This is expected on non-Apple JDKs
                final ClassLoader systemToolClassLoader = ToolProvider.getSystemToolClassLoader();
                ProgramError.check(systemToolClassLoader != null, "Cannot find the standard system tools class loader");
                try {
                    javapMainClass = Class.forName("sun.tools.javap.Main", true, systemToolClassLoader);
                } catch (Exception exception) {
                    ProgramWarning.message("Cannot find or initialize javap: " + exception);
                }
            }
            try {
                if (javapMainClass != null) {
                    javapMainMethod = javapMainClass.getDeclaredMethod("main", String[].class);
                }
            } catch (Exception exception) {
                ProgramWarning.message("Cannot find or initialize javap: " + exception);
            }
        }
        return javapMainMethod;
    }

    public static boolean javap(String[] args) {
        try {
            javap().invoke(null, (Object) args);
            return true;
        } catch (InvocationTargetException e) {
            ProgramWarning.message("Error invoking javap: " + e.getTargetException());
        } catch (Exception e) {
            ProgramWarning.message("Error invoking javap: " + e);
        }
        return false;
    }
}
