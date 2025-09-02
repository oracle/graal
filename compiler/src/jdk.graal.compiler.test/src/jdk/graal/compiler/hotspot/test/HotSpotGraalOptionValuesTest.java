/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.hotspot.HotSpotGraalOptionValues.GRAAL_OPTION_PROPERTY_PREFIX;
import static jdk.graal.compiler.test.SubprocessUtil.getVMCommandLine;
import static jdk.graal.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.test.SubprocessUtil;

public class HotSpotGraalOptionValuesTest extends HotSpotGraalCompilerTest {

    @Test
    public void testOptionsInFile() throws IOException, InterruptedException {
        File optionsFile = File.createTempFile("options", ".properties").getAbsoluteFile();
        try {
            List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
            vmArgs.removeIf(a -> a.startsWith("-Djdk.graal."));
            vmArgs.add("-XX:+UseJVMCICompiler");
            vmArgs.add("-Djdk.graal.options.file=" + optionsFile);
            vmArgs.add("-XX:+EagerJVMCI");
            vmArgs.add("--version");
            SubprocessUtil.Subprocess proc = SubprocessUtil.java(vmArgs);

            if (proc.exitCode == 0) {
                Assert.fail(String.format("Expected non-0 exit code%n%s", proc.preserveArgfile()));
            }

            String expect = "The 'jdk.graal.options.file' property is no longer supported";
            if (proc.output.stream().noneMatch(line -> line.contains(expect))) {
                Assert.fail(String.format("Did not find '%s' in output of command:%n%s", expect, proc.preserveArgfile()));
            }
        } finally {
            optionsFile.delete();
        }
    }

    @Test
    public void testPrintHelp() throws IOException {
        OptionValues options = getInitialOptions();
        for (boolean all : new boolean[]{true, false}) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                PrintStream out = new PrintStream(baos);
                options.printHelp(OptionsParser.getOptionsLoader(), out, GRAAL_OPTION_PROPERTY_PREFIX, all);
                Assert.assertNotEquals(0, baos.size());
            }
        }
    }

    /**
     * Ensures the legacy prefix for Graal options (i.e. {@code -Dgraal.}) is accepted without
     * emitting a warning.
     */
    @Test
    public void testLegacy() throws IOException, InterruptedException {
        List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
        vmArgs.removeIf(a -> a.startsWith("-Djdk.graal."));
        vmArgs.add("-Dgraal.ShowConfiguration=info");
        vmArgs.add("-Dgraal.PrintCompilation=true");
        vmArgs.add("-XX:+EagerJVMCI");
        vmArgs.add("--version");
        SubprocessUtil.Subprocess proc = SubprocessUtil.java(vmArgs);

        if (proc.exitCode != 0) {
            Assert.fail(String.format("Expected non-0 exit code%n%s", proc.preserveArgfile()));
        }

        for (String line : proc.output) {
            if (line.contains("WARNING:")) {
                Assert.fail(String.format("Found match for 'WARNING:' in output of command:%n%s",
                                proc.preserveArgfile()));
            }
        }
    }
}
