/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.hotspot.CompileTheWorld.Options.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.nodes.StructuredGraph.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.HotSpotOptions.OptionConsumer;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.options.*;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.replacements.*;

/**
 * This class implements compile-the-world functionality in Graal.
 */
public final class CompileTheWorld {

    /**
     * Magic token to trigger reading files from the boot class path.
     */
    public static final String SUN_BOOT_CLASS_PATH = "sun.boot.class.path";

    public static class Options {
        // @formatter:off
        @Option(help = "Compile all methods in all classes on given class path")
        public static final OptionValue<String> CompileTheWorldClasspath = new OptionValue<>(SUN_BOOT_CLASS_PATH);
        @Option(help = "Verbose CompileTheWorld operation")
        public static final OptionValue<Boolean> CompileTheWorldVerbose = new OptionValue<>(true);
        @Option(help = "The number of CompileTheWorld iterations to perform")
        public static final OptionValue<Integer> CompileTheWorldIterations = new OptionValue<>(1);
        @Option(help = "First class to consider when using -XX:+CompileTheWorld")
        public static final OptionValue<Integer> CompileTheWorldStartAt = new OptionValue<>(1);
        @Option(help = "Last class to consider when using -XX:+CompileTheWorld")
        public static final OptionValue<Integer> CompileTheWorldStopAt = new OptionValue<>(Integer.MAX_VALUE);
        @Option(help = "Option value overrides to use during compile the world. For example, " +
                       "to disable inlining and partial escape analysis specify '-PartialEscapeAnalysis -Inline'. " +
                       "The format for each option is the same as on the command line just without the '-G:' prefix.")
        public static final OptionValue<String> CompileTheWorldConfig = new OptionValue<>(null);
        // @formatter:on

        /**
         * Overrides {@link #CompileTheWorldStartAt} and {@link #CompileTheWorldStopAt} from
         * {@code -XX} HotSpot options of the same name if the latter have non-default values.
         */
        static void overrideWithNativeOptions(HotSpotVMConfig c) {
            if (c.compileTheWorldStartAt != 1) {
                CompileTheWorldStartAt.setValue(c.compileTheWorldStartAt);
            }
            if (c.compileTheWorldStopAt != Integer.MAX_VALUE) {
                CompileTheWorldStopAt.setValue(c.compileTheWorldStopAt);
            }
        }
    }

    /**
     * A mechanism for overriding Graal options that effect compilation. A {@link Config} object
     * should be used in a try-with-resources statement to ensure overriding of options is scoped
     * properly. For example:
     * 
     * <pre>
     *     Config config = ...;
     *     try (AutoCloseable s = config == null ? null : config.apply()) {
     *         // perform a Graal compilation
     *     }
     * </pre>
     */
    @SuppressWarnings("serial")
    public static class Config extends HashMap<OptionValue<?>, Object> implements AutoCloseable, OptionConsumer {
        OverrideScope scope;

        /**
         * Creates a {@link Config} object by parsing a set of space separated override options.
         * 
         * @param options a space separated set of option value settings with each option setting in
         *            a format compatible with
         *            {@link HotSpotOptions#parseOption(String, OptionConsumer)}
         */
        public Config(String options) {
            for (String option : options.split("\\s+")) {
                if (!HotSpotOptions.parseOption(option, this)) {
                    throw new GraalInternalError("Invalid option specified: %s", option);
                }
            }
        }

        /**
         * Applies the overrides represented by this object. The overrides are in effect until
         * {@link #close()} is called on this object.
         */
        Config apply() {
            assert scope == null;
            scope = OptionValue.override(this);
            return this;
        }

        public void close() {
            assert scope != null;
            scope.close();

            scope = null;

        }

        public void set(OptionDescriptor desc, Object value) {
            put(desc.getOptionValue(), value);
        }

        public static Config parse(String input) {
            if (input == null) {
                return null;
            } else {
                return new Config(input);
            }
        }
    }

    // Some runtime instances we need.
    private final HotSpotGraalRuntime runtime = runtime();
    private final VMToCompilerImpl vmToCompiler = (VMToCompilerImpl) runtime.getVMToCompiler();

    /** List of Zip/Jar files to compile (see {@link #CompileTheWorldClasspath}. */
    private final String files;

    /** Class index to start compilation at (see {@link #CompileTheWorldStartAt}. */
    private final int startAt;

    /** Class index to stop compilation at (see {@link #CompileTheWorldStopAt}. */
    private final int stopAt;

    // Counters
    private int classFileCounter = 0;
    private int compiledMethodsCounter = 0;
    private long compileTime = 0;

    private boolean verbose;
    private final Config config;

    /**
     * Creates a compile-the-world instance.
     * 
     * @param files {@link File#pathSeparator} separated list of Zip/Jar files to compile
     * @param startAt index of the class file to start compilation at
     * @param stopAt index of the class file to stop compilation at
     */
    public CompileTheWorld(String files, Config config, int startAt, int stopAt, boolean verbose) {
        this.files = files;
        this.startAt = startAt;
        this.stopAt = stopAt;
        this.verbose = verbose;
        this.config = config;

        // We don't want the VM to exit when a method fails to compile...
        ExitVMOnException.setValue(false);

        // ...but we want to see exceptions.
        PrintBailout.setValue(true);
        PrintStackTraceOnException.setValue(true);
    }

    /**
     * Compiles all methods in all classes in the Zip/Jar archive files in
     * {@link #CompileTheWorldClasspath}. If {@link #CompileTheWorldClasspath} contains the magic
     * token {@link #SUN_BOOT_CLASS_PATH} passed up from HotSpot we take the files from the boot
     * class path.
     */
    public void compile() throws Throwable {
        if (SUN_BOOT_CLASS_PATH.equals(files)) {
            final String[] entries = System.getProperty(SUN_BOOT_CLASS_PATH).split(File.pathSeparator);
            String bcpFiles = "";
            for (int i = 0; i < entries.length; i++) {
                final String entry = entries[i];

                // We stop at rt.jar, unless it is the first boot class path entry.
                if (entry.endsWith("rt.jar") && (i > 0)) {
                    break;
                }
                if (i > 0) {
                    bcpFiles += File.pathSeparator;
                }
                bcpFiles += entry;
            }
            compile(bcpFiles);
        } else {
            compile(files);
        }
    }

    public void println() {
        println("");
    }

    public void println(String format, Object... args) {
        println(String.format(format, args));
    }

    public void println(String s) {
        if (verbose) {
            TTY.println(s);
        }
    }

    /**
     * Compiles all methods in all classes in the Zip/Jar files passed.
     * 
     * @param fileList {@link File#pathSeparator} separated list of Zip/Jar files to compile
     * @throws Throwable
     */
    private void compile(String fileList) throws Throwable {
        final String[] entries = fileList.split(File.pathSeparator);

        for (int i = 0; i < entries.length; i++) {
            final String entry = entries[i];

            // For now we only compile all methods in all classes in zip/jar files.
            if (!entry.endsWith(".zip") && !entry.endsWith(".jar")) {
                println("CompileTheWorld : Skipped classes in " + entry);
                println();
                continue;
            }

            println("CompileTheWorld : Compiling all classes in " + entry);
            println();

            URL url = new URL("jar", "", "file:" + entry + "!/");
            ClassLoader loader = new URLClassLoader(new URL[]{url});

            JarFile jarFile = new JarFile(entry);
            Enumeration<JarEntry> e = jarFile.entries();

            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (je.isDirectory() || !je.getName().endsWith(".class")) {
                    continue;
                }

                // Are we done?
                if (classFileCounter >= stopAt) {
                    break;
                }

                String className = je.getName().substring(0, je.getName().length() - ".class".length());
                classFileCounter++;

                try (AutoCloseable s = config == null ? null : config.apply()) {
                    // Load and initialize class
                    Class<?> javaClass = Class.forName(className.replace('/', '.'), true, loader);

                    // Pre-load all classes in the constant pool.
                    try {
                        HotSpotResolvedObjectType objectType = (HotSpotResolvedObjectType) HotSpotResolvedObjectType.fromClass(javaClass);
                        ConstantPool constantPool = objectType.constantPool();
                        for (int cpi = 1; cpi < constantPool.length(); cpi++) {
                            constantPool.loadReferencedType(cpi, Bytecodes.LDC);
                        }
                    } catch (Throwable t) {
                        // If something went wrong during pre-loading we just ignore it.
                        println("Preloading failed for (%d) %s", classFileCounter, className);
                    }

                    // Are we compiling this class?
                    HotSpotMetaAccessProvider metaAccess = runtime.getHostProviders().getMetaAccess();
                    if (classFileCounter >= startAt) {
                        println("CompileTheWorld (%d) : %s", classFileCounter, className);

                        // Enqueue each constructor/method in the class for compilation.
                        for (Constructor<?> constructor : javaClass.getDeclaredConstructors()) {
                            HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaConstructor(constructor);
                            if (canBeCompiled(javaMethod, constructor.getModifiers())) {
                                compileMethod(javaMethod);
                            }
                        }
                        for (Method method : javaClass.getDeclaredMethods()) {
                            HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaMethod(method);
                            if (canBeCompiled(javaMethod, method.getModifiers())) {
                                compileMethod(javaMethod);
                            }
                        }
                    }
                } catch (Throwable t) {
                    println("CompileTheWorld (%d) : Skipping %s", classFileCounter, className);
                }
            }
            jarFile.close();
        }

        println();
        println("CompileTheWorld : Done (%d classes, %d methods, %d ms)", classFileCounter, compiledMethodsCounter, compileTime);
    }

    /**
     * A compilation task that creates a fresh compilation suite for its compilation. This is
     * required so that a CTW compilation can be {@linkplain Config configured} differently from a
     * VM triggered compilation.
     */
    static class CTWCompilationTask extends CompilationTask {

        CTWCompilationTask(HotSpotBackend backend, PhasePlan plan, OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, HotSpotResolvedJavaMethod method, int entryBCI, int id) {
            super(backend, plan, optimisticOpts, profilingInfo, method, entryBCI, id);
        }

        @Override
        protected Suites getSuites(HotSpotProviders providers) {
            return providers.getSuites().createSuites();
        }
    }

    /**
     * Compiles a method and gathers some statistics.
     */
    private void compileMethod(HotSpotResolvedJavaMethod method) {
        try {
            long start = System.currentTimeMillis();

            final ProfilingInfo profilingInfo = method.getCompilationProfilingInfo(false);
            final OptimisticOptimizations optimisticOpts = new OptimisticOptimizations(profilingInfo);
            int id = vmToCompiler.allocateCompileTaskId();
            HotSpotBackend backend = runtime.getHostBackend();
            PhasePlan phasePlan = vmToCompiler.createPhasePlan(backend.getProviders(), optimisticOpts, false);
            CompilationTask task = new CTWCompilationTask(backend, phasePlan, optimisticOpts, profilingInfo, method, INVOCATION_ENTRY_BCI, id);
            task.runCompilation();

            compileTime += (System.currentTimeMillis() - start);
            compiledMethodsCounter++;
            method.reprofile();  // makes the method also not-entrant
        } catch (Throwable t) {
            // Catch everything and print a message
            println("CompileTheWorldClasspath (%d) : Error compiling method: %s", classFileCounter, MetaUtil.format("%H.%n(%p):%r", method));
            t.printStackTrace(TTY.cachedOut);
        }
    }

    /**
     * Determines if a method should be compiled (Cf. CompilationPolicy::can_be_compiled).
     * 
     * @return true if it can be compiled, false otherwise
     */
    private static boolean canBeCompiled(HotSpotResolvedJavaMethod javaMethod, int modifiers) {
        if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
            return false;
        }
        // This number is from HotSpot:
        final int hugeMethodLimit = 8000;
        if (javaMethod.getCodeSize() > hugeMethodLimit) {
            return false;
        }
        // Skip @Snippets for now
        if (javaMethod.getAnnotation(Snippet.class) != null) {
            return false;
        }
        return true;
    }

}
