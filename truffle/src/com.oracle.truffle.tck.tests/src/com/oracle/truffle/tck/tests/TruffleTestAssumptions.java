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

import org.graalvm.polyglot.Engine;
import org.junit.Assume;

import java.util.regex.Pattern;

public class TruffleTestAssumptions {
    private static final boolean spawnIsolate = Boolean.getBoolean("polyglot.engine.SpawnIsolate");
    private static final boolean aot = Boolean.getBoolean("com.oracle.graalvm.isaot");
    private static final boolean isolationDisabled = Boolean.parseBoolean(System.getProperty("polyglotimpl.DisableClassPathIsolation", "true"));

    public static void assumeWeakEncapsulation() {
        assumeNoIsolateEncapsulation();
        assumeNoClassLoaderEncapsulation();
    }

    public static void assumeNoIsolateEncapsulation() {
        Assume.assumeFalse(spawnIsolate);
    }

    public static void assumeNoClassLoaderEncapsulation() {
        Assume.assumeFalse(isClassLoaderEncapsulation());
    }

    public static void assumeOptimizingRuntime() {
        Assume.assumeTrue(isOptimizingRuntime());
    }

    public static void assumeFallbackRuntime() {
        Assume.assumeFalse(isOptimizingRuntime());
    }

    /**
     * Indicates that no Truffle classes can be passed from the test into a truffle langauge as
     * Truffle in a polyglot context is running in an isolated classloader.
     */
    public static boolean isClassLoaderEncapsulation() {
        return !Engine.class.getModule().isNamed() && !isolationDisabled;
    }

    public static boolean isNoClassLoaderEncapsulation() {
        return !isClassLoaderEncapsulation();
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
        return !isIsolateEncapsulation() && !isClassLoaderEncapsulation();
    }

    public static boolean isStrongEncapsulation() {
        return isIsolateEncapsulation() || isClassLoaderEncapsulation();
    }

    public static boolean isIsolateEncapsulation() {
        return spawnIsolate;
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

}
