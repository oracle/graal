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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import jdk.vm.ci.runtime.JVMCICompilerFactory;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.compiler.CompilerThreadFactory;
import com.oracle.graal.compiler.common.util.Util;
import com.oracle.graal.options.OptionDescriptor;
import com.oracle.graal.options.OptionDescriptors;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionsParser;
import com.oracle.graal.options.OptionsParser.OptionDescriptorsProvider;
import com.oracle.graal.test.SubprocessUtil;

/**
 * Test lazy initialization of Graal in the context of Truffle. When simply executing Truffle code,
 * Graal should not be initialized unless there is an actual compilation request.
 */
public class LazyInitializationTest {

    private final Class<?> hotSpotVMEventListener;
    private final Class<?> hotSpotGraalCompilerFactoryOptions;

    public LazyInitializationTest() {
        hotSpotVMEventListener = forNameOrNull("jdk.vm.ci.hotspot.HotSpotVMEventListener");
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

        int jvmciArg = args.indexOf("-jvmci");
        if (jvmciArg >= 0) {
            args.set(jvmciArg, "-server");
        }

        args.add("-XX:+TraceClassLoading");
        args.add("com.oracle.mxtool.junit.MxJUnitWrapper");
        args.addAll(Arrays.asList(tests));

        ArrayList<Class<?>> loadedGraalClasses = new ArrayList<>();

        Process process = new ProcessBuilder(args).start();

        if (VERBOSE) {
            System.out.println("-----------------------------------------------------------------------------");
            System.out.println(Util.join(args, " "));
        }
        int testCount = 0;
        BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = stdout.readLine()) != null) {
            if (VERBOSE) {
                System.out.println(line);
            }
            if (line.startsWith("[Loaded ")) {
                int start = "[Loaded ".length();
                int end = line.indexOf(' ', start);
                String loadedClass = line.substring(start, end);
                if (isGraalClass(loadedClass)) {
                    try {
                        loadedGraalClasses.add(Class.forName(loadedClass));
                    } catch (ClassNotFoundException e) {
                        Assert.fail("loaded class " + loadedClass + " not found");
                    }
                }
            } else if (line.startsWith("OK (")) {
                Assert.assertTrue(testCount == 0);
                int start = "OK (".length();
                int end = line.indexOf(' ', start);
                testCount = Integer.parseInt(line.substring(start, end));
            }
        }
        if (VERBOSE) {
            System.out.println("-----------------------------------------------------------------------------");
        }

        String suffix = VERBOSE ? "" : " (use -D" + VERBOSE_PROPERTY + "=true to debug)";
        Assert.assertNotEquals("test count" + suffix, 0, testCount);
        Assert.assertEquals("exit code" + suffix, 0, process.waitFor());

        checkAllowedGraalClasses(loadedGraalClasses, suffix);
    }

    private static boolean isGraalClass(String className) {
        if (className.startsWith("com.oracle.graal.truffle.")) {
            // Ignore classes in the com.oracle.graal.truffle package, they are all allowed.
            return false;
        } else {
            return className.startsWith("com.oracle.graal.");
        }
    }

    private void checkAllowedGraalClasses(List<Class<?>> loadedGraalClasses, String errorMessageSuffix) {
        HashSet<Class<?>> whitelist = new HashSet<>();

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

        for (Class<?> cls : loadedGraalClasses) {
            if (whitelist.contains(cls)) {
                continue;
            }

            if (!isGraalClassAllowed(cls)) {
                Assert.fail("loaded class: " + cls.getName() + errorMessageSuffix);
            }
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

        if (hotSpotVMEventListener != null && hotSpotVMEventListener.isAssignableFrom(cls)) {
            // HotSpotVMEventListeners need to be loaded on JVMCI startup.
            return true;
        }

        // No other class from the com.oracle.graal package should be loaded.
        return false;
    }
}
