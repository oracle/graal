/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.tck.tests;

import java.util.regex.Pattern;

import org.graalvm.polyglot.Engine;
import org.junit.Assume;

public class TruffleTestAssumptions {
    private static final boolean spawnIsolate = Boolean.getBoolean("polyglot.engine.SpawnIsolate");
    private static final boolean externalIsolate = "external".equals(System.getProperty("polyglot.engine.IsolateMode"));
    private static final boolean aot = Boolean.getBoolean("com.oracle.graalvm.isaot");

    public static void assumeWeakEncapsulation() {
        assumeNoIsolateEncapsulation();
    }

    public static void assumeNoIsolateEncapsulation() {
        Assume.assumeFalse(spawnIsolate);
    }

    public static void assumeOptimizingRuntime() {
        Assume.assumeTrue(isOptimizingRuntime());
    }

    public static void assumeEnterpriseRuntime() {
        Assume.assumeTrue(isEnterpriseRuntime());
    }

    public static void assumeFallbackRuntime() {
        Assume.assumeFalse(isOptimizingRuntime());
    }

    public static boolean isNoIsolateEncapsulation() {
        return !spawnIsolate;
    }

    private static Boolean optimizingRuntimeUsed;

    public static boolean isFallbackRuntime() {
        return !isOptimizingRuntime();
    }

    public static boolean isOptimizingRuntime() {
        Boolean optimizing = optimizingRuntimeUsed;
        if (optimizing == null) {
            try (Engine e = Engine.create()) {
                optimizingRuntimeUsed = optimizing = !e.getImplementationName().equals("Interpreted");
            }
        }
        return optimizing;
    }

    private static volatile Boolean enterpriseRuntimeUsed;

    public static boolean isEnterpriseRuntime() {
        Boolean enterprise = enterpriseRuntimeUsed;
        if (enterprise == null) {
            try (Engine e = Engine.create()) {
                enterprise = Pattern.compile("Oracle GraalVM( Isolated)?").matcher(e.getImplementationName()).matches();
                enterpriseRuntimeUsed = enterprise;
            }
        }
        return enterprise;
    }

    public static boolean isWeakEncapsulation() {
        return !isIsolateEncapsulation();
    }

    public static boolean isStrongEncapsulation() {
        return isIsolateEncapsulation();
    }

    public static boolean isIsolateEncapsulation() {
        return spawnIsolate;
    }

    public static boolean isExternalIsolate() {
        return externalIsolate;
    }

    public static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase().equals("linux");
    }

    public static boolean isAarch64() {
        String osArch = System.getProperty("os.arch").toLowerCase();
        return osArch.equals("aarch64") || osArch.equals("arm64"); // some JVMs use arm64
    }

    public static void assumeAOT() {
        Assume.assumeTrue(aot);
    }

    public static void assumeNotAOT() {
        Assume.assumeFalse(aot);
    }

    public static boolean isAOT() {
        return aot;
    }

    public static boolean isNotAOT() {
        return !aot;
    }

    public static boolean isDeoptLoopDetectionAvailable() {
        return Runtime.version().feature() >= 25;
    }

    public static void assumeDeoptLoopDetectionAvailable() {
        Assume.assumeTrue(isDeoptLoopDetectionAvailable());
    }
}
