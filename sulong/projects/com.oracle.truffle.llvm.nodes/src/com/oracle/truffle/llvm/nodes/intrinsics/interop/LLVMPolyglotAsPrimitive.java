/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotAsPrimitiveFactory.AsBooleanNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotAsPrimitiveFactory.AsDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotAsPrimitiveFactory.AsFloatNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotAsPrimitiveFactory.AsI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotAsPrimitiveFactory.AsI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotAsPrimitiveFactory.AsI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotAsPrimitiveFactory.AsI8NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild(type = LLVMAsForeignNode.class)
public abstract class LLVMPolyglotAsPrimitive extends LLVMIntrinsic {

    public abstract static class AsBoolean extends LLVMPolyglotAsPrimitive {

        public static LLVMPolyglotAsPrimitive create(LLVMExpressionNode arg) {
            return AsBooleanNodeGen.create(LLVMAsForeignNode.create(arg));
        }

        @Specialization(limit = "3")
        boolean asBoolean(Object foreign,
                        @CachedLibrary("foreign") InteropLibrary interop,
                        @Cached BranchProfile exception) {
            try {
                return interop.asBoolean(foreign);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Argument to polyglot_as_boolean can not be convented to boolean.");
            }
        }
    }

    public abstract static class AsI8 extends LLVMPolyglotAsPrimitive {

        public static LLVMPolyglotAsPrimitive create(LLVMExpressionNode arg) {
            return AsI8NodeGen.create(LLVMAsForeignNode.create(arg));
        }

        @Specialization(limit = "3")
        byte asI8(Object foreign,
                        @CachedLibrary("foreign") InteropLibrary interop,
                        @Cached BranchProfile exception) {
            try {
                return interop.asByte(foreign);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Argument to polyglot_as_i8 can not be convented to i8.");
            }
        }
    }

    public abstract static class AsI16 extends LLVMPolyglotAsPrimitive {

        public static LLVMPolyglotAsPrimitive create(LLVMExpressionNode arg) {
            return AsI16NodeGen.create(LLVMAsForeignNode.create(arg));
        }

        @Specialization(limit = "3")
        short asI16(Object foreign,
                        @CachedLibrary("foreign") InteropLibrary interop,
                        @Cached BranchProfile exception) {
            try {
                return interop.asShort(foreign);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Argument to polyglot_as_i16 can not be convented to i16.");
            }
        }
    }

    public abstract static class AsI32 extends LLVMPolyglotAsPrimitive {

        public static LLVMPolyglotAsPrimitive create(LLVMExpressionNode arg) {
            return AsI32NodeGen.create(LLVMAsForeignNode.create(arg));
        }

        @Specialization(limit = "3")
        int asI32(Object foreign,
                        @CachedLibrary("foreign") InteropLibrary interop,
                        @Cached BranchProfile exception) {
            try {
                return interop.asInt(foreign);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Argument to polyglot_as_i32 can not be convented to i32.");
            }
        }
    }

    public abstract static class AsI64 extends LLVMPolyglotAsPrimitive {

        public static LLVMPolyglotAsPrimitive create(LLVMExpressionNode arg) {
            return AsI64NodeGen.create(LLVMAsForeignNode.create(arg));
        }

        @Specialization(limit = "3")
        long asI64(Object foreign,
                        @CachedLibrary("foreign") InteropLibrary interop,
                        @Cached BranchProfile exception) {
            try {
                return interop.asLong(foreign);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Argument to polyglot_as_i64 can not be convented to i64.");
            }
        }
    }

    public abstract static class AsFloat extends LLVMPolyglotAsPrimitive {

        public static LLVMPolyglotAsPrimitive create(LLVMExpressionNode arg) {
            return AsFloatNodeGen.create(LLVMAsForeignNode.create(arg));
        }

        @Specialization(limit = "3")
        float asFloat(Object foreign,
                        @CachedLibrary("foreign") InteropLibrary interop,
                        @Cached BranchProfile exception) {
            try {
                return interop.asFloat(foreign);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Argument to polyglot_as_float can not be convented to float.");
            }
        }
    }

    public abstract static class AsDouble extends LLVMPolyglotAsPrimitive {

        public static LLVMPolyglotAsPrimitive create(LLVMExpressionNode arg) {
            return AsDoubleNodeGen.create(LLVMAsForeignNode.create(arg));
        }

        @Specialization(limit = "3")
        double asDouble(Object foreign,
                        @CachedLibrary("foreign") InteropLibrary interop,
                        @Cached BranchProfile exception) {
            try {
                return interop.asDouble(foreign);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Argument to polyglot_as_double can not be convented to double.");
            }
        }
    }
}
