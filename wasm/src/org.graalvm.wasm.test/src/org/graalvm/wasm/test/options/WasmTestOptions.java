/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test.options;

public final class WasmTestOptions {
    public static final String WAT_TO_WASM_EXECUTABLE = System.getProperty("wasmtest.watToWasmExecutable");
    public static final String TEST_SOURCE_PATH = System.getProperty("wasmtest.testSourcePath");
    public static final String TEST_FILTER = System.getProperty("wasmtest.testFilter");
    public static final String LOG_LEVEL = System.getProperty("wasmtest.logLevel");
    public static final String STORE_CONSTANTS_POLICY = System.getProperty("wasmtest.storeConstantsPolicy");
    public static final String OFFICIAL_TESTS_DIR = System.getProperty("wasmtest.officialTestsDir");
    public static final boolean OFFICIAL_TESTS_RUN_ALL = Boolean.parseBoolean(System.getProperty("wasmtest.officialTestsRunAll"));
    public static final boolean OFFICIAL_TESTS_LOG = Boolean.parseBoolean(System.getProperty("wasmtest.officialTestsLog"));
    public static final boolean SHARED_ENGINE = Boolean.parseBoolean(System.getProperty("wasmtest.sharedEngine"));
    public static final boolean COVERAGE_MODE = Boolean.parseBoolean(System.getProperty("wasmtest.coverageMode"));
}
