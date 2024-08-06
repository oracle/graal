/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class DeoptimizeOnIntegerExactTest extends GraalCompilerTest {

    static boolean highlyLikely = true;
    static boolean highlyUnlikely = false;

    public static int testAddExactSnippet(int x, int y) {
        if (highlyLikely) {
            return highlyUnlikely ? Math.addExact(x, y) : x;
        } else {
            return highlyUnlikely ? y : Math.addExact(x, y);
        }
    }

    public static int testSubtractExactSnippet(int x, int y) {
        if (highlyLikely) {
            return highlyUnlikely ? Math.subtractExact(x, y) : x;
        } else {
            return highlyUnlikely ? y : Math.subtractExact(x, y);
        }
    }

    public static int testMultiplyExactSnippet(int x, int y) {
        if (highlyLikely) {
            return highlyUnlikely ? Math.multiplyExact(x, y) : x;
        } else {
            return highlyUnlikely ? y : Math.multiplyExact(x, y);
        }
    }

    public static int testIncrementExactSnippet(int x, int y) {
        if (highlyLikely) {
            return highlyUnlikely ? Math.incrementExact(x) : x;
        } else {
            return highlyUnlikely ? y : Math.incrementExact(x);
        }
    }

    public static int testDecrementExactSnippet(int x, int y) {
        if (highlyLikely) {
            return highlyUnlikely ? Math.decrementExact(x) : x;
        } else {
            return highlyUnlikely ? y : Math.decrementExact(x);
        }
    }

    public static int testNegateExactSnippet(int v) {
        if (highlyLikely) {
            return highlyUnlikely ? Math.negateExact(v) : v;
        } else {
            return highlyUnlikely ? v : Math.negateExact(v);
        }
    }

    public void testAgainIfDeopt(String methodName, Object... args) throws InvalidInstalledCodeException {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        // We speculate on the first compilation. The global value numbering will merge the two
        // floating integer exact operation nodes.
        InstalledCode code = getCode(method);
        code.executeVarargs(args);
        if (!code.isValid()) {
            // At the recompilation, we anchor the floating integer exact operation nodes at their
            // corresponding branches.
            code = getCode(method);
            code.executeVarargs(args);
            // The recompiled code should not get deoptimized.
            assertTrue(code.isValid());
        }
    }

    @Test
    public void testAddExact() throws InvalidInstalledCodeException {
        testAgainIfDeopt("testAddExactSnippet", Integer.MAX_VALUE, 1);
    }

    @Test
    public void testSubtractExact() throws InvalidInstalledCodeException {
        testAgainIfDeopt("testSubtractExactSnippet", 0, Integer.MIN_VALUE);
    }

    @Test
    public void testMultiplyExact() throws InvalidInstalledCodeException {
        testAgainIfDeopt("testMultiplyExactSnippet", Integer.MAX_VALUE, 2);
    }

    @Test
    public void testIncrementExact() throws InvalidInstalledCodeException {
        testAgainIfDeopt("testIncrementExactSnippet", Integer.MAX_VALUE, 1);
    }

    @Test
    public void testDecrementExact() throws InvalidInstalledCodeException {
        testAgainIfDeopt("testDecrementExactSnippet", Integer.MIN_VALUE, 1);
    }

    @Test
    public void testNegateExact() throws InvalidInstalledCodeException {
        testAgainIfDeopt("testNegateExactSnippet", Integer.MIN_VALUE);
    }

    @Override
    protected InstalledCode addMethod(DebugContext debug, final ResolvedJavaMethod method, final CompilationResult compilationResult) {
        assert getSpeculationLog() == compilationResult.getSpeculationLog();
        return getBackend().createInstalledCode(debug, method, compilationResult, null, false);
    }
}
