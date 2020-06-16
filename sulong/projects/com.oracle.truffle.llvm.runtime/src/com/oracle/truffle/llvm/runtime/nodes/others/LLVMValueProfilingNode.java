/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.others;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ByteValueProfile;
import com.oracle.truffle.api.profiles.DoubleValueProfile;
import com.oracle.truffle.api.profiles.FloatValueProfile;
import com.oracle.truffle.api.profiles.IntValueProfile;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMValueProfilingNodeFactory.LLVMDoubleProfiledValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMValueProfilingNodeFactory.LLVMFloatProfiledValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMValueProfilingNodeFactory.LLVMI16ProfiledValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMValueProfilingNodeFactory.LLVMI1ProfiledValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMValueProfilingNodeFactory.LLVMI32ProfiledValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMValueProfilingNodeFactory.LLVMI64ProfiledValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMValueProfilingNodeFactory.LLVMI8ProfiledValueNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.profiling.BooleanValueProfile;
import com.oracle.truffle.llvm.runtime.profiling.ShortValueProfile;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChild
public abstract class LLVMValueProfilingNode extends LLVMExpressionNode {

    public abstract Object executeWithTarget(Object value);

    public static LLVMExpressionNode create(LLVMExpressionNode value, Type type) {
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return LLVMI1ProfiledValueNodeGen.create(value);
                case I8:
                    return LLVMI8ProfiledValueNodeGen.create(value);
                case I16:
                    return LLVMI16ProfiledValueNodeGen.create(value);
                case I32:
                    return LLVMI32ProfiledValueNodeGen.create(value);
                case I64:
                    return LLVMI64ProfiledValueNodeGen.create(value);
                case FLOAT:
                    return LLVMFloatProfiledValueNodeGen.create(value);
                case DOUBLE:
                    return LLVMDoubleProfiledValueNodeGen.create(value);
                default:
                    return value;
            }
        } else if (type instanceof PointerType) {
            return LLVMI64ProfiledValueNodeGen.create(value);
        }
        return value;
    }

    abstract static class LLVMI1ProfiledValueNode extends LLVMValueProfilingNode {

        private final BooleanValueProfile profile = BooleanValueProfile.create();

        @Specialization
        protected boolean doI1(boolean value) {
            return profile.profile(value);
        }
    }

    abstract static class LLVMI8ProfiledValueNode extends LLVMValueProfilingNode {

        private final ByteValueProfile profile = ByteValueProfile.createIdentityProfile();

        @Specialization
        protected byte doI8(byte value) {
            return profile.profile(value);
        }
    }

    abstract static class LLVMI16ProfiledValueNode extends LLVMValueProfilingNode {

        private final ShortValueProfile profile = ShortValueProfile.create();

        @Specialization
        protected short doI1(short value) {
            return profile.profile(value);
        }
    }

    abstract static class LLVMI32ProfiledValueNode extends LLVMValueProfilingNode {

        private final IntValueProfile profile = IntValueProfile.createIdentityProfile();

        @Specialization
        protected int doI32(int value) {
            return profile.profile(value);
        }
    }

    abstract static class LLVMI64ProfiledValueNode extends LLVMValueProfilingNode {
        private static final byte UNINITIALIZED = 0;
        private static final byte SPECIALIZED_LONG = 1;
        private static final byte SPECIALIZED_POINTER = 2;
        private static final byte GENERIC = 3;

        @CompilationFinal private byte state = UNINITIALIZED;
        @CompilationFinal private LLVMPointer cachedPointer;
        @CompilationFinal private long cachedLong;

        @Specialization
        protected long doI64(long value) {
            byte localState = this.state;
            if (localState != GENERIC) {
                if (localState == SPECIALIZED_LONG) {
                    long v = cachedLong;
                    if (v == value) {
                        return v;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (localState == UNINITIALIZED) {
                    this.cachedLong = value;
                    this.state = SPECIALIZED_LONG;
                } else {
                    this.state = GENERIC;
                }
            }
            return value;
        }

        @Specialization
        protected LLVMPointer doPointer(LLVMPointer value) {
            byte localState = this.state;
            if (localState != GENERIC) {
                if (localState == SPECIALIZED_POINTER) {
                    LLVMPointer v = cachedPointer;
                    if (v.isSame(value)) {
                        return v.export(value.getExportType());
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (localState == UNINITIALIZED && isCachablePointer(value)) {
                    this.cachedPointer = value;
                    this.state = SPECIALIZED_POINTER;
                } else {
                    this.state = GENERIC;
                }
            }
            return value;
        }

        private static boolean isCachablePointer(LLVMPointer value) {
            if (LLVMNativePointer.isInstance(value)) {
                // Native pointers can always be cached.
                return true;
            } else {
                // Managed pointers are only cached if they point to a white-listed class (we must
                // not create a memory leak nor must we cache @ValueType objects)
                assert LLVMManagedPointer.isInstance(value);
                LLVMManagedPointer pointer = LLVMManagedPointer.cast(value);
                Object object = pointer.getObject();
                return object instanceof LLVMGlobalContainer || object instanceof LLVMFunctionDescriptor;
            }
        }

        @Fallback
        protected Object noCache(Object value) {
            return value;
        }
    }

    abstract static class LLVMFloatProfiledValueNode extends LLVMValueProfilingNode {

        private final FloatValueProfile profile = FloatValueProfile.createRawIdentityProfile();

        @Specialization
        protected float doFloat(float value) {
            return profile.profile(value);
        }
    }

    abstract static class LLVMDoubleProfiledValueNode extends LLVMValueProfilingNode {

        private final DoubleValueProfile profile = DoubleValueProfile.createRawIdentityProfile();

        @Specialization
        protected double doFloat(double value) {
            return profile.profile(value);
        }
    }
}
