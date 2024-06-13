/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.api;

import org.graalvm.wasm.ModuleLimits;

public final class JsConstants {
    private JsConstants() {
    }

    // Limits specified by https://www.w3.org/TR/wasm-js-api/#limits
    private static final int MODULE_SIZE_LIMIT = 1 << 30;
    private static final int TYPE_COUNT_LIMIT = 1000000;
    private static final int FUNCTION_COUNT_LIMIT = 1000000;
    private static final int IMPORT_COUNT_LIMIT = 100000;
    private static final int EXPORT_COUNT_LIMIT = 100000;
    private static final int GLOBAL_COUNT_LIMIT = 1000000;
    private static final int DATA_SEGMENT_LIMIT = 100000;
    private static final int TABLE_COUNT_LIMIT = 100000;
    private static final int MEMORY_COUNT_LIMIT = 1;
    private static final int MULTI_MEMORY_COUNT_LIMIT = 100;
    private static final int ELEMENT_SEGMENT_LIMIT = 10000000;
    private static final int FUNCTION_SIZE_LIMIT = 7654321;
    private static final int PARAM_COUNT_LIMIT = 1000;
    private static final int RESULT_COUNT_LIMIT = 1;
    private static final int MULTI_VALUE_RESULT_COUNT_LIMIT = 1000;
    private static final int LOCAL_COUNT_LIMIT = 50000;
    private static final int TABLE_SIZE_LIMIT = 10000000;
    private static final int MEMORY_SIZE_LIMIT = 32767;

    public static final ModuleLimits JS_LIMITS = new ModuleLimits(
                    MODULE_SIZE_LIMIT,
                    TYPE_COUNT_LIMIT,
                    FUNCTION_COUNT_LIMIT,
                    TABLE_COUNT_LIMIT,
                    MEMORY_COUNT_LIMIT,
                    MULTI_MEMORY_COUNT_LIMIT,
                    IMPORT_COUNT_LIMIT,
                    EXPORT_COUNT_LIMIT,
                    GLOBAL_COUNT_LIMIT,
                    DATA_SEGMENT_LIMIT,
                    ELEMENT_SEGMENT_LIMIT,
                    FUNCTION_SIZE_LIMIT,
                    PARAM_COUNT_LIMIT,
                    RESULT_COUNT_LIMIT,
                    MULTI_VALUE_RESULT_COUNT_LIMIT,
                    LOCAL_COUNT_LIMIT,
                    TABLE_SIZE_LIMIT,
                    MEMORY_SIZE_LIMIT,
                    MEMORY_SIZE_LIMIT);
}
