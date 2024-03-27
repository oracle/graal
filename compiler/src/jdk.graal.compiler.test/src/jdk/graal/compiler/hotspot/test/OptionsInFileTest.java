/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.GraalOptions.InlineMegamorphicCalls;
import static jdk.graal.compiler.core.common.GraalOptions.MaximumDesiredSize;
import static jdk.graal.compiler.test.SubprocessUtil.getVMCommandLine;
import static jdk.graal.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.graal.compiler.test.SubprocessUtil.Subprocess;

/**
 * Tests reading options from a file specified by the {@code graal.options.file}.
 */
public class OptionsInFileTest extends GraalCompilerTest {
    @Test
    public void test() throws IOException, InterruptedException {
        Boolean inlineMegamorphic = false;
        Integer maximumDesiredSize = 10000;
        File optionsFile = File.createTempFile("options", ".properties").getAbsoluteFile();
        try {
            Assert.assertFalse(inlineMegamorphic.equals(InlineMegamorphicCalls.getDefaultValue()));
            Assert.assertFalse(maximumDesiredSize.equals(MaximumDesiredSize.getDefaultValue()));

            try (PrintStream out = new PrintStream(new FileOutputStream(optionsFile))) {
                out.println(InlineMegamorphicCalls.getName() + "=" + inlineMegamorphic);
                out.println(MaximumDesiredSize.getName() + "=" + maximumDesiredSize);
            }

            List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
            vmArgs.removeIf(a -> a.startsWith("-Djdk.graal."));
            vmArgs.add("-Djdk.graal.options.file=" + optionsFile);
            vmArgs.add("-Djdk.graal.PrintPropertiesAll=true");
            vmArgs.add("-XX:+JVMCIPrintProperties");
            Subprocess proc = SubprocessUtil.java(vmArgs);

            String[] expected = {
                            "graal.InlineMegamorphicCalls := false",
                            "graal.MaximumDesiredSize := 10000"};
            for (String line : proc.output) {
                for (int i = 0; i < expected.length; i++) {
                    if (expected[i] != null && line.contains(expected[i])) {
                        expected[i] = null;
                    }
                }
            }

            for (int i = 0; i < expected.length; i++) {
                if (expected[i] != null) {
                    Assert.fail(String.format("Did not find '%s' in output of command:%n%s", expected[i], proc));
                }
            }
        } finally {
            optionsFile.delete();
        }
    }
}
