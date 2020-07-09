/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.nodes.util;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMAddressEqualsNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.spi.ReferenceLibrary;

/**
 * Helper node to determine whether two managed objects are reference equal.
 *
 * This node operates on the objects that are stored in {@link LLVMManagedPointer#getObject}, not on
 * pointers themselves. See {@link LLVMAddressEqualsNode} for comparing two pointers.
 *
 * For foreign objects, this node delegates to the {@link InteropLibrary#isIdentical} message.
 */
@SuppressWarnings("deprecation") // for backwards compatibility
@GenerateUncached
public abstract class LLVMSameObjectNode extends LLVMNode {

    public abstract boolean execute(Object a, Object b);

    /**
     * This is intentionally overlapping with the doForeign specialization. There is no need to
     * guard for !isForeign here. If we get two identity-equal (possibly wrapped) foreign objects
     * here, it's still correct to return {@code true}.
     */
    @Specialization(guards = "a == b")
    boolean doSame(Object a, Object b) {
        assert a == b;
        return true;
    }

    @Specialization(limit = "3", guards = {"aForeigns.isForeign(a)", "bForeigns.isForeign(b)"})
    boolean doForeign(Object a, Object b,
                    @CachedLibrary("a") LLVMAsForeignLibrary aForeigns,
                    @CachedLibrary("b") LLVMAsForeignLibrary bForeigns,
                    @Cached CompareForeignNode compare) {
        return compare.execute(aForeigns.asForeign(a), bForeigns.asForeign(b));
    }

    @Fallback
    boolean doNotSame(Object a, Object b) {
        assert a != b;
        return false;
    }

    @GenerateUncached
    abstract static class CompareForeignNode extends LLVMNode {

        abstract boolean execute(Object a, Object b);

        /**
         * All pointers need to have an identity. Since in interop, identity might be undefined, we
         * use Java object identity as a fallback.
         *
         * Semantically, we want to use the interop identity if it is defined, and fall back to the
         * Java object identity otherwise.
         *
         * In the implementation, we do it the other way round because of efficiency. The result is
         * the same. If the two arguments are Java object identical, and interop identity is defined
         * on that object, we can safely return true here without actually calling
         * {@link InteropLibrary#isIdentical}, because interop identity is reflexive.
         *
         * @see InteropLibrary#isIdentical
         */
        @Specialization(guards = "a == b")
        boolean doSame(Object a, Object b) {
            assert a == b;
            return true;
        }

        // for backwards compatibility
        @Specialization(limit = "3", guards = {"a != b", "references.isSame(a, b)"})
        boolean doReferenceLibrary(Object a, Object b,
                        @CachedLibrary("a") ReferenceLibrary references) {
            assert references.isSame(a, b);
            return true;
        }

        @Specialization(limit = "3", guards = {"a != b", "!references.isSame(a, b)"})
        boolean doIdentical(Object a, Object b,
                        @CachedLibrary("a") ReferenceLibrary references,
                        @CachedLibrary("a") InteropLibrary aInterop,
                        @CachedLibrary("b") InteropLibrary bInterop) {
            assert !references.isSame(a, b);
            return aInterop.isIdentical(a, b, bInterop);
        }
    }
}
