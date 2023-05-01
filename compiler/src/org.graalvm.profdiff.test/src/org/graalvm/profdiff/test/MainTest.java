/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import org.graalvm.profdiff.Profdiff;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MainTest {

    /**
     * The saved value of {@link System#out}.
     */
    private PrintStream savedSystemOut;

    /**
     * The saved value of {@link System#err}.
     */
    private PrintStream savedSystemErr;

    @Before
    public void replaceSystemStreams() {
        savedSystemOut = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream()));
        savedSystemErr = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream()));
    }

    @After
    public void restoreSystemStreams() {
        System.out.close();
        System.setOut(savedSystemOut);
        savedSystemOut = null;
        System.err.close();
        System.setErr(savedSystemErr);
        savedSystemErr = null;
    }

    @Test
    public void testHelp() {
        String[] args = {"help"};
        Profdiff.main(args);

        args = new String[]{"help", "report"};
        Profdiff.main(args);

        args = new String[]{"help", "jit-vs-jit"};
        Profdiff.main(args);

        args = new String[]{"help", "jit-vs-aot"};
        Profdiff.main(args);

        args = new String[]{"help", "aot-vs-aot"};
        Profdiff.main(args);

        args = new String[]{"help", "unknown"};
        Profdiff.main(args);
    }

    @Test
    public void testArgumentParsing() {
        String[] args = {"--hot-min-limit", "1", "--hot-max-limit", "2", "--hot-percentile", "0.5", "--optimization-context-tree", "true", "--diff-compilations", "true", "--long-bci", "true",
                        "--sort-inlining-tree", "true", "--sort-unordered-phases", "true", "--remove-detailed-phases", "true", "--prune-identities", "true", "--create-fragments", "true",
                        "--inliner-reasoning", "true", "help"};
        Profdiff.main(args);
    }

    @Test
    public void testReport() throws IOException {
        Path tmpDir = Files.createTempDirectory(Paths.get("."), "profdiffReport");
        try {
            String[] args = {"report", tmpDir.toAbsolutePath().toString()};
            Profdiff.main(args);
        } finally {
            deleteTree(tmpDir);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walk(root).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }
}
