/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

public class WasmContextOptions {
    @CompilationFinal private boolean saturatingFloatToInt;
    @CompilationFinal private boolean signExtensionOps;
    @CompilationFinal private boolean multiValue;
    @CompilationFinal private boolean bulkMemoryAndRefTypes;
    @CompilationFinal private boolean memory64;
    @CompilationFinal private boolean extendedConstExpressions;
    @CompilationFinal private boolean multiMemory;
    @CompilationFinal private boolean unsafeMemory;
    @CompilationFinal private boolean threads;
    @CompilationFinal private boolean simd;

    @CompilationFinal private boolean memoryOverheadMode;
    @CompilationFinal private boolean constantRandomGet;

    @CompilationFinal private String debugCompDirectory;
    private final OptionValues optionValues;

    WasmContextOptions(OptionValues optionValues) {
        this.optionValues = optionValues;
        setOptionValues();
        checkOptionDependencies();
    }

    public static WasmContextOptions fromOptionValues(OptionValues optionValues) {
        return new WasmContextOptions(optionValues);
    }

    private void setOptionValues() {
        this.saturatingFloatToInt = readBooleanOption(WasmOptions.SaturatingFloatToInt);
        this.signExtensionOps = readBooleanOption(WasmOptions.SignExtensionOps);
        this.multiValue = readBooleanOption(WasmOptions.MultiValue);
        this.bulkMemoryAndRefTypes = readBooleanOption(WasmOptions.BulkMemoryAndRefTypes);
        this.memory64 = readBooleanOption(WasmOptions.Memory64);
        this.extendedConstExpressions = readBooleanOption(WasmOptions.ExtendedConstExpressions);
        this.multiMemory = readBooleanOption(WasmOptions.MultiMemory);
        this.threads = readBooleanOption(WasmOptions.Threads);
        this.unsafeMemory = readBooleanOption(WasmOptions.UseUnsafeMemory);
        this.simd = readBooleanOption(WasmOptions.SIMD);
        this.memoryOverheadMode = readBooleanOption(WasmOptions.MemoryOverheadMode);
        this.constantRandomGet = readBooleanOption(WasmOptions.WasiConstantRandomGet);
        this.debugCompDirectory = readStringOption(WasmOptions.DebugCompDirectory);
    }

    private void checkOptionDependencies() {
        if (memory64 && !unsafeMemory) {
            failDependencyCheck("Memory64", "UseUnsafeMemory");
        }
    }

    private boolean readBooleanOption(OptionKey<Boolean> key) {
        return key.getValue(optionValues);
    }

    private String readStringOption(OptionKey<String> key) {
        return key.getValue(optionValues);
    }

    private static void failDependencyCheck(String option, String dependency) {
        throw WasmException.format(Failure.INCOMPATIBLE_OPTIONS, "Incompatible WebAssembly options: %s requires %s to be enabled.", option, dependency);
    }

    public boolean supportSaturatingFloatToInt() {
        return saturatingFloatToInt;
    }

    public boolean supportSignExtensionOps() {
        return signExtensionOps;
    }

    public boolean supportMultiValue() {
        return multiValue;
    }

    public boolean supportBulkMemoryAndRefTypes() {
        return bulkMemoryAndRefTypes;
    }

    public boolean supportMemory64() {
        return memory64;
    }

    public boolean supportExtendedConstExpressions() {
        return extendedConstExpressions;
    }

    public boolean supportMultiMemory() {
        return multiMemory;
    }

    public boolean supportThreads() {
        return threads;
    }

    public boolean useUnsafeMemory() {
        return unsafeMemory;
    }

    public boolean supportSIMD() {
        return simd;
    }

    public boolean memoryOverheadMode() {
        return memoryOverheadMode;
    }

    public boolean constantRandomGet() {
        return constantRandomGet;
    }

    public String debugCompDirectory() {
        return debugCompDirectory;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + (this.saturatingFloatToInt ? 1 : 0);
        hash = 53 * hash + (this.signExtensionOps ? 1 : 0);
        hash = 53 * hash + (this.multiValue ? 1 : 0);
        hash = 53 * hash + (this.bulkMemoryAndRefTypes ? 1 : 0);
        hash = 53 * hash + (this.memory64 ? 1 : 0);
        hash = 54 * hash + (this.extendedConstExpressions ? 1 : 0);
        hash = 53 * hash + (this.multiMemory ? 1 : 0);
        hash = 53 * hash + (this.unsafeMemory ? 1 : 0);
        hash = 53 * hash + (this.simd ? 1 : 0);
        hash = 53 * hash + (this.memoryOverheadMode ? 1 : 0);
        hash = 53 * hash + (this.constantRandomGet ? 1 : 0);
        hash = 53 * hash + (this.debugCompDirectory.hashCode());
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final WasmContextOptions other = (WasmContextOptions) obj;
        if (this.saturatingFloatToInt != other.saturatingFloatToInt) {
            return false;
        }
        if (this.signExtensionOps != other.signExtensionOps) {
            return false;
        }
        if (this.multiValue != other.multiValue) {
            return false;
        }
        if (this.bulkMemoryAndRefTypes != other.bulkMemoryAndRefTypes) {
            return false;
        }
        if (this.memory64 != other.memory64) {
            return false;
        }
        if (this.extendedConstExpressions != other.extendedConstExpressions) {
            return false;
        }
        if (this.multiMemory != other.multiMemory) {
            return false;
        }
        if (this.threads != other.threads) {
            return false;
        }
        if (this.unsafeMemory != other.unsafeMemory) {
            return false;
        }
        if (this.simd != other.simd) {
            return false;
        }
        if (this.memoryOverheadMode != other.memoryOverheadMode) {
            return false;
        }
        if (this.constantRandomGet != other.constantRandomGet) {
            return false;
        }
        if (!this.debugCompDirectory.equals(other.debugCompDirectory)) {
            return false;
        }
        return true;
    }
}
