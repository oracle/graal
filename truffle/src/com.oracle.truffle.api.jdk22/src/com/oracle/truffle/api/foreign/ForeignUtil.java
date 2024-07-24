/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.foreign;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

final class ForeignUtil {

    private ForeignUtil() {
    }

    @SuppressWarnings("restricted")
    public static Object libraryLookup(String libraryName, Object arena) {
        return SymbolLookup.libraryLookup(libraryName, (Arena) arena);
    }

    @SuppressWarnings("restricted")
    public static MethodHandle downcallHandle(String symbolName, Object functionDescriptor) {
        Linker linker = Linker.nativeLinker();
        MemorySegment addr = linker.defaultLookup().findOrThrow(symbolName);
        return linker.downcallHandle(addr, (FunctionDescriptor) functionDescriptor);
    }

    @SuppressWarnings("restricted")
    public static MethodHandle downcallHandle(Object functionDescriptor) {
        return Linker.nativeLinker().downcallHandle((FunctionDescriptor) functionDescriptor);
    }

    @SuppressWarnings("restricted")
    public static Object upcallStub(MethodHandle methodHandle, Object functionDescriptor, Object arena) {
        return Linker.nativeLinker().upcallStub(methodHandle, (FunctionDescriptor) functionDescriptor, (Arena) arena);
    }

    @SuppressWarnings("restricted")
    public static Object reinterpret(Object memorySegment, long newSize) {
        return ((MemorySegment) memorySegment).reinterpret(newSize);
    }
}
