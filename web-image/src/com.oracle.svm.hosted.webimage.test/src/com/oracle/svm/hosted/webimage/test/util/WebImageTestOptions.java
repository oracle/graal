/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.test.util;

import java.util.Arrays;
import java.util.List;

import jdk.graal.compiler.debug.GraalError;

public class WebImageTestOptions {
    public static final String JS_CMD = System.getProperty("webimage.test.js");
    private static final String LAUNCHER = System.getProperty("webimage.test.launcher");

    /**
     * Additional flags to pass to the {@link #LAUNCHER}.
     *
     * @implNote The default value of "," results in an empty array being returned by
     *           String.split(",").
     */
    public static final List<String> LAUNCHER_FLAGS = Arrays.asList(System.getProperty("webimage.test.flags", ",").split(","));
    public static final boolean USE_CLOSURE_COMPILER = Boolean.parseBoolean(System.getProperty("webimage.test.closure_compiler", "false"));
    /**
     * Whether the test directory should be cleaned up.
     *
     * Set to 'false' if you want access to test compilation artifacts after the tests are done.
     */
    public static final boolean CLEANUP = Boolean.parseBoolean(System.getProperty("webimage.test.cleanup", "true"));
    /**
     * This system property contains a list of comma separated options that should be set for Web
     * Image. Example - additional_options might look like:
     *
     * <pre>
     * -H:LongEmulation=BIGINT,-H:+UsePEA
     * </pre>
     *
     * @implNote The default value of "," results in an empty array being returned by
     *           String.split(",").
     */
    public static final List<String> ADDITIONAL_OPTIONS = Arrays.asList(System.getProperty("webimage.test.additional_vm_options", ",").split(","));

    /**
     * Threshold after how many tests, the test runner should stop.
     * <p>
     * Only works with test suites using {@link FailFastSuite}.
     * <p>
     * '0' means no limit.
     */
    public static final int MAX_FAILURES = Integer.parseInt(System.getProperty("webimage.test.max_failures", "0"));

    /**
     * Place test output files in given directory.
     * <p>
     * If specified, this directory will be created and its content deleted. Otherwise, a temporary
     * directory is created. In either case, the directory will be deleted unless {@link #CLEANUP}
     * is turned off.
     */
    public static final String TEST_DIRECTORY = System.getProperty("webimage.test.dir");

    public static String getLauncher() {
        GraalError.guarantee(LAUNCHER != null, "No web image launcher specified");
        return LAUNCHER;
    }

    public static boolean isWasmLMBackend() {
        return WebImageTestOptions.ADDITIONAL_OPTIONS.contains("-H:Backend=WASM");
    }

    public static boolean isWasmGCBackend() {
        return WebImageTestOptions.ADDITIONAL_OPTIONS.contains("-H:Backend=WASMGC");
    }
}
