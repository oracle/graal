/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm;

import org.graalvm.wasm.exception.Failure;

import static org.graalvm.wasm.Assert.assertUnsignedIntLessOrEqual;
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_INSTANCE_SIZE;
import static org.graalvm.wasm.constants.Sizes.MAX_TABLE_INSTANCE_SIZE;

/**
 * Limits on various aspects of a module.
 */
public final class ModuleLimits {
    private final int moduleSizeLimit;
    private final int typeCountLimit;
    private final int functionCountLimit;
    private final int importCountLimit;
    private final int exportCountLimit;
    private final int globalCountLimit;
    private final int dataSegmentCountLimit;
    private final int elementSegmentCountLimit;
    private final int functionSizeLimit;
    private final int paramCountLimit;
    private final int returnCountLimit;
    private final int localCountLimit;
    private final int tableInstanceSizeLimit;
    private final int memoryInstanceSizeLimit;

    public ModuleLimits(int moduleSizeLimit, int typeCountLimit, int functionCountLimit, int importCountLimit, int exportCountLimit, int globalCountLimit, int dataSegmentCountLimit,
                    int elementSegmentCountLimit, int functionSizeLimit, int paramCountLimit, int returnCountLimit, int localCountLimit, int tableInstanceSizeLimit, int memoryInstanceSizeLimit) {
        this.moduleSizeLimit = minUnsigned(moduleSizeLimit, Integer.MAX_VALUE);
        this.typeCountLimit = minUnsigned(typeCountLimit, Integer.MAX_VALUE);
        this.functionCountLimit = minUnsigned(functionCountLimit, Integer.MAX_VALUE);
        this.importCountLimit = minUnsigned(importCountLimit, Integer.MAX_VALUE);
        this.exportCountLimit = minUnsigned(exportCountLimit, Integer.MAX_VALUE);
        this.globalCountLimit = minUnsigned(globalCountLimit, Integer.MAX_VALUE);
        this.dataSegmentCountLimit = minUnsigned(dataSegmentCountLimit, Integer.MAX_VALUE);
        this.elementSegmentCountLimit = minUnsigned(elementSegmentCountLimit, Integer.MAX_VALUE);
        this.functionSizeLimit = minUnsigned(functionSizeLimit, Integer.MAX_VALUE);
        this.paramCountLimit = minUnsigned(paramCountLimit, Integer.MAX_VALUE);
        this.returnCountLimit = minUnsigned(returnCountLimit, Integer.MAX_VALUE);
        this.localCountLimit = minUnsigned(localCountLimit, Integer.MAX_VALUE);
        this.tableInstanceSizeLimit = minUnsigned(tableInstanceSizeLimit, MAX_TABLE_INSTANCE_SIZE);
        this.memoryInstanceSizeLimit = minUnsigned(memoryInstanceSizeLimit, MAX_MEMORY_INSTANCE_SIZE);
    }

    private static int minUnsigned(int a, int b) {
        return Integer.compareUnsigned(a, b) < 0 ? a : b;
    }

    static final ModuleLimits DEFAULTS = new ModuleLimits(
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    MAX_TABLE_INSTANCE_SIZE,
                    MAX_MEMORY_INSTANCE_SIZE);

    public void checkModuleSize(int size) {
        assertUnsignedIntLessOrEqual(size, moduleSizeLimit, Failure.MODULE_SIZE_LIMIT_EXCEEDED);
    }

    public void checkTypeCount(int count) {
        assertUnsignedIntLessOrEqual(count, typeCountLimit, Failure.TYPE_COUNT_LIMIT_EXCEEDED);
    }

    public void checkFunctionCount(int count) {
        assertUnsignedIntLessOrEqual(count, functionCountLimit, Failure.FUNCTION_COUNT_LIMIT_EXCEEDED);
    }

    public void checkImportCount(int count) {
        assertUnsignedIntLessOrEqual(count, importCountLimit, Failure.IMPORT_COUNT_LIMIT_EXCEEDED);
    }

    public void checkExportCount(int count) {
        assertUnsignedIntLessOrEqual(count, exportCountLimit, Failure.EXPORT_COUNT_LIMIT_EXCEEDED);
    }

    public void checkGlobalCount(int count) {
        assertUnsignedIntLessOrEqual(count, globalCountLimit, Failure.GLOBAL_COUNT_LIMIT_EXCEEDED);
    }

    public void checkDataSegmentCount(int count) {
        assertUnsignedIntLessOrEqual(count, dataSegmentCountLimit, Failure.DATA_SEGMENT_COUNT_LIMIT_EXCEEDED);
    }

    public void checkElementSegmentCount(int count) {
        assertUnsignedIntLessOrEqual(count, elementSegmentCountLimit, Failure.ELEMENT_SEGMENT_COUNT_LIMIT_EXCEEDED);
    }

    public void checkFunctionSize(int size) {
        assertUnsignedIntLessOrEqual(size, functionSizeLimit, Failure.FUNCTION_SIZE_LIMIT_EXCEEDED);
    }

    public void checkParamCount(int count) {
        assertUnsignedIntLessOrEqual(count, paramCountLimit, Failure.PARAMETERS_COUNT_LIMIT_EXCEEDED);
    }

    public void checkReturnCount(int count) {
        assertUnsignedIntLessOrEqual(count, returnCountLimit, Failure.RETURN_COUNT_LIMIT_EXCEEDED);
    }

    public void checkLocalCount(int count) {
        assertUnsignedIntLessOrEqual(count, localCountLimit, Failure.TOO_MANY_LOCALS);
    }

    public void checkTableInstanceSize(int size) {
        assertUnsignedIntLessOrEqual(size, tableInstanceSizeLimit, Failure.TABLE_INSTANCE_SIZE_LIMIT_EXCEEDED);
    }

    public void checkMemoryInstanceSize(int size) {
        assertUnsignedIntLessOrEqual(size, memoryInstanceSizeLimit, Failure.MEMORY_INSTANCE_SIZE_LIMIT_EXCEEDED);
    }

    public int tableInstanceSizeLimit() {
        return tableInstanceSizeLimit;
    }

    public int memoryInstanceSizeLimit() {
        return memoryInstanceSizeLimit;
    }
}
