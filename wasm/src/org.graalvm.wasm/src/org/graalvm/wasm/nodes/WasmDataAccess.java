/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.nodes;

import com.oracle.truffle.api.frame.MaterializedFrame;

/**
 * Interface for accessing the data needed for debugging.
 */
public interface WasmDataAccess {

    boolean isValidStackIndex(MaterializedFrame frame, int index);

    int loadI32FromStack(MaterializedFrame frame, int index);

    long loadI64FromStack(MaterializedFrame frame, int index);

    float loadF32FromStack(MaterializedFrame frame, int index);

    double loadF64FromStack(MaterializedFrame frame, int index);

    boolean isValidLocalIndex(MaterializedFrame frame, int index);

    int loadI32FromLocals(MaterializedFrame frame, int index);

    long loadI64FromLocals(MaterializedFrame frame, int index);

    float loadF32FromLocals(MaterializedFrame frame, int index);

    double loadF64FromLocals(MaterializedFrame frame, int index);

    boolean isValidGlobalIndex(int index);

    int loadI32FromGlobals(MaterializedFrame frame, int index);

    long loadI64FromGlobals(MaterializedFrame frame, int index);

    float loadF32FromGlobals(MaterializedFrame frame, int index);

    double loadF64FromGlobals(MaterializedFrame frame, int index);

    boolean isValidMemoryAddress(MaterializedFrame frame, long address, int length);

    byte loadI8FromMemory(MaterializedFrame frame, long address);

    short loadI16FromMemory(MaterializedFrame frame, long address);

    int loadI32FromMemory(MaterializedFrame frame, long address);

    long loadI64FromMemory(MaterializedFrame frame, long address);

    float loadF32FromMemory(MaterializedFrame frame, long address);

    double loadF64FromMemory(MaterializedFrame frame, long address);

    String loadStringFromMemory(MaterializedFrame frame, long address, int length);
}
