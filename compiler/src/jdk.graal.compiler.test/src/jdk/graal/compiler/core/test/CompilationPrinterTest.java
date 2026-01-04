/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.core.CompilationPrinter;
import jdk.graal.compiler.core.GraalCompilerOptions;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.LogStream;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests {@link CompilationPrinter}.
 */
public class CompilationPrinterTest extends GraalCompilerTest {
    @Test
    public void testPrintToCSV() throws IOException {
        Assume.assumeFalse(CompilationPrinter.printingToCSV());
        try (TemporaryDirectory temp = new TemporaryDirectory("CompilationPrinterTest")) {
            Path path = temp.path.resolve("stats.csv");
            OptionValues options = new OptionValues(getInitialOptions(), GraalCompilerOptions.PrintCompilationCSV, path.toString());
            ResolvedJavaMethod method = getResolvedJavaMethod("dummy");
            CompilationPrinter.begin(options, CompilationIdentifier.INVALID_COMPILATION_ID, method, 42).finish(null, null);
            try (BufferedReader inputReader = new BufferedReader(new FileReader(path.toFile()))) {
                String header = inputReader.readLine();
                assertFalse(header.isBlank());
                String compilation = inputReader.readLine();
                assertTrue(compilation.contains(method.getName()));
            }
        } finally {
            CompilationPrinter.close();
        }
    }

    @SuppressWarnings("try")
    @Test
    public void testPrintToTTY() throws IOException {
        try (ByteArrayOutputStream captureStream = new ByteArrayOutputStream(); TTY.Filter filter = new TTY.Filter(new LogStream(captureStream))) {
            OptionValues options = new OptionValues(getInitialOptions(), GraalCompilerOptions.PrintCompilation, true);
            ResolvedJavaMethod method = getResolvedJavaMethod("dummy");
            CompilationPrinter.begin(options, CompilationIdentifier.INVALID_COMPILATION_ID, method, 42).finish(null, null);
            assertTrue(captureStream.toString().contains(method.getName()));
        }
    }

    public static void dummy() {
    }
}
