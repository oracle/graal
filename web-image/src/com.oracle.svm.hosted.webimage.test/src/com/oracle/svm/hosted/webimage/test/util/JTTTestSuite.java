/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.test.util;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.oracle.svm.common.option.CommonOptionParser;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.hosted.webimage.NativeImageWasmGeneratorRunner;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsContainer;
import jdk.graal.compiler.test.AddExports;
import jdk.vm.ci.common.JVMCIError;

@AddExports({
                "jdk.graal.compiler/jdk.graal.compiler.options",
                "jdk.graal.compiler/jdk.graal.compiler.debug",
})
public abstract class JTTTestSuite {

    /*
     * Makes sure all the option descriptors are set so that we can use
     * SubstrateOptionsParser.commandArgument instead of hardcoding option names.
     *
     * The collectOptions implicitly sets the option descriptors for all OptionKeys by iterating
     * over them.
     */
    static {
        CommonOptionParser.collectOptions(OptionsContainer.getDiscoverableOptions(NativeImageWasmGeneratorRunner.class.getClassLoader()), (d) -> {
            assert d.getOptionKey().getDescriptor() == d;
        });
    }

    public static final String VM_IMAGE_NAME = "out";

    /**
     * Manages the test directory for a test suite.
     * <p>
     * The directory is only deleted once the class is done, so compilation artifacts can be reused.
     */
    @ClassRule //
    public static final TestDirectory TEST_DIRECTORY = new TestDirectory();

    public static Path getTestDir() {
        return TEST_DIRECTORY.getDirectory();
    }

    public static Path getTestCommand() {
        return getTestDir().resolve(VM_IMAGE_NAME + ".js");
    }

    /**
     * Watches for failed tests and dumps associated files so that they can later be used for
     * debugging.
     */
    @Rule //
    public final TestRule fileDumper = new TestWatcher() {
        @Override
        protected void failed(Throwable e, Description description) {
            super.failed(e, description);

            try {
                OptionValues options = new OptionValues(OptionValues.newOptionMap());
                Path dumpDir = Paths.get(DebugOptions.getDumpDirectory(options));
                Path dumpFile = dumpDir.resolve(ClassUtil.getUnqualifiedName(description.getTestClass()) + "." + description.getMethodName() + ".zip");

                Path testDir = getTestDir();

                Collection<Path> files = TEST_DIRECTORY.getFilesToDump();

                if (files == null) {
                    System.out.println();
                    System.out.println("Skipping dump to '" + dumpFile.toAbsolutePath() + "' files already present.");
                    return;
                }

                // If the test directory is empty, we don't create a zip file.
                if (files.isEmpty()) {
                    System.out.println();
                    System.out.println("No files found for dumping");
                    return;
                }

                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(dumpFile.toFile()))) {
                    zos.setLevel(Deflater.BEST_COMPRESSION);
                    files.forEach(path -> {
                        ZipEntry ze = new ZipEntry(testDir.getParent().relativize(path).toString());
                        try {
                            zos.putNextEntry(ze);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    });
                }

                System.out.println();
                System.out.println("Dumping test fail to " + dumpFile.toAbsolutePath());
            } catch (IOException exception) {
                throw GraalError.shouldNotReachHere(exception);
            }
        }
    };

    /**
     * Gets the commandline argument for setting the given hosted option to the given value.
     *
     * @param option The option to use
     * @param value The value for the option. Can be a {@link Boolean} for boolean options
     * @return The commandline argument for the given option key and value
     * @see SubstrateOptionsParser#commandArgument(OptionKey, String)
     */
    public static String oH(OptionKey<?> option, Object value) {
        String strValue;
        if (value instanceof Boolean bool) {
            strValue = bool ? "+" : "-";
        } else {
            strValue = String.valueOf(value);
        }

        return SubstrateOptionsParser.commandArgument(option, strValue);
    }

    /**
     * Run an already compiled JS file.
     *
     * @param args Arguments passed to the JS program
     * @return The {@link WebImageTestUtil.RunResult}
     */
    private static WebImageTestUtil.RunResult runJS(String[] args, int expectExitCode) {
        return WebImageTestUtil.runJS(getTestCommand().toString(), args, expectExitCode);
    }

    /**
     * Runs the currently compiled JS image and compares its result against the expected result.
     */
    private static void testFileAgainstNoBuild(int exitCode, WebImageTestUtil.RunResult expected, String... args) {
        expected.assertEquals(runJS(args, exitCode));
    }

    /**
     * Runs the currently compiled JS image and compares its output against the expected output.
     */
    protected static void testFileAgainstNoBuild(String[] expected, String... args) {
        testFileAgainstNoBuild(0, new WebImageTestUtil.RunResult(expected), args);
    }

    /**
     * Runs the currently compiled JS image and, asserts the given {@code exitCode} and checks its
     * output using the {@code lineChecker} consumer.
     */
    protected static void testFileAgainstNoBuild(int exitCode, Consumer<String[]> lineChecker, String... args) {
        testFileAgainstNoBuild(exitCode, new WebImageTestUtil.VirtualRunResult(lineChecker), args);
    }

    /**
     * Same as {@link #testFileAgainstNoBuild(int, Consumer, String...)}, but asserts a 0 exit code.
     */
    protected static void testFileAgainstNoBuild(Consumer<String[]> lineChecker, String... args) {
        testFileAgainstNoBuild(0, lineChecker, args);
    }

    /**
     * Runs the currently compiled JS image and compares its result against the given class run in a
     * Java runtime.
     */
    protected static void testClassNoBuild(Class<?> c, String... args) {
        testFileAgainstNoBuild(0, WebImageTestUtil.executeTestProgram(c, args, 0), args);
    }

    /**
     * Runs the currently compiled JS image and compares its result against the given class run in a
     * Java runtime.
     *
     * It also checks the expected exit code.
     */
    protected static void testClassNoBuildWithExitCode(int exitCode, Class<?> c, String... args) {
        testFileAgainstNoBuild(exitCode, WebImageTestUtil.executeTestProgram(c, args, exitCode), args);
    }

    /**
     * Runs the currently compiled JS image and compares its result against the given class run in a
     * Java runtime using the {@code lineChecker} consumer.
     */
    protected static void testClassNoBuild(Class<?> c, String[] args, BiConsumer<String[], String[]> lineChecker) {
        WebImageTestUtil.executeTestProgram(c, args, 0).assertEquals(runJS(args, 0), lineChecker);
    }

    /**
     * Runs the currently compiled JS image and compares its result against the given class run in a
     * Java runtime using the {@code lineChecker} consumer.
     *
     * It also checks the expected exit code.
     */
    protected static void testClassNoBuildWithExitCode(int exitCode, Class<?> c, String[] args, BiConsumer<String[], String[]> lineChecker) {
        WebImageTestUtil.executeTestProgram(c, args, exitCode).assertEquals(runJS(args, exitCode), lineChecker);
    }

    protected static void compileToJS(Class<?> clazz, String... args) {
        compileToJS(WebImageOptions.VMType.Generic, clazz, args);
    }

    /**
     * Compile the given class using Web Image.
     *
     * @param jsRuntime Target JS runtime
     * @param args Extra arguments passed to the compiler
     */
    protected static void compileToJS(WebImageOptions.VMType jsRuntime, Class<?> clazz, String... args) {
        System.out.printf("Testing %-50s ......%n", clazz.getCanonicalName());

        /*
         * Options have to be in a fixed order so that they start with
         * -H:+UnlockExperimentalVMOptions
         */
        List<Map.Entry<OptionKey<?>, Object>> hostedOptions = new ArrayList<>();
        hostedOptions.add(Map.entry(SubstrateOptions.UnlockExperimentalVMOptions, true));
        hostedOptions.add(Map.entry(SubstrateOptions.Name, VM_IMAGE_NAME));
        hostedOptions.add(Map.entry(SubstrateOptions.ConcealedOptions.Path, getTestDir()));
        hostedOptions.add(Map.entry(WebImageOptions.SILENT_COMPILE, true));
        hostedOptions.add(Map.entry(WebImageOptions.ClosureCompiler, WebImageTestOptions.USE_CLOSURE_COMPILER));
        hostedOptions.add(Map.entry(WebImageOptions.DumpPreClosure, true));
        hostedOptions.add(Map.entry(WebImageOptions.DebugOptions.VerificationPhases, true));
        hostedOptions.add(Map.entry(WebImageOptions.JSRuntime, jsRuntime.name()));
        hostedOptions.add(Map.entry(SubstrateOptions.Class, clazz.getCanonicalName()));
        hostedOptions.add(Map.entry(SubstrateOptions.GenerateDebugInfo, 2));

        List<String> opts = new ArrayList<>();

        for (Map.Entry<OptionKey<?>, Object> entry : hostedOptions) {
            opts.add(oH(entry.getKey(), entry.getValue()));
        }

        opts.addAll(WebImageTestOptions.LAUNCHER_FLAGS);

        // Enables assertions in the driver
        opts.add("--vm.ea");
        opts.add("--vm.esa");

        // Enables assertions in the JVM running the builder
        opts.add("-J-ea");
        opts.add("-J-esa");

        // Enables assertions in the compiled image
        opts.add("-ea");
        opts.add("-esa");

        if (args != null) {
            Collections.addAll(opts, args);
        }

        opts.addAll(WebImageTestOptions.ADDITIONAL_OPTIONS);

        runWebImage(opts.toArray(new String[0]));
    }

    private static void runWebImage(String[] args) {
        List<String> cmd = new ArrayList<>(args.length + 1);
        cmd.add(WebImageTestOptions.getLauncher());
        cmd.addAll(Arrays.asList(args));

        System.out.println(cmd);

        try {
            int exitCode = new ProcessBuilder(cmd).inheritIO().start().waitFor();
            Assert.assertEquals("Compiling the test image failed", 0, exitCode);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected static Path writeReflectionConfig(String content) {
        Path reflectConfigFile = getTestDir().resolve("testReflectConfig.json");
        try (PrintWriter writer = new PrintWriter(reflectConfigFile.toFile())) {
            writer.println(content);
        } catch (FileNotFoundException e) {
            JVMCIError.shouldNotReachHere(e);
        }

        return reflectConfigFile;
    }
}
