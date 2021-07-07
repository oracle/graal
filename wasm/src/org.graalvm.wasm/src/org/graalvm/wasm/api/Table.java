/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.Integer.compareUnsigned;
import static org.graalvm.wasm.WasmMath.minUnsigned;
import static org.graalvm.wasm.api.JsConstants.JS_LIMITS;

import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmTable;
import org.graalvm.wasm.WasmVoidResult;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.exception.WasmJsApiException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;

@ExportLibrary(InteropLibrary.class)
public class Table extends Dictionary {
    private final TableDescriptor descriptor;
    private final WasmTable table;

    public Table(WasmTable table) {
        this.descriptor = new TableDescriptor(TableKind.anyfunc.name(), table.declaredMinSize(), table.declaredMaxSize());
        this.table = table;
        addMembers(new Object[]{
                        "descriptor", this.descriptor,
                        "grow", new Executable(args -> grow((Integer) args[0])),
                        "get", new Executable(args -> get((Integer) args[0])),
                        "set", new Executable(args -> set((Integer) args[0], args[1])),
        });
    }

    public static Table create(int declaredMinSize, int declaredMaxSize) {
        if (compareUnsigned(declaredMinSize, declaredMaxSize) > 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Min table size exceeds max memory size");
        } else if (compareUnsigned(declaredMinSize, JS_LIMITS.memoryInstanceSizeLimit()) > 0) {
            throw new WasmJsApiException(WasmJsApiException.Kind.LinkError, "Min table size exceeds implementation limit");
        }
        final int maxAllowedSize = minUnsigned(declaredMaxSize, JS_LIMITS.memoryInstanceSizeLimit());
        final WasmTable wasmTable = new WasmTable(declaredMinSize, declaredMaxSize, maxAllowedSize);
        return new Table(wasmTable);
    }

    public static Table create(Object descriptor) {
        return create(initial(descriptor), maximum(descriptor));
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    @Override
    public boolean isMemberReadable(String member) {
        return member.equals("length") || super.isMemberReadable(member);
    }

    @SuppressWarnings({"unused"})
    @ExportMessage
    @Override
    public Object readMember(String member) throws UnknownIdentifierException {
        if (member.equals("length")) {
            return table.size();
        } else {
            return super.readMember(member);
        }
    }

    private static int initial(Object descriptor) {
        try {
            return (Integer) InteropLibrary.getUncached().readMember(descriptor, "initial");
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid memory descriptor " + descriptor);
        }
    }

    private static int maximum(Object descriptor) {
        try {
            return (Integer) InteropLibrary.getUncached().readMember(descriptor, "maximum");
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.TypeError, "Invalid memory descriptor " + descriptor);
        }
    }

    public WasmTable wasmTable() {
        return table;
    }

    public TableDescriptor descriptor() {
        return descriptor;
    }

    public int grow(int delta) {
        final int size = table.size();
        try {
            table.grow(delta);
        } catch (IllegalArgumentException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, e.getMessage());
        }
        return size;
    }

    public Object get(int index) {
        try {
            final Object result = table.get(index);
            return result == null ? WasmVoidResult.getInstance() : result;
        } catch (IndexOutOfBoundsException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Table index out of bounds: " + e.getMessage());
        }
    }

    public Object set(int index, Object element) {
        final WasmFunctionInstance functionInstance = new WasmFunctionInstance(null, null, Truffle.getRuntime().createCallTarget(new RootNode(WasmContext.getCurrent().language()) {
            @Override
            public Object execute(VirtualFrame frame) {
                if (InteropLibrary.getUncached().isExecutable(element)) {
                    try {
                        return InteropLibrary.getUncached().execute(element, frame.getArguments());
                    } catch (UnsupportedTypeException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw WasmException.format(Failure.UNSPECIFIED_TRAP, "Table element %s has an unsupported type.", element);
                    } catch (ArityException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw WasmException.format(Failure.UNSPECIFIED_TRAP, "Table element %s has unexpected arity.", element);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw WasmException.format(Failure.UNSPECIFIED_TRAP, "Table element %s is not executable.", element);
                    }
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw WasmException.format(Failure.UNSPECIFIED_TRAP, "Table element %s is not executable.", element);
                }
            }
        }));

        try {
            table.set(index, functionInstance);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new WasmJsApiException(WasmJsApiException.Kind.RangeError, "Table index out of bounds: " + e.getMessage());
        }

        return WasmVoidResult.getInstance();
    }

}
