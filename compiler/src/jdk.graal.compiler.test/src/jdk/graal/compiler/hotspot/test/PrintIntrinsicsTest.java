/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.List;
import java.util.Set;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.hotspot.nodes.VMErrorNode;
import jdk.graal.compiler.hotspot.replacements.AssertionSnippets;
import jdk.graal.compiler.hotspot.stubs.CreateExceptionStub;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.replacements.StringHelperIntrinsics;
import jdk.graal.compiler.replacements.StringUTF16Snippets;
import org.junit.Test;

import jdk.graal.compiler.test.SubprocessUtil.Subprocess;

/**
 * Tests support for
 * {@link jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Options#PrintIntrinsics}.
 */
public class PrintIntrinsicsTest extends SubprocessTest {

    public void test() {

    }

    @Test
    public void testInSubprocess() throws InterruptedException, IOException {
        String[] args = {
                        "-XX:+EagerJVMCI",
                        "-Djdk.graal.PrintIntrinsics=true",
                        "-XX:+UnlockDiagnosticVMOptions",
                        "-XX:-UseAESIntrinsics",
                        "-Djdk.graal.DisableIntrinsics=java.lang.Integer.*",
                        "--version"
        };
        Subprocess subprocess = launchSubprocess(this::test, args);
        assertLineInOutput(subprocess, "<Intrinsics>");
        assertLineInOutput(subprocess, "</Intrinsics>");

        // Random selection of intrinsics that should be stable.
        assertLineInOutput(subprocess, "java.lang.Class.getModifiers()");
        assertLineInOutput(subprocess, "java.lang.Byte.valueOf(byte)");
        assertLineInOutput(subprocess, "java.lang.System.nanoTime()");
        assertLineInOutput(subprocess, "java.lang.Thread.currentThread()");

        // Test intrinsics disabled by -XX:-UseAESIntrinsics
        assertLineInOutput(subprocess, "com.sun.crypto.provider.AESCrypt.implDecryptBlock(byte[];int;byte[];int) [disabled]");
        assertLineInOutput(subprocess, "com.sun.crypto.provider.AESCrypt.implEncryptBlock(byte[];int;byte[];int) [disabled]");

        // Test intrinsics disabled by -Djdk.graal.DisableIntrinsics
        for (String line : subprocess.output) {
            if (line.startsWith("java.lang.Integer.") && !line.endsWith(" [disabled]")) {
                throw new AssertionError(String.format("Expected intrinsic to be labeled with [disabled]: %s", line));
            }
        }

        String[] forcedIntrinsicPrefixes = {
                        GraalDirectives.class.getName(),
                        HotSpotBackend.class.getName(),
                        PiNode.class.getPackageName(),
                        ReplacementsUtil.class.getPackageName(),
                        VMErrorNode.class.getPackageName(),
                        AssertionSnippets.class.getPackageName(),
                        CreateExceptionStub.class.getPackageName()
        };
        Set<String> forcedIntrinsicPrefixExceptions = Set.of(
                        StringHelperIntrinsics.class.getName() + ".getByte(byte[];int)",
                        StringUTF16Snippets.class.getName() + ".getChar(byte[];int)");
        for (String line : subprocess.output) {
            for (var prefix : forcedIntrinsicPrefixes) {
                if (line.startsWith(prefix) && !line.endsWith(" [cannot be disabled]") && !forcedIntrinsicPrefixExceptions.contains(line)) {
                    throw new AssertionError(String.format("Expected intrinsic to be labeled with [cannot be disabled]: %s", line));
                }
            }
        }
    }

    private static void assertLineInOutput(Subprocess subprocess, String line) {
        List<String> output = subprocess.output;
        if (!output.contains(line)) {
            throw new AssertionError(String.format("Missing line in subprocess: %s%nOutput:%n%s", line, String.join(System.lineSeparator(), output)));
        }
    }
}
