/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import org.graalvm.wasm.exception.WasmJsApiException;
import org.graalvm.wasm.memory.ByteArrayWasmMemory;
import org.graalvm.wasm.memory.WasmMemory;

import static java.lang.Integer.compareUnsigned;
import static org.graalvm.wasm.WasmMath.minUnsigned;
import static org.graalvm.wasm.api.JsConstants.JS_LIMITS;

public class Memory extends Dictionary {
    private final MemoryDescriptor descriptor;
    private final WasmMemory memory;

    public Memory(WasmMemory memory) {
        this.descriptor = new MemoryDescriptor(memory.declaredMinSize(), memory.declaredMaxSize());
        this.memory = memory;
        addMembers(new Object[]{
                        "descriptor", this.descriptor,
                        "grow", new Executable(args -> grow((Integer) args[0])),
                        "buffer", new Executable(args -> memory),
        });
    }

    public static Memory create(int declaredMinSize, int declaredMaxSize) {
        if (compareUnsigned(declaredMinSize, declaredMaxSize) > 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Min memory size exceeds max memory size");
        } else if (compareUnsigned(declaredMinSize, JS_LIMITS.memoryInstanceSizeLimit()) > 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Min memory size exceeds implementation limit");
        }
        final int maxAllowedSize = minUnsigned(declaredMaxSize, JS_LIMITS.memoryInstanceSizeLimit());
        final WasmMemory wasmMemory = new ByteArrayWasmMemory(declaredMinSize, declaredMaxSize, maxAllowedSize);
        return new Memory(wasmMemory);
    }

    public static Memory create(Object descriptor) {
        return create(initial(descriptor), maximum(descriptor));
    }

    private static int initial(Object descriptor) {
        try {
            return (int) InteropLibrary.getUncached().readMember(descriptor, "initial");
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid memory descriptor " + descriptor);
        }
    }

    private static int maximum(Object descriptor) {
        try {
            return (int) InteropLibrary.getUncached().readMember(descriptor, "maximum");
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid memory descriptor " + descriptor);
        }
    }

    public WasmMemory wasmMemory() {
        return memory;
    }

    private long grow(int delta) {
        final long pageSize = memory.size();
        if (!memory.grow(delta)) {
            throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Cannot grow memory above max limit");
        }
        return pageSize;
    }
}
