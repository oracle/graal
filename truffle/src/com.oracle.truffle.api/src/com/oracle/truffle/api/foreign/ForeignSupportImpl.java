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

import com.oracle.truffle.api.impl.Accessor.ForeignSupport;

import java.lang.invoke.MethodHandle;

/**
 * Encapsulates access to restricted {@code java.lang.foreign} functions. The motivation for this is
 * to pass native access from implementation modules like NFI Panama to the Truffle API module. The
 * embedder needs to enable native access only for Truffle API, which then re-exports the restricted
 * functions to known friendly modules. We cannot use {@code java.lang.foreign} types in method
 * signatures because Truffle API still supports JDK 17.
 */
final class ForeignSupportImpl extends ForeignSupport {

    /**
     * Loads a library with the given name (if not already loaded) and creates a symbol lookup for
     * symbols in that library.
     * 
     * @throws IllegalStateException if {@code arena.scope().isAlive() == false}
     * @throws WrongThreadException if {@code arena} is a confined arena, and this method is called
     *             from a thread {@code T}, other than the arena's owner thread
     * @throws IllegalArgumentException if {@code name} does not identify a valid library
     * @throws IllegalCallerException if Truffle module {@code org.graalvm.truffle} does not have
     *             native access enabled
     */
    @Override
    public Object libraryLookup(String libraryName, Object arena) {
        return ForeignUtil.libraryLookup(libraryName, arena);
    }

    /**
     * Creates a method handle that is used to call a foreign function with {@code symbolName} and
     * {@code functionDescriptor} signature.
     *
     * @throws java.util.NoSuchElementException if no symbol address can be found for
     *             {@code symbolName}
     * @throws IllegalArgumentException if {@code functionDescriptor} is not supported
     * @throws IllegalCallerException if Truffle module {@code org.graalvm.truffle} does not have
     *             native access enabled
     */
    @Override
    public MethodHandle downcallHandle(String symbolName, Object functionDescriptor) {
        return ForeignUtil.downcallHandle(symbolName, functionDescriptor);
    }

    /**
     * Creates a method handle that is used to call a foreign function with
     * {@code functionDescriptor} signature.
     * 
     * @throws IllegalArgumentException if {@code functionDescriptor} is not supported
     * @throws IllegalCallerException if Truffle module {@code org.graalvm.truffle} does not have
     *             native access enabled
     */
    @Override
    public MethodHandle downcallHandle(Object functionDescriptor) {
        return ForeignUtil.downcallHandle(functionDescriptor);
    }

    /**
     * Creates an upcall stub which can be passed to other foreign functions as a function pointer,
     * associated with the given arena. Calling such a function pointer from foreign code will
     * result in the execution of the provided method handle.
     * 
     * @throws IllegalArgumentException if {@code functionDescriptor} is not supported. If the type
     *             of {@code methodHandle} is incompatible with the type {@code functionDescriptor}.
     *             If it is determined that the target method handle can throw an exception.
     * @throws IllegalStateException if {@code arena.scope().isAlive() == false}
     * @throws WrongThreadException if {@code arena} is a confined arena, and this method is called
     *             from a thread {@code T}, other than the arena's owner thread
     * @throws IllegalCallerException if Truffle module {@code org.graalvm.truffle} does not have
     *             native access enabled
     */
    @Override
    public Object upcallStub(MethodHandle methodHandle, Object functionDescriptor, Object arena) {
        return ForeignUtil.upcallStub(methodHandle, functionDescriptor, arena);
    }

    /**
     * Returns a new memory segment that has the same address and scope as this segment, but with
     * the provided size.
     * 
     * @throws IllegalArgumentException if {@code newSize < 0}
     * @throws UnsupportedOperationException if this segment is not a {@linkplain #isNative()
     *             native} segment
     * @throws IllegalCallerException if Truffle module {@code org.graalvm.truffle} does not have
     *             native access enabled
     */
    @Override
    public Object reinterpret(Object memorySegment, long newSize) {
        return ForeignUtil.reinterpret(memorySegment, newSize);
    }
}
