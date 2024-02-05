/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.truffle.test;

import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.CompileImmediately;
import static jdk.graal.compiler.test.SubprocessUtil.getVMCommandLine;
import static jdk.graal.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jdk.graal.compiler.core.CompilerThreadFactory;
import jdk.graal.compiler.core.GraalCompilerOptions;
import jdk.graal.compiler.core.common.util.Util;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.hotspot.CommunityCompilerConfigurationFactory;
import jdk.graal.compiler.hotspot.CompilerConfigurationFactory;
import jdk.graal.compiler.hotspot.EconomyCompilerConfigurationFactory;
import jdk.graal.compiler.hotspot.HotSpotGraalVMEventListener;
import jdk.graal.compiler.nodes.Cancellable;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.graal.compiler.test.SubprocessUtil.Subprocess;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.vm.ci.runtime.JVMCICompilerFactory;
import jdk.vm.ci.services.JVMCIServiceLocator;

/**
 * Test lazy initialization of Graal in the context of Truffle. When simply executing Truffle code,
 * Graal should not be initialized unless there is an actual compilation request.
 */
public class LazyClassLoadingTest extends TestWithPolyglotOptions {

    private final Class<?> hotSpotVMEventListener;
    private final Class<?> hotSpotGraalCompilerFactoryOptions;
    private final Class<?> hotSpotGraalJVMCIServiceLocatorShared;
    private final Class<?> jvmciVersionCheck;

    public LazyClassLoadingTest() {
        hotSpotVMEventListener = forNameOrNull("jdk.vm.ci.hotspot.services.HotSpotVMEventListener");
        hotSpotGraalCompilerFactoryOptions = forNameOrNull("jdk.graal.compiler.hotspot.HotSpotGraalCompilerFactory$Options");
        hotSpotGraalJVMCIServiceLocatorShared = forNameOrNull("jdk.graal.compiler.hotspot.HotSpotGraalJVMCIServiceLocator$Shared");
        jvmciVersionCheck = forNameOrNull("jdk.graal.compiler.hotspot.JVMCIVersionCheck");
    }

    private static Class<?> forNameOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Test
    public void testClassLoading() throws IOException, InterruptedException {
        setupContext();
        OptimizedCallTarget target = (OptimizedCallTarget) RootNode.createConstantNode(0).getCallTarget();
        Assume.assumeFalse(target.getOptionValue(CompileImmediately));
        List<String> vmCommandLine = getVMCommandLine();
        Assume.assumeFalse("Explicitly enables JVMCI compiler", vmCommandLine.contains("-XX:+UseJVMCINativeLibrary") || vmCommandLine.contains("-XX:+UseJVMCICompiler"));
        runTest(LazyClassLoadingTargetNegativeTest.class, false);
        runTest(LazyClassLoadingTargetPositiveTest.class, true);
    }

    private void runTest(Class<?> testClass, boolean expectGraalClassesLoaded) throws IOException, InterruptedException, AssertionError {
        List<String> vmCommandLine = getVMCommandLine();
        List<String> vmArgs = withoutDebuggerArguments(vmCommandLine);
        vmArgs.add("-Xlog:class+init=info");
        vmArgs.add(SubprocessUtil.PACKAGE_OPENING_OPTIONS);
        vmArgs.add("-dsa");
        vmArgs.add("-da");
        vmArgs.add("-Dpolyglot.engine.AssertProbes=false");
        vmArgs.add("-Dpolyglot.engine.AllowExperimentalOptions=true");
        vmArgs.add("-XX:-UseJVMCICompiler");

        // Remove -Djdk.graal.CompilationFailureAction as it drags in CompilationWrapper
        vmArgs = vmArgs.stream().filter(e -> !e.contains(GraalCompilerOptions.CompilationFailureAction.getName())).collect(Collectors.toList());

        Subprocess proc = SubprocessUtil.java(vmArgs, "com.oracle.mxtool.junit.MxJUnitWrapper", testClass.getName());
        int exitCode = proc.exitCode;
        if (exitCode != 0) {
            Assert.fail(String.format("non-zero exit code %d for command:%n%s", exitCode, proc));
        }

        ArrayList<String> loadedGraalClassNames = new ArrayList<>();
        int testCount = 0;
        for (String line : proc.output) {
            String loadedClass = extractClass(line);
            if (loadedClass != null) {
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
        if (testCount == 0) {
            Assert.fail(String.format("no tests found in output of command:%n%s", testCount, proc));
        }

        try {
            List<String> graalClasses = filterGraalCompilerClasses(loadedGraalClassNames);
            if (expectGraalClassesLoaded) {
                if (graalClasses.isEmpty()) {
                    Assert.fail(String.format("Failure for command:%n%s", proc) + ". Expected Graal classes loaded but weren't.");
                }
            } else {
                if (!graalClasses.isEmpty()) {
                    Assert.fail(String.format("Failure for command:%n%s", proc) + ". Loaded forbidden classes:\n    " + graalClasses.stream().collect(Collectors.joining("\n    ")) + "\n");
                }
            }
        } catch (AssertionError e) {
            throw new AssertionError(String.format("Failure for command:%n%s", proc), e);
        }
    }

    private static final Pattern CLASS_INIT_LOG_PATTERN = Pattern.compile("\\[info\\]\\[class,init\\] \\d+ Initializing '([^']+)'");

    /**
     * Extracts the class name from a line of log output.
     */
    private static String extractClass(String line) {
        Matcher matcher = CLASS_INIT_LOG_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.group(1).replace('/', '.');
        }
        return null;
    }

    private static boolean isGraalClass(String className) {
        if (className.startsWith("jdk.graal.compiler.truffle.") || className.startsWith("jdk.graal.compiler.serviceprovider.")) {
            // Ignore classes in the com.oracle.graal.truffle package, they are all allowed.
            // Also ignore classes in the Graal service provider package, as they might not be
            // lazily loaded.
            return false;
        } else {
            return className.startsWith("jdk.graal.compiler.");
        }
    }

    private List<String> filterGraalCompilerClasses(List<String> loadedGraalClassNames) {
        HashSet<Class<?>> allowList = new HashSet<>();
        List<Class<?>> loadedGraalClasses = new ArrayList<>();

        for (String name : loadedGraalClassNames) {
            try {
                loadedGraalClasses.add(Class.forName(name));
            } catch (ClassNotFoundException e) {
                if (e.getMessage().contains("$$Lambda")) {
                    // lambdas may not be found.
                    continue;
                }
                Assert.fail("loaded class " + name + " not found");
            }
        }
        /*
         * Look for all loaded OptionDescriptors classes, and allow the classes that declare the
         * options. They may be loaded by the option parsing code.
         */
        for (Class<?> cls : loadedGraalClasses) {
            if (OptionDescriptors.class.isAssignableFrom(cls)) {
                try {
                    OptionDescriptors optionDescriptors = cls.asSubclass(OptionDescriptors.class).getDeclaredConstructor().newInstance();
                    for (OptionDescriptor option : optionDescriptors) {
                        allowList.add(option.getDeclaringClass());
                        allowList.add(option.getOptionValueType());
                        allowList.add(option.getOptionType().getDeclaringClass());
                    }
                } catch (ReflectiveOperationException e) {
                }
            }
        }

        // classes needed to find out whether enterprise is installed in the JDK
        allowList.add(OptionStability.class);
        allowList.add(CompilerConfigurationFactory.class);
        allowList.add(CompilerConfigurationFactory.Options.class);
        allowList.add(CompilerConfigurationFactory.ShowConfigurationLevel.class);
        allowList.add(EconomyCompilerConfigurationFactory.class);
        allowList.add(CommunityCompilerConfigurationFactory.class);

        allowList.add(Cancellable.class);

        List<String> forbiddenClasses = new ArrayList<>();
        for (Class<?> cls : loadedGraalClasses) {
            if (allowList.contains(cls)) {
                continue;
            }

            if (!isGraalClassAllowed(cls)) {
                forbiddenClasses.add(cls.getName());
            }
        }
        return forbiddenClasses;
    }

    private boolean isGraalClassAllowed(Class<?> cls) {
        if (CompilerThreadFactory.class.equals(cls)) {
            // The HotSpotTruffleRuntime creates a CompilerThreadFactory for Truffle.
            return true;
        }

        if (cls.equals(hotSpotGraalCompilerFactoryOptions)) {
            // Graal initialization needs to access this class.
            return true;
        }

        if (cls.equals(Util.class)) {
            // Provider of the Java runtime check utility used during Graal initialization.
            return true;
        }

        if (cls.equals(jvmciVersionCheck) || Objects.equals(cls.getEnclosingClass(), jvmciVersionCheck)) {
            // The Graal initialization needs to check the JVMCI version.
            return true;
        }

        if (JVMCICompilerFactory.class.isAssignableFrom(cls) || cls.getName().startsWith("jdk.graal.compiler.hotspot.IsGraalPredicate")) {
            // The compiler factories have to be loaded and instantiated by the JVMCI.
            return true;
        }

        if (JVMCIServiceLocator.class.isAssignableFrom(cls) || cls == hotSpotGraalJVMCIServiceLocatorShared || HotSpotGraalVMEventListener.class.isAssignableFrom(cls)) {
            return true;
        }

        if (OptionDescriptors.class.isAssignableFrom(cls) || OptionDescriptor.class.isAssignableFrom(cls)) {
            // If options are specified, the corresponding *_OptionDescriptors classes are loaded.
            return true;
        }

        if (cls == Assertions.class || cls == OptionsParser.class || cls == OptionValues.class ||
                        cls.getName().equals("jdk.graal.compiler.hotspot.HotSpotGraalOptionValues")) {
            // Classes implementing Graal option loading
            return true;
        }

        if (OptionKey.class.isAssignableFrom(cls)) {
            // If options are specified, that may implicitly load a custom OptionKey subclass.
            return true;
        }

        if (cls.getName().equals("jdk.graal.compiler.hotspot.HotSpotGraalMBean")) {
            // MBean is OK and fast
            return true;
        }

        if (hotSpotVMEventListener != null && hotSpotVMEventListener.isAssignableFrom(cls)) {
            // HotSpotVMEventListeners need to be loaded on JVMCI startup.
            return true;
        }

        // No other class from the jdk.graal.compiler. package should be loaded.
        return false;
    }
}
