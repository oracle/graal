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

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.replacements.*;

/**
 * This class implements compile-the-world functionality in Graal.
 */
public final class CompileTheWorld {

    /**
     * This is our magic token to trigger reading files from the boot class path.
     */
    public static final String SUN_BOOT_CLASS_PATH = "sun.boot.class.path";

    // Some runtime instances we need.
    private final HotSpotGraalRuntime runtime = runtime();
    private final VMToCompilerImpl vmToCompiler = (VMToCompilerImpl) runtime.getVMToCompiler();

    /** List of Zip/Jar files to compile (see {@link GraalOptions#CompileTheWorld}. */
    private final String files;

    /** Class index to start compilation at (see {@link GraalOptions#CompileTheWorldStartAt}. */
    private final int startAt;

    /** Class index to stop compilation at (see {@link GraalOptions#CompileTheWorldStopAt}. */
    private final int stopAt;

    // Counters
    private int classFileCounter = 0;
    private int compiledMethodsCounter = 0;
    private long compileTime = 0;

    /**
     * Create a compile-the-world instance with default values from
     * {@link GraalOptions#CompileTheWorld}, {@link GraalOptions#CompileTheWorldStartAt} and
     * {@link GraalOptions#CompileTheWorldStopAt}.
     */
    public CompileTheWorld() {
        this(CompileTheWorld.getValue(), CompileTheWorldStartAt.getValue(), CompileTheWorldStopAt.getValue());
    }

    /**
     * Create a compile-the-world instance.
     * 
     * @param files {@link File#pathSeparator} separated list of Zip/Jar files to compile
     * @param startAt index of the class file to start compilation at
     * @param stopAt index of the class file to stop compilation at
     */
    public CompileTheWorld(String files, int startAt, int stopAt) {
        this.files = files;
        this.startAt = startAt;
        this.stopAt = stopAt;

        // We don't want the VM to exit when a method fails to compile...
        ExitVMOnException.setValue(false);

        // ...but we want to see exceptions.
        PrintBailout.setValue(true);
        PrintStackTraceOnException.setValue(true);
    }

    /**
     * Compile all methods in all classes in the Zip/Jar files in
     * {@link GraalOptions#CompileTheWorld}. If the GraalOptions.CompileTheWorld contains the magic
     * token {@link CompileTheWorld#SUN_BOOT_CLASS_PATH} passed up from HotSpot we take the files
     * from the boot class path.
     * 
     * @throws Throwable
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

    public static void println() {
        println("");
    }

    public static void println(String format, Object... args) {
        println(String.format(format, args));
    }

    public static final boolean LOG = Boolean.getBoolean("graal.compileTheWorldTest.log");

    public static void println(String s) {
        if (LOG) {
            TTY.println(s);
        }
    }

    /**
     * Compile all methods in all classes in the Zip/Jar files passed.
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

                try {
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
     * Helper method to schedule a method for compilation and gather some statistics.
     */
    private void compileMethod(HotSpotResolvedJavaMethod method) {
        try {
            long start = System.currentTimeMillis();
            vmToCompiler.compileMethod(method, StructuredGraph.INVOCATION_ENTRY_BCI, true);
            compileTime += (System.currentTimeMillis() - start);
            compiledMethodsCounter++;
            method.reprofile();  // makes the method also not-entrant
        } catch (Throwable t) {
            // Catch everything and print a message
            println("CompileTheWorld (%d) : Error compiling method: %s", classFileCounter, MetaUtil.format("%H.%n(%p):%r", method));
            t.printStackTrace(TTY.cachedOut);
        }
    }

    /**
     * Helper method for CompileTheWorld to determine if a method should be compiled (Cf.
     * CompilationPolicy::can_be_compiled).
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
