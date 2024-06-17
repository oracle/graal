/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import static jdk.graal.compiler.debug.StandardPathUtilitiesProvider.DIAGNOSTIC_OUTPUT_DIRECTORY_MESSAGE_FORMAT;
import static jdk.graal.compiler.debug.StandardPathUtilitiesProvider.DIAGNOSTIC_OUTPUT_DIRECTORY_MESSAGE_REGEXP;
import static jdk.graal.compiler.test.SubprocessUtil.getVMCommandLine;
import static jdk.graal.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.core.GraalCompilerOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.graal.compiler.test.SubprocessUtil.Subprocess;
import jdk.graal.compiler.truffle.test.SLTruffleGraalTestSuite;

/**
 * Tests support for dumping graphs and other info useful for debugging a compiler crash.
 */
public class CompilationWrapperTest extends GraalCompilerTest {

    /**
     * Tests compilation requested by the VM.
     */
    @Test
    public void testVMCompilation1() throws IOException, InterruptedException {
        assumeNotImpactedByJDK8316453();
        assumeManagementLibraryIsLoadable();
        testHelper(Collections.emptyList(), Arrays.asList("-XX:-TieredCompilation",
                        "-XX:+UseJVMCICompiler",
                        "-XX:-UseJVMCINativeLibrary",
                        "-XX:JVMCIThreads=1",
                        "-Djdk.graal.CompilationFailureAction=ExitVM",
                        "-Djdk.graal.CrashAt=TestProgram.*",
                        "-Xcomp",
                        "-XX:CompileCommand=compileonly,*/TestProgram.print*",
                        TestProgram.class.getName()));
    }

    /**
     * Assumes the current JDK does not contain the bug resolved by JDK-8316453.
     */
    private static void assumeNotImpactedByJDK8316453() {
        Runtime.Version version = Runtime.version();
        Runtime.Version jdk8316453 = Runtime.Version.parse("22+17");
        Assume.assumeTrue("-Xcomp broken", version.feature() < 22 || version.compareTo(jdk8316453) >= 0);
    }

    public static class Probe {
        final String substring;
        final int minOccurrences;
        final int maxOccurrences;

        int actualOccurrences;
        String lastMatchingLine;

        public Probe(String substring, int expectedOccurrences) {
            this(substring, expectedOccurrences, expectedOccurrences);
        }

        public Probe(String substring, int minOccurrences, int maxOccurrences) {
            this.substring = substring;
            this.minOccurrences = minOccurrences;
            this.maxOccurrences = maxOccurrences;
        }

        public boolean matches(String line) {
            if (line.contains(substring)) {
                actualOccurrences++;
                lastMatchingLine = line;
                return true;
            }
            return false;
        }

        public String test() {
            if (actualOccurrences >= minOccurrences && actualOccurrences <= maxOccurrences) {
                return null;
            }
            if (maxOccurrences == Integer.MAX_VALUE) {
                return String.format("expected at least %d occurrences, found %d", minOccurrences, actualOccurrences);
            }
            return String.format("expected occurrences to be in [%d .. %d], found %d", minOccurrences, maxOccurrences, actualOccurrences);
        }
    }

    public static class ZipProbe {
        final String suffix;
        final int minOccurrences;

        int actualOccurrences = 0;

        public ZipProbe(String suffix, int minOccurrences) {
            this.suffix = suffix;
            this.minOccurrences = minOccurrences;
        }

        public boolean matches(ZipEntry entry, ZipFile zipFile) throws IOException {
            if (entry.getName().endsWith(suffix)) {
                actualOccurrences++;
                verify(entry, zipFile);
                return true;
            }
            return false;
        }

        @SuppressWarnings("unused")
        public void verify(ZipEntry entry, ZipFile zipFile) throws IOException {
            // Meant to be overridden
        }

        public String test() {
            if (actualOccurrences >= minOccurrences) {
                return null;
            }
            return String.format("expected at least %d occurrences, found %d", minOccurrences, actualOccurrences);
        }
    }

    /**
     * Tests {@link GraalCompilerOptions#MaxCompilationProblemsPerAction} in context of a
     * compilation requested by the VM.
     */
    @Test
    public void testVMCompilation3() throws IOException, InterruptedException {
        assumeNotImpactedByJDK8316453();
        assumeManagementLibraryIsLoadable();
        final int maxProblems = 2;
        Probe failurePatternProbe = new Probe("[[[Graal compilation failure]]]", 1, maxProblems);
        Probe retryingProbe = new Probe("Retrying compilation of", maxProblems);
        Probe adjustmentProbe = new Probe("adjusting CompilationFailureAction from Diagnose to Print", 1) {
            @Override
            public String test() {
                if (retryingProbe.actualOccurrences >= maxProblems) {
                    if (actualOccurrences == 0) {
                        return "expected at least one occurrence";
                    }
                }
                return null;
            }
        };
        Probe[] probes = {
                        failurePatternProbe,
                        retryingProbe,
                        adjustmentProbe
        };
        testHelper(Arrays.asList(probes), Arrays.asList("-XX:-TieredCompilation",
                        "-XX:+UseJVMCICompiler",
                        "-XX:-UseJVMCINativeLibrary",
                        "-XX:JVMCIThreads=1",
                        "-Djdk.graal.SystemicCompilationFailureRate=0",
                        "-Djdk.graal.CompilationFailureAction=Diagnose",
                        "-Djdk.graal.MaxCompilationProblemsPerAction=" + maxProblems,
                        "-Djdk.graal.CrashAt=TestProgram.*",
                        "-Xcomp",
                        "-XX:CompileCommand=compileonly,*/TestProgram.print*",
                        TestProgram.class.getName()));
    }

    /**
     * Tests compilation requested by Truffle.
     */
    @Test
    public void testTruffleCompilation1() throws IOException, InterruptedException {
        assumeManagementLibraryIsLoadable();
        testHelper(Collections.emptyList(),
                        Arrays.asList(
                                        SubprocessUtil.PACKAGE_OPENING_OPTIONS,
                                        "-Djdk.graal.CompilationFailureAction=ExitVM",
                                        "-Dpolyglot.engine.CompilationFailureAction=ExitVM",
                                        "-Dpolyglot.engine.TreatPerformanceWarningsAsErrors=all",
                                        "-Djdk.graal.CrashAt=root test1"),
                        SLTruffleGraalTestSuite.class.getName(), "test");
    }

    /**
     * Tests that --engine.CompilationFailureAction=ExitVM generates diagnostic output.
     */
    @Test
    public void testTruffleCompilation2() throws IOException, InterruptedException {
        assumeManagementLibraryIsLoadable();
        Probe[] probes = {
                        new Probe("Exiting VM due to engine.CompilationFailureAction=ExitVM", 1),
        };
        testHelper(Arrays.asList(probes),
                        Arrays.asList(
                                        SubprocessUtil.PACKAGE_OPENING_OPTIONS,
                                        "-Djdk.graal.CompilationFailureAction=Silent",
                                        "-Dpolyglot.engine.CompilationFailureAction=ExitVM",
                                        "-Dpolyglot.engine.TreatPerformanceWarningsAsErrors=all",
                                        "-Djdk.graal.CrashAt=root test1:PermanentBailout"),
                        SLTruffleGraalTestSuite.class.getName(), "test");
    }

    private static final boolean VERBOSE = Boolean.getBoolean("CompilationWrapperTest.verbose");

    public static void testHelper(List<Probe> initialProbes,
                    List<String> extraVmArgs,
                    String... mainClassAndArgs) throws IOException, InterruptedException {
        testHelper(initialProbes, List.of(), extraVmArgs, mainClassAndArgs);
    }

    public static void testHelper(List<Probe> initialOutputProbes,
                    List<ZipProbe> initialZipProbes,
                    List<String> extraVmArgs,
                    String... mainClassAndArgs) throws IOException, InterruptedException {
        final Path dumpPath = getOutputDirectory().resolve(CompilationWrapperTest.class.getSimpleName() + "_" + nowAsFileName());
        List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
        vmArgs.removeIf(a -> a.startsWith("-Djdk.graal."));
        vmArgs.remove("-esa");
        vmArgs.remove("-ea");
        vmArgs.add("-Djdk.graal.DumpPath=" + dumpPath);
        // Force output to a file even if there's a running IGV instance available.
        vmArgs.add("-Djdk.graal.PrintGraphFile=true");
        vmArgs.addAll(extraVmArgs);

        Subprocess proc = SubprocessUtil.java(vmArgs, mainClassAndArgs);
        if (VERBOSE) {
            System.out.printf("%n%s%n", proc.preserveArgfile());
        }

        try {
            List<Probe> probes = new ArrayList<>(initialOutputProbes);
            String format = DIAGNOSTIC_OUTPUT_DIRECTORY_MESSAGE_FORMAT;
            assert format.endsWith("'%s'") : format;
            String prefix = format.substring(0, format.length() - "'%s'".length());
            Probe diagnosticProbe = new Probe(prefix, 1);
            probes.add(diagnosticProbe);
            probes.add(new Probe("Forced crash after compiling", 1, Integer.MAX_VALUE));

            for (String line : proc.output) {
                for (Probe probe : probes) {
                    if (probe.matches(line)) {
                        break;
                    }
                }
            }
            for (Probe probe : probes) {
                String error = probe.test();
                if (error != null) {
                    Assert.fail(String.format("Did not find expected occurrences of '%s' in output of command: %s%n%s", probe.substring, error, proc.preserveArgfile()));
                }
            }

            Pattern diagnosticOutputFileRE = Pattern.compile(DIAGNOSTIC_OUTPUT_DIRECTORY_MESSAGE_REGEXP);
            String line = diagnosticProbe.lastMatchingLine;
            Matcher m = diagnosticOutputFileRE.matcher(line);
            Assert.assertTrue(line, m.find());
            String diagnosticOutputZip = m.group(1);

            List<String> dumpPathEntries = List.of(dumpPath.toFile().list());

            File zip = new File(diagnosticOutputZip).getAbsoluteFile();
            Assert.assertTrue(zip.toString(), zip.exists());
            Assert.assertTrue(zip + " not in " + dumpPathEntries, dumpPathEntries.contains(zip.getName()));
            try {
                ZipFile dd = new ZipFile(diagnosticOutputZip);
                List<String> entries = new ArrayList<>();
                List<ZipProbe> zipProbes = new ArrayList<>(initialZipProbes);
                zipProbes.add(new ZipProbe(".bgv", 1));
                zipProbes.add(new ZipProbe(".cfg", 1));
                zipProbes.add(new ZipProbe("retry.log", 0) {
                    @Override
                    public void verify(ZipEntry entry, ZipFile file) throws IOException {
                        String log = new String(file.getInputStream(entry).readAllBytes());
                        Pattern re = Pattern.compile("<Metrics>.*</Metrics>", Pattern.DOTALL);
                        if (!re.matcher(log).find()) {
                            Assert.fail(String.format("Could not find %s in %s:%n%s", re.pattern(), entry.getName(), log));
                        }
                    }
                });
                for (Enumeration<? extends ZipEntry> e = dd.entries(); e.hasMoreElements();) {
                    ZipEntry ze = e.nextElement();
                    entries.add(ze.getName());
                    for (ZipProbe probe : zipProbes) {
                        if (probe.matches(ze, dd)) {
                            break;
                        }
                    }
                }
                for (ZipProbe probe : zipProbes) {
                    String error = probe.test();
                    if (error != null) {
                        Assert.fail(String.format("Did not find expected occurrences of '%s' files in %s: %s%n%s", probe.suffix, entries, error, proc.preserveArgfile()));
                    }
                }
            } finally {
                zip.delete();
            }
        } finally {
            Path directory = dumpPath;
            removeDirectory(directory);
        }
    }
}

class TestProgram {
    public static void main(String[] args) {
        printHello1();
        printWorld1();
        printHello2();
        printWorld2();
        printHello3();
        printWorld3();
    }

    private static void printHello1() {
        System.out.println("Hello1");
    }

    private static void printWorld1() {
        System.out.println("World1");
    }

    private static void printHello2() {
        System.out.println("Hello2");
    }

    private static void printWorld2() {
        System.out.println("World2");
    }

    private static void printHello3() {
        System.out.println("Hello3");
    }

    private static void printWorld3() {
        System.out.println("World3");
    }
}
