/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.op;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.LLVMManagedCompareNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.LLVMNativeCompareNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.LLVMNegateNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.LLVMObjectEqualsNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.LLVMPointToSameObjectNodeGen;
import com.oracle.truffle.llvm.nodes.op.ToComparableValue.ManagedToComparableValue;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMPointerCompareNode extends LLVMAbstractCompareNode {
    private final NativePointerCompare op;

    public LLVMPointerCompareNode(NativePointerCompare op) {
        this.op = op;
    }

    @Specialization(guards = {"libA.guard(a)", "libB.guard(b)"})
    protected boolean doCached(Object a, Object b,
                    @Cached("createCached(a)") LLVMObjectNativeLibrary libA,
                    @Cached("createCached(b)") LLVMObjectNativeLibrary libB,
                    @Cached("createCompare()") LLVMNativeCompareNode compare) {
        return compare.execute(a, libA, b, libB, op);
    }

    @Specialization(replaces = "doCached", guards = {"lib.guard(a)", "lib.guard(b)"})
    protected boolean doGeneric(Object a, Object b,
                    @Cached("createGeneric()") LLVMObjectNativeLibrary lib,
                    @Cached("createCompare()") LLVMNativeCompareNode compare) {
        return compare.execute(a, lib, b, lib, op);
    }

    @TruffleBoundary
    protected static LLVMNativeCompareNode createCompare() {
        return LLVMNativeCompareNodeGen.create();
    }

    public static LLVMAbstractCompareNode create(Kind kind, LLVMExpressionNode l, LLVMExpressionNode r) {
        switch (kind) {
            case SLT:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long a, long b) {
                        return a < b;
                    }
                }, l, r);
            case SLE:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long a, long b) {
                        return a <= b;
                    }
                }, l, r);
            case ULE:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long a, long b) {
                        return Long.compareUnsigned(a, b) <= 0;
                    }
                }, l, r);
            case ULT:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long a, long b) {
                        return Long.compareUnsigned(a, b) < 0;
                    }
                }, l, r);
            case EQ:
                return LLVMAddressEqualsNodeGen.create(l, r);
            case NEQ:
                return LLVMNegateNodeGen.create(LLVMAddressEqualsNodeGen.create(null, null), l, r);
            default:
                throw new AssertionError();

        }
    }

    public enum Kind {
        ULT,
        ULE,
        SLT,
        SLE,
        EQ,
        NEQ,
    }

    protected abstract static class NativePointerCompare {
        abstract boolean compare(long a, long b);
    }

    abstract static class LLVMNativeCompareNode extends LLVMNode {
        abstract boolean execute(Object a, LLVMObjectNativeLibrary libA, Object b, LLVMObjectNativeLibrary libB, NativePointerCompare op);

        @Specialization(guards = {"libA.isPointer(a)", "libB.isPointer(b)"})
        protected boolean doPointerPointer(Object a, LLVMObjectNativeLibrary libA, Object b, LLVMObjectNativeLibrary libB, NativePointerCompare op) {
            try {
                return op.compare(libA.asPointer(a), libB.asPointer(b));
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }

        @Specialization(guards = "!libA.isPointer(a) || !libB.isPointer(b)")
        protected boolean doOther(Object a, LLVMObjectNativeLibrary libA, Object b, LLVMObjectNativeLibrary libB, NativePointerCompare op,
                        @Cached("create()") LLVMManagedCompareNode managedCompare) {
            return managedCompare.execute(a, libA, b, libB, op);
        }
    }

    /**
     * Whenever we used {@link ManagedToComparableValue} in this class, we only convert the managed
     * object to a long value and ignore the pointer offset (including the offset would increase the
     * probability of collisions). However, we can only ignore the offset safely when we know that
     * both pointers do not point to the same object.
     */
    abstract static class LLVMManagedCompareNode extends LLVMNode {
        private static final long TYPICAL_POINTER = 0x00007f0000000000L;

        abstract boolean execute(Object a, LLVMObjectNativeLibrary libA, Object b, LLVMObjectNativeLibrary libB, NativePointerCompare op);

        @Specialization(guards = {"pointToSameObject.execute(a, b)"})
        protected boolean doForeign(LLVMManagedPointer a, @SuppressWarnings("unused") LLVMObjectNativeLibrary libA,
                        LLVMManagedPointer b, @SuppressWarnings("unused") LLVMObjectNativeLibrary libB, NativePointerCompare op,
                        @Cached("create()") @SuppressWarnings("unused") LLVMPointToSameObjectNode pointToSameObject) {
            // when comparing pointers to the same object, it is not sufficient to simply compare
            // the offsets if we have an unsigned comparison and one of the offsets is negative. So,
            // we add a "typical" pointer value to both offsets and compare the resulting values.
            return op.compare(TYPICAL_POINTER + a.getOffset(), TYPICAL_POINTER + b.getOffset());
        }

        @Specialization(guards = "!pointToSameObject.execute(a, b)")
        protected boolean doForeign(LLVMManagedPointer a, @SuppressWarnings("unused") LLVMObjectNativeLibrary libA,
                        LLVMManagedPointer b, @SuppressWarnings("unused") LLVMObjectNativeLibrary libB, NativePointerCompare op,
                        @Cached("create()") @SuppressWarnings("unused") LLVMPointToSameObjectNode pointToSameObject,
                        @Cached("createIgnoreOffset()") ManagedToComparableValue convertA,
                        @Cached("createIgnoreOffset()") ManagedToComparableValue convertB) {
            return op.compare(convertA.executeWithTarget(a), convertB.executeWithTarget(b));
        }

        @Specialization(guards = "pointToSameObject.execute(a, b)")
        protected boolean doVirtual(LLVMVirtualAllocationAddress a, @SuppressWarnings("unused") LLVMObjectNativeLibrary libA,
                        LLVMVirtualAllocationAddress b, @SuppressWarnings("unused") LLVMObjectNativeLibrary libB, NativePointerCompare op,
                        @Cached("create()") @SuppressWarnings("unused") LLVMPointToSameObjectNode pointToSameObject) {
            return op.compare(TYPICAL_POINTER + a.getOffset(), TYPICAL_POINTER + b.getOffset());
        }

        @Specialization(guards = "!pointToSameObject.execute(a, b)")
        protected boolean doVirtual(LLVMVirtualAllocationAddress a, @SuppressWarnings("unused") LLVMObjectNativeLibrary libA,
                        LLVMVirtualAllocationAddress b, @SuppressWarnings("unused") LLVMObjectNativeLibrary libB, NativePointerCompare op,
                        @Cached("create()") @SuppressWarnings("unused") LLVMPointToSameObjectNode pointToSameObject,
                        @Cached("createIgnoreOffset()") ManagedToComparableValue convertA,
                        @Cached("createIgnoreOffset()") ManagedToComparableValue convertB) {
            return op.compare(convertA.executeWithTarget(a), convertB.executeWithTarget(b));
        }

        @Specialization(guards = "libA.isPointer(a)")
        protected boolean doManagedNative(Object a, LLVMObjectNativeLibrary libA, Object b, LLVMObjectNativeLibrary libB, NativePointerCompare op,
                        @Cached("createIgnoreOffset()") ManagedToComparableValue convert) {
            assert !libB.isPointer(b);
            try {
                return op.compare(libA.asPointer(a), convert.executeWithTarget(b));
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        @Specialization(guards = "libB.isPointer(b)")
        protected boolean doManagedNative(LLVMManagedPointer a, LLVMObjectNativeLibrary libA, Object b, LLVMObjectNativeLibrary libB, NativePointerCompare op,
                        @Cached("createIgnoreOffset()") ManagedToComparableValue convert) {
            assert !libA.isPointer(a);
            try {
                return op.compare(convert.executeWithTarget(a), libB.asPointer(b));
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        @TruffleBoundary
        public static LLVMManagedCompareNode create() {
            return LLVMManagedCompareNodeGen.create();
        }
    }

    public abstract static class LLVMPointToSameObjectNode extends LLVMNode {
        public abstract boolean execute(Object a, Object b);

        @Specialization
        protected boolean pointToSameObjectCached(LLVMManagedPointer a, LLVMManagedPointer b,
                        @Cached("create()") LLVMObjectEqualsNode equalsNode) {
            return equalsNode.execute(a.getObject(), b.getObject());
        }

        @Specialization
        protected boolean pointToSameObject(LLVMVirtualAllocationAddress a, LLVMVirtualAllocationAddress b) {
            return a.getObject() == b.getObject();
        }

        @TruffleBoundary
        public static LLVMPointToSameObjectNode create() {
            return LLVMPointToSameObjectNodeGen.create();
        }
    }

    /**
     * Uses an inline cache to devirtualize the virtual call to equals.
     */
    public abstract static class LLVMObjectEqualsNode extends LLVMNode {
        public abstract boolean execute(Object a, Object b);

        @Specialization(guards = "cachedClassA == getObjectClass(a)")
        protected boolean pointToSameForeignObjectCached(LLVMTypedForeignObject a, LLVMTypedForeignObject b,
                        @Cached("getObjectClass(a)") Class<?> cachedClassA) {
            return CompilerDirectives.castExact(a.getForeign(), cachedClassA).equals(b.getForeign());
        }

        @Specialization(replaces = "pointToSameForeignObjectCached")
        protected boolean pointToSameForeignObject(LLVMTypedForeignObject a, LLVMTypedForeignObject b) {
            return a.equals(b);
        }

        @Specialization(guards = "!isTypedForeignObject(a)")
        protected boolean pointToDifferentObjects(@SuppressWarnings("unused") Object a, @SuppressWarnings("unused") LLVMTypedForeignObject b) {
            return false;
        }

        @Specialization(guards = "!isTypedForeignObject(b)")
        protected boolean pointToDifferentObjects(@SuppressWarnings("unused") LLVMTypedForeignObject a, @SuppressWarnings("unused") Object b) {
            return false;
        }

        @Specialization
        protected boolean pointToSameDynamicObject(DynamicObject a, DynamicObject b) {
            // workaround for Graal issue GR-12757 - this removes the redundant checks from the
            // compiler graph
            return a.equals(b);
        }

        @Specialization(guards = {"!isTypedForeignObject(a)", "!isTypedForeignObject(b)", "cachedClass == a.getClass()"}, replaces = "pointToSameDynamicObject")
        protected boolean pointToSameObjectCached(Object a, Object b,
                        @Cached("a.getClass()") Class<?> cachedClass) {
            return CompilerDirectives.castExact(a, cachedClass).equals(b);
        }

        @Specialization(guards = {"!isTypedForeignObject(a)", "!isTypedForeignObject(b)"}, replaces = "pointToSameObjectCached")
        protected boolean pointToSameObject(Object a, Object b) {
            return a.equals(b);
        }

        protected static Class<?> getObjectClass(LLVMTypedForeignObject foreignObject) {
            return foreignObject.getForeign().getClass();
        }

        protected static boolean isTypedForeignObject(Object object) {
            return object instanceof LLVMTypedForeignObject;
        }

        public static LLVMObjectEqualsNode create() {
            return LLVMObjectEqualsNodeGen.create();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMNegateNode extends LLVMAbstractCompareNode {
        @Child private LLVMAbstractCompareNode booleanExpression;

        LLVMNegateNode(LLVMAbstractCompareNode booleanExpression) {
            this.booleanExpression = booleanExpression;
        }

        @Specialization
        protected boolean doCompare(Object a, Object b) {
            return !booleanExpression.executeWithTarget(a, b);
        }
    }
}
