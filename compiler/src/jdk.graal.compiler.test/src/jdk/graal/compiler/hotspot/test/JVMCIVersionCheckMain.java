/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.JVMCIVersionCheck;

/**
 * Tests {@link JVMCIVersionCheck#main(String[])} argument handling. This focuses on covering all
 * accepted flags and the unknown-argument path while avoiding the default path that may call
 * System.exit on version check failure.
 */
public class JVMCIVersionCheckMain extends GraalCompilerTest {

    private static String runMainCaptureOut(String... args) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(baos);
        try {
            System.setOut(capture);
            JVMCIVersionCheck.main(args);
        } finally {
            System.setOut(originalOut);
        }
        return baos.toString();
    }

    @Test
    public void testNoArgument() {
        String out = runMainCaptureOut();
        Assert.assertNotNull(out);
        Assert.assertFalse("no output produced", out.isEmpty());
        String[] split = out.strip().split(",");
        Assert.assertEquals("unexpected length of result: " + Arrays.toString(split), 3, split.length);
        JVMCIVersionCheck.createLabsJDKVersion(split[0], split[1], Integer.parseInt(split[2]));
    }

    @Test
    public void testMinVersionTuple() {
        String out = runMainCaptureOut("--min-version");
        Assert.assertNotNull(out);
        Assert.assertFalse("no output produced", out.isEmpty());
        String[] split = out.strip().split(",");
        Assert.assertEquals("unexpected length of result: " + Arrays.toString(split), 3, split.length);
        JVMCIVersionCheck.createLabsJDKVersion(split[0], split[1], Integer.parseInt(split[2]));
    }

    @Test
    public void testMinVersionAsTag() {
        String out = runMainCaptureOut("--min-version", "--as-tag");
        Assert.assertNotNull(out);
        Assert.assertFalse(out.contains("No minimum JVMCI version specified for JDK version"));
        // check that the output is a valid version
        Runtime.Version.parse(out.strip());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownArgument() {
        JVMCIVersionCheck.main(new String[]{"--unknown-flag"});
    }
}
