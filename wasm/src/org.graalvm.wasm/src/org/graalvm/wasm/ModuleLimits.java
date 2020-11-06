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
import org.graalvm.wasm.exception.WasmException;

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
    private final int tableSizeLimit;
    private final int memorySizeLimit;

    public ModuleLimits(int moduleSizeLimit, int typeCountLimit, int functionCountLimit, int importCountLimit, int exportCountLimit, int globalCountLimit, int dataSegmentCountLimit,
                    int elementSegmentCountLimit, int functionSizeLimit, int paramCountLimit, int returnCountLimit, int localCountLimit, int tableSizeLimit, int memorySizeLimit) {
        this.moduleSizeLimit = moduleSizeLimit;
        this.typeCountLimit = typeCountLimit;
        this.functionCountLimit = functionCountLimit;
        this.importCountLimit = importCountLimit;
        this.exportCountLimit = exportCountLimit;
        this.globalCountLimit = globalCountLimit;
        this.dataSegmentCountLimit = dataSegmentCountLimit;
        this.elementSegmentCountLimit = elementSegmentCountLimit;
        this.functionSizeLimit = functionSizeLimit;
        this.paramCountLimit = paramCountLimit;
        this.returnCountLimit = returnCountLimit;
        this.localCountLimit = localCountLimit;
        this.tableSizeLimit = tableSizeLimit;
        this.memorySizeLimit = memorySizeLimit;
    }

    public void checkModuleSize(int size) {
        if (size > moduleSizeLimit) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "The size of the module (%d bytes) exceeds the limit (%d bytes).", size, moduleSizeLimit);
        }
    }

    public void checkTypeCount(int count) {
        if (count > typeCountLimit) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "The number of types defined in the types section (%d) exceeds the limit (%d).", count, typeCountLimit);
        }
    }

    public void checkFunctionCount(int count) {
        if (count > functionCountLimit) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "The number of functions defined in the module (%d) exceeds the limit (%d).", count, functionCountLimit);
        }
    }

    public void checkImportCount(int count) {
        if (count > importCountLimit) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "The number of imports declared in the module (%d) exceeds the limit (%d).", count, importCountLimit);
        }
    }

    public void checkExportCount(int count) {
        if (count > exportCountLimit) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "The number of exports declared in the module (%d) exceeds the limit (%d).", count, exportCountLimit);
        }
    }

    public void checkGlobalCount(int count) {
        if (count > globalCountLimit) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "The number of globals defined in the module (%d) exceeds the limit (%d).", count, globalCountLimit);
        }
    }

    public void checkDataSegmentCount(int count) {
        if (count > dataSegmentCountLimit) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "The number of data segments defined in the module (%d) exceeds the limit (%d).", count, dataSegmentCountLimit);
        }
    }

    public void checkElementSegmentCount(int count) {
        if (count > elementSegmentCountLimit) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "The number of table entries in the table initialization (%d) exceeds the limit (%d).", count, elementSegmentCountLimit);
        }
    }

    public void checkFunctionSize(int size) {
        if (size > functionSizeLimit) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "The size of the function body (%d) exceeds the limit (%d).", size, functionSizeLimit);
        }
    }

    public void checkParamCount(int count) {
        if (count > paramCountLimit) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "The number of parameters of the function (%d) exceeds the limit (%d).", count, paramCountLimit);
        }
    }

    public void checkReturnCount(int count) {
        if (count > returnCountLimit) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "The number of return values of the function (%d) exceeds the limit (%d).", count, returnCountLimit);
        }
    }

    public void checkLocalCount(int count) {
        if (count > localCountLimit) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "The number of locals declared in the function (%d) exceeds the limit (%d).", count, localCountLimit);
        }
    }

    public int getTableSizeLimit() {
        return tableSizeLimit;
    }

    public int getMemorySizeLimit() {
        return memorySizeLimit;
    }

}
