/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.junit.Test;

import jdk.graal.compiler.core.GraalCompilerOptions;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.DebugOptions.PrintGraphTarget;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

/**
 * Check that setting the dump path results in files ending up in the right directory with matching
 * names.
 */
public class DumpPathTest extends SubprocessTest {

    /**
     * If this test does not complete in 60 seconds, something is very wrong.
     */
    private static final int TIME_LIMIT = 60000;

    public static Object snippet() {
        return new String("snippet");
    }

    @Test
    public void test() throws IOException, InterruptedException {
        launchSubprocess(this::runInSubprocess, "-Xmx50M");

    }

    public void runInSubprocess() {
        assumeManagementLibraryIsLoadable();
        try (var _ = new TTY.Filter();
                        TimeLimit _ = TimeLimit.create(TIME_LIMIT, "DumpPathTest")) {
            try (TemporaryDirectory temp = new TemporaryDirectory("DumpPathTest")) {
                String[] extensions = {".cfg", ".bgv", ".graph-strings"};
                EconomicMap<OptionKey<?>, Object> overrides = OptionValues.newOptionMap();
                overrides.put(DebugOptions.DumpPath, temp.toString());
                overrides.put(DebugOptions.ShowDumpFiles, false);
                overrides.put(DebugOptions.PrintBackendCFG, true);
                overrides.put(DebugOptions.PrintGraph, PrintGraphTarget.File);
                overrides.put(DebugOptions.PrintCanonicalGraphStrings, true);
                overrides.put(DebugOptions.Dump, "*");
                overrides.put(GraalCompilerOptions.DumpHeapAfter, "<compilation>:Schedule");
                overrides.put(DebugOptions.MethodFilter, null);

                try (var _ = new TTY.Filter()) {
                    // Generate dump files.
                    test(new OptionValues(getInitialOptions(), overrides), "snippet");
                }
                // Check that IGV files got created, in the right place.
                List<Path> paths = checkForFiles(temp.path, extensions);
                List<Path> compilationHeapDumps = new ArrayList<>();
                List<Path> phaseHeapDumps = new ArrayList<>();
                for (Path path : paths) {
                    String name = path.toString();
                    if (name.endsWith(".compilation.hprof")) {
                        compilationHeapDumps.add(path);
                    } else if (name.endsWith(".hprof")) {
                        phaseHeapDumps.add(path);
                    }
                }

                assertTrue(!compilationHeapDumps.isEmpty());
                assertTrue(!phaseHeapDumps.isEmpty());
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Check that the given directory contains file or directory names with all the given
     * extensions.
     */
    private static List<Path> checkForFiles(Path directoryPath, String[] extensions) throws IOException {
        String[] paths = new String[extensions.length];
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
            for (Path filePath : stream) {
                result.add(filePath);
                String fileName = filePath.getFileName().toString();
                System.out.printf("%s -> %,d bytes%n", filePath, Files.size(filePath));
                for (int i = 0; i < extensions.length; i++) {
                    String extension = extensions[i];
                    if (fileName.endsWith(extensions[i])) {
                        assertTrue(paths[i] == null, "multiple files found for %s in %s", extension, directoryPath);
                        paths[i] = fileName.replace(extensions[i], "");
                    }
                }
            }
        }
        for (int i = 0; i < paths.length; i++) {
            assertTrue(paths[i] != null, "missing file for extension %s in %s", extensions[i], directoryPath);
        }
        // Ensure that all file names are the same.
        for (int i = 1; i < paths.length; i++) {
            assertTrue(paths[0].equals(paths[i]), paths[0] + " != " + paths[i]);
        }
        return result;
    }
}
