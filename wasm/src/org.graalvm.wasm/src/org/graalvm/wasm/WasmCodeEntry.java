/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.profiles.BranchProfile;

public final class WasmCodeEntry {

    private final WasmFunction function;
    @CompilationFinal(dimensions = 1) private final byte[] data;
    @CompilationFinal(dimensions = 1) private final byte[] localTypes;
    private final int maxStackSize;
    private final BranchProfile errorBranch = BranchProfile.create();
    @CompilationFinal(dimensions = 1) private final int[] extraData;
    @CompilationFinal(dimensions = 1) private final byte[] resultTypes;
    private final int numLocals;
    private final int resultCount;

    public WasmCodeEntry(WasmFunction function, byte[] data, byte[] localTypes, byte[] resultTypes, int maxStackSize, int[] extraData) {
        this.function = function;
        this.data = data;
        this.localTypes = localTypes;
        this.maxStackSize = maxStackSize;
        this.extraData = extraData;
        this.numLocals = localTypes.length;
        this.resultTypes = resultTypes;
        this.resultCount = resultTypes.length;
    }

    public WasmFunction function() {
        return function;
    }

    public byte[] data() {
        return data;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public byte localType(int index) {
        return localTypes[index];
    }

    public int numLocals() {
        return numLocals;
    }

    public int functionIndex() {
        return function.index();
    }

    public int[] extraData() {
        return extraData;
    }

    public int resultCount() {
        return resultCount;
    }

    public byte resultType(int index) {
        return resultTypes[index];
    }

    public void errorBranch() {
        errorBranch.enter();
    }

    @Override
    public String toString() {
        return "wasm-code-entry:" + functionIndex();
    }
}
