/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotBoxedPredicateNodeGen.MatchForeignNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

@NodeChild(value = "object", type = LLVMExpressionNode.class)
public abstract class LLVMPolyglotBoxedPredicate extends LLVMIntrinsic {

    public abstract static class Predicate extends LLVMNode {

        abstract boolean execute(Object obj);
    }

    public static final class IsNumber extends Predicate {

        @Override
        boolean execute(Object obj) {
            return obj instanceof Number;
        }
    }

    public static final class IsBoolean extends Predicate {

        @Override
        boolean execute(Object obj) {
            return obj instanceof Boolean;
        }
    }

    public static final class IsString extends Predicate {

        @Override
        boolean execute(Object obj) {
            return obj instanceof String;
        }
    }

    public abstract static class FitsInI8 extends Predicate {

        @Specialization
        boolean doByte(@SuppressWarnings("unused") byte b) {
            return true;
        }

        @Specialization
        boolean doShort(short s) {
            byte b = (byte) s;
            return s == b;
        }

        @Specialization
        boolean doInt(int i) {
            byte b = (byte) i;
            return i == b;
        }

        @Specialization
        boolean doLong(long l) {
            byte b = (byte) l;
            return l == b;
        }

        @Specialization
        boolean doFloat(float f) {
            if (Float.isFinite(f)) {
                byte b = (byte) f;
                return f == b;
            } else {
                return false;
            }
        }

        @Specialization
        boolean doDouble(double d) {
            if (Double.isFinite(d)) {
                byte b = (byte) d;
                return d == b;
            } else {
                return false;
            }
        }

        @Fallback
        boolean doOther(@SuppressWarnings("unused") Object o) {
            return false;
        }
    }

    public abstract static class FitsInI16 extends Predicate {

        @Specialization
        boolean doByte(@SuppressWarnings("unused") byte b) {
            return true;
        }

        @Specialization
        boolean doShort(@SuppressWarnings("unused") short s) {
            return true;
        }

        @Specialization
        boolean doInt(int i) {
            short s = (short) i;
            return i == s;
        }

        @Specialization
        boolean doLong(long l) {
            short s = (short) l;
            return l == s;
        }

        @Specialization
        boolean doFloat(float f) {
            if (Float.isFinite(f)) {
                short s = (short) f;
                return f == s;
            } else {
                return false;
            }
        }

        @Specialization
        boolean doDouble(double d) {
            if (Double.isFinite(d)) {
                short s = (short) d;
                return d == s;
            } else {
                return false;
            }
        }

        @Fallback
        boolean doOther(@SuppressWarnings("unused") Object o) {
            return false;
        }
    }

    public abstract static class FitsInI32 extends Predicate {

        @Specialization
        boolean doByte(@SuppressWarnings("unused") byte b) {
            return true;
        }

        @Specialization
        boolean doShort(@SuppressWarnings("unused") short s) {
            return true;
        }

        @Specialization
        boolean doInt(@SuppressWarnings("unused") int i) {
            return true;
        }

        @Specialization
        boolean doLong(long l) {
            int i = (int) l;
            return l == i;
        }

        @Specialization
        boolean doFloat(float f) {
            if (Float.isFinite(f)) {
                int i = (int) f;
                return f == i;
            } else {
                return false;
            }
        }

        @Specialization
        boolean doDouble(double d) {
            if (Double.isFinite(d)) {
                int i = (int) d;
                return d == i;
            } else {
                return false;
            }
        }

        @Fallback
        boolean doOther(@SuppressWarnings("unused") Object o) {
            return false;
        }
    }

    public abstract static class FitsInI64 extends Predicate {

        @Specialization
        boolean doByte(@SuppressWarnings("unused") byte b) {
            return true;
        }

        @Specialization
        boolean doShort(@SuppressWarnings("unused") short s) {
            return true;
        }

        @Specialization
        boolean doInt(@SuppressWarnings("unused") int i) {
            return true;
        }

        @Specialization
        boolean doLong(@SuppressWarnings("unused") long l) {
            return true;
        }

        @Specialization
        boolean doFloat(float f) {
            if (Float.isFinite(f)) {
                long l = (long) f;
                return f == l;
            } else {
                return false;
            }
        }

        @Specialization
        boolean doDouble(double d) {
            if (Double.isFinite(d)) {
                long l = (long) d;
                return d == l;
            } else {
                return false;
            }
        }

        @Fallback
        boolean doOther(@SuppressWarnings("unused") Object o) {
            return false;
        }
    }

    public abstract static class FitsInFloat extends Predicate {

        @Specialization
        boolean doByte(@SuppressWarnings("unused") byte b) {
            return true;
        }

        @Specialization
        boolean doShort(@SuppressWarnings("unused") short s) {
            return true;
        }

        @Specialization
        boolean doInt(int i) {
            float f = i;
            return i == (int) f;
        }

        @Specialization
        boolean doLong(long l) {
            float f = l;
            return l == (long) f;
        }

        @Specialization
        boolean doFloat(@SuppressWarnings("unused") float f) {
            return true;
        }

        @Specialization
        boolean doDouble(double d) {
            float f = (float) d;
            return d == f;
        }

        @Fallback
        boolean doOther(@SuppressWarnings("unused") Object o) {
            return false;
        }
    }

    public abstract static class FitsInDouble extends Predicate {

        @Specialization
        boolean doByte(@SuppressWarnings("unused") byte b) {
            return true;
        }

        @Specialization
        boolean doShort(@SuppressWarnings("unused") short s) {
            return true;
        }

        @Specialization
        boolean doInt(@SuppressWarnings("unused") int i) {
            return true;
        }

        @Specialization
        boolean doLong(long l) {
            double d = l;
            return l == (long) d;
        }

        @Specialization
        boolean doFloat(@SuppressWarnings("unused") float f) {
            return true;
        }

        @Specialization
        boolean doDouble(@SuppressWarnings("unused") double d) {
            return true;
        }

        @Fallback
        boolean doOther(@SuppressWarnings("unused") Object o) {
            return false;
        }
    }

    final Predicate predicate;

    protected LLVMPolyglotBoxedPredicate(Predicate predicate) {
        this.predicate = predicate;
    }

    @Specialization
    boolean matchManaged(LLVMManagedPointer object,
                    @Cached("createOptional()") LLVMAsForeignNode asForeign,
                    @Cached("create()") MatchForeign match) {
        TruffleObject foreign = asForeign.execute(object);
        return match.execute(foreign, predicate);
    }

    @Specialization
    boolean matchBoxedPrimitive(LLVMBoxedPrimitive prim) {
        return predicate.execute(prim.getValue());
    }

    @Specialization
    boolean matchString(String str) {
        return predicate.execute(str);
    }

    @Fallback
    public boolean fallback(@SuppressWarnings("unused") Object object) {
        return false;
    }

    abstract static class MatchForeign extends LLVMNode {

        @Child Node isBoxed = Message.IS_BOXED.createNode();
        @Child Node unbox = Message.UNBOX.createNode();

        protected abstract boolean execute(TruffleObject obj, Predicate predicate);

        @Specialization(guards = "isBoxed(obj)")
        protected boolean matchBoxed(TruffleObject obj, Predicate predicate) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, obj);
                return predicate.execute(unboxed);
            } catch (UnsupportedMessageException ex) {
                throw ex.raise();
            }
        }

        @Specialization(guards = "!isBoxed(obj)")
        @SuppressWarnings("unused")
        protected boolean matchNotBoxed(TruffleObject obj, Predicate predicate) {
            return false;
        }

        protected boolean isBoxed(TruffleObject obj) {
            return obj != null && ForeignAccess.sendIsBoxed(isBoxed, obj);
        }

        public static MatchForeign create() {
            return MatchForeignNodeGen.create();
        }
    }
}
