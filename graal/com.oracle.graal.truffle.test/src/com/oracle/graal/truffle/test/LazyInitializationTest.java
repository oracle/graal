/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.graal.api.runtime.GraalRuntime;
import com.oracle.graal.compiler.CompilerThreadFactory;
import com.oracle.graal.compiler.common.spi.CodeGenProviders;
import com.oracle.graal.compiler.common.util.Util;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.debug.DebugConfig;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.framemap.FrameMap;
import com.oracle.graal.options.OptionDescriptor;
import com.oracle.graal.options.OptionDescriptors;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionsParser;
import com.oracle.graal.options.OptionsParser.OptionDescriptorsProvider;
import com.oracle.graal.phases.tiers.CompilerConfiguration;
import com.oracle.graal.phases.tiers.TargetProvider;
import com.oracle.graal.runtime.RuntimeProvider;
import com.oracle.graal.test.SubprocessUtil;

import jdk.vm.ci.runtime.services.JVMCICompilerFactory;

/**
 * Test lazy initialization of Graal in the context of Truffle. When simply executing Truffle code,
 * Graal should not be initialized unless there is an actual compilation request.
 */
public class LazyInitializationTest {

    private final Class<?> hotSpotVMEventListener;
    private final Class<?> hotSpotGraalCompilerFactoryOptions;

    private static boolean Java8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    public LazyInitializationTest() {
        hotSpotVMEventListener = forNameOrNull("jdk.vm.ci.hotspot.services.HotSpotVMEventListener");
        hotSpotGraalCompilerFactoryOptions = forNameOrNull("com.oracle.graal.hotspot.HotSpotGraalCompilerFactory$Options");
    }

    private static Class<?> forNameOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Test
    public void testSLTck() throws IOException, InterruptedException {
        spawnUnitTests("com.oracle.truffle.sl.test.SLTckTest");
    }

    private static final String VERBOSE_PROPERTY = "LazyInitializationTest.verbose";
    private static final boolean VERBOSE = Boolean.getBoolean(VERBOSE_PROPERTY);

    /**
     * Spawn a new VM, execute unit tests, and check which classes are loaded.
     */
    private void spawnUnitTests(String... tests) throws IOException, InterruptedException {
        ArrayList<String> args = new ArrayList<>(SubprocessUtil.getVMCommandLine());

        // Remove debugger arguments
        for (Iterator<String> i = args.iterator(); i.hasNext();) {
            String arg = i.next();
            if (arg.equals("-Xdebug") || arg.startsWith("-Xrunjdwp:")) {
                i.remove();
            }
        }

        boolean usesJvmciCompiler = args.contains("-jvmci") || args.contains("-XX:+UseJVMCICompiler");
        Assume.assumeFalse("This test can only run if JVMCI is not one of the default compilers", usesJvmciCompiler);

        args.add(Java8OrEarlier ? "-XX:+TraceClassLoading" : "-Xlog:class+load=info");
        args.add("com.oracle.mxtool.junit.MxJUnitWrapper");
        args.addAll(Arrays.asList(tests));

        ArrayList<String> loadedGraalClassNames = new ArrayList<>();

        ProcessBuilder processBuilder = new ProcessBuilder(args);
        if (VERBOSE) {
            processBuilder.redirectError(Redirect.INHERIT);
        }

        if (VERBOSE) {
            System.out.println("\n=============================================================================");
            System.out.println(Util.join(args, " "));
            System.out.println("-----------------------------------------------------------------------------");
        }
        Process process = processBuilder.start();
        int testCount = 0;
        BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = stdout.readLine()) != null) {
            if (VERBOSE) {
                System.out.println(line);
            }
            String traceClassLoadingPrefix = Java8OrEarlier ? "[Loaded " : "[info][class,load] ";
            int index = line.indexOf(traceClassLoadingPrefix);
            if (index != -1) {
                int start = index + traceClassLoadingPrefix.length();
                int end = line.indexOf(' ', start);
                String loadedClass = line.substring(start, end);
                if (isGraalClass(loadedClass)) {
                    loadedGraalClassNames.add(loadedClass);
                }
            } else if (line.startsWith("OK (")) {
                Assert.assertTrue(testCount == 0);
                int start = "OK (".length();
                int end = line.indexOf(' ', start);
                testCount = Integer.parseInt(line.substring(start, end));
            }
        }
        if (VERBOSE) {
            System.out.println("=============================================================================");
        }

        String suffix = VERBOSE ? "" : " (use -D" + VERBOSE_PROPERTY + "=true to debug)";
        Assert.assertEquals("exit code" + suffix, 0, process.waitFor());
        Assert.assertNotEquals("test count" + suffix, 0, testCount);

        checkAllowedGraalClasses(loadedGraalClassNames, suffix);
    }

    private static boolean isGraalClass(String className) {
        if (className.startsWith("com.oracle.graal.truffle.")) {
            // Ignore classes in the com.oracle.graal.truffle package, they are all allowed.
            return false;
        } else {
            return className.startsWith("com.oracle.graal.");
        }
    }

    private void checkAllowedGraalClasses(List<String> loadedGraalClassNames, String errorMessageSuffix) {
        HashSet<Class<?>> whitelist = new HashSet<>();
        List<Class<?>> loadedGraalClasses = new ArrayList<>();

        for (String name : loadedGraalClassNames) {
            try {
                loadedGraalClasses.add(Class.forName(name));
            } catch (ClassNotFoundException e) {
                Assert.fail("loaded class " + name + " not found");
            }
        }
        /*
         * Look for all loaded OptionDescriptors classes, and whitelist the classes that declare the
         * options. They may be loaded by the option parsing code.
         */
        for (Class<?> cls : loadedGraalClasses) {
            if (OptionDescriptors.class.isAssignableFrom(cls)) {
                try {
                    OptionDescriptors optionDescriptors = cls.asSubclass(OptionDescriptors.class).newInstance();
                    for (OptionDescriptor option : optionDescriptors) {
                        whitelist.add(option.getDeclaringClass());
                    }
                } catch (ReflectiveOperationException e) {
                }
            }
        }

        List<String> forbiddenClasses = new ArrayList<>();
        for (Class<?> cls : loadedGraalClasses) {
            if (whitelist.contains(cls)) {
                continue;
            }

            if (!isGraalClassAllowed(cls)) {
                forbiddenClasses.add(cls.getName());
            }
        }
        if (!forbiddenClasses.isEmpty()) {
            Assert.fail("loaded forbidden classes:\n    " + forbiddenClasses.stream().collect(Collectors.joining("\n    ")) + "\n" + errorMessageSuffix);
        }
    }

    private boolean isGraalClassAllowed(Class<?> cls) {
        if (CompilerThreadFactory.class.equals(cls) || CompilerThreadFactory.DebugConfigAccess.class.equals(cls)) {
            // The HotSpotTruffleRuntime creates a CompilerThreadFactory for Truffle.
            return true;
        }

        if (cls.equals(hotSpotGraalCompilerFactoryOptions)) {
            // The JVMCI initialization code needs to accesses an option defined in this class.
            return true;
        }

        if (JVMCICompilerFactory.class.isAssignableFrom(cls)) {
            // The compiler factories have to be loaded and instantiated by the JVMCI.
            return true;
        }

        if (OptionDescriptors.class.isAssignableFrom(cls) || OptionDescriptor.class.isAssignableFrom(cls)) {
            // If options are specified, the corresponding *_OptionDescriptors classes are loaded.
            return true;
        }

        if (OptionDescriptorsProvider.class.isAssignableFrom(cls) || cls == OptionsParser.class) {
            // Classes implementing Graal option loading
            return true;
        }

        if (OptionValue.class.isAssignableFrom(cls)) {
            // If options are specified, that may implicitly load a custom OptionValue subclass.
            return true;
        }

        if (OptionValue.OverrideScope.class.isAssignableFrom(cls)) {
            // Reading options can check override scopes
            return true;
        }

        if (hotSpotVMEventListener != null && hotSpotVMEventListener.isAssignableFrom(cls)) {
            // HotSpotVMEventListeners need to be loaded on JVMCI startup.
            return true;
        }

        if (!Java8OrEarlier) {
            // In JDK9 without the JVMCI class loader, Graal classes are considered "remote"
            // and are thus verified which in turn means extra class resolving is done
            // during verification. Below are the list of extra classes resolved during
            // verification of the above Graal classes.
            if (cls.equals(GraalError.class) ||
                            Backend.class.isAssignableFrom(cls) ||
                            CodeGenProviders.class.isAssignableFrom(cls) ||
                            RuntimeProvider.class.isAssignableFrom(cls) ||
                            Thread.class.isAssignableFrom(cls) ||
                            cls.equals(FrameMap.ReferenceMapBuilderFactory.class) ||
                            cls.equals(GraalRuntime.class) ||
                            cls.equals(DebugConfig.class) ||
                            cls.equals(CompilationResultBuilderFactory.class) ||
                            cls.equals(CompilerConfiguration.class) ||
                            cls.equals(TargetProvider.class)) {
                return true;
            }
        }

        // No other class from the com.oracle.graal package should be loaded.
        return false;
    }
}
