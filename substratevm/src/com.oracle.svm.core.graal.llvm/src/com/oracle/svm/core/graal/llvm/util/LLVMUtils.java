/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.graal.llvm.util;

import static com.oracle.svm.shadowed.org.bytedeco.llvm.global.LLVM.LLVMTypeOf;
import static jdk.graal.compiler.debug.GraalError.shouldNotReachHere;
import static jdk.graal.compiler.debug.GraalError.unimplementedOverride;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.VirtualStackSlot;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.llvm.LLVMGenerator;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMTypeRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.global.LLVM;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class LLVMUtils {
    static final int FALSE = 0;
    static final int TRUE = 1;
    static final long ENUM_ATTRIBUTE_VALUE = 0L;

    public interface LLVMValueWrapper {
        LLVMValueRef get();

        default LLVMTypeRef getType() {
            return LLVMIRBuilder.typeOf(get());
        }
    }

    interface LLVMTypeWrapper {
        LLVMTypeRef get();
    }

    public static LLVMValueRef getVal(Value value) {
        return ((LLVMValueWrapper) value).get();
    }

    public static LLVMTypeRef getType(ValueKind<?> kind) {
        return ((LLVMTypeWrapper) kind.getPlatformKind()).get();
    }

    public static String dumpValues(String prefix, LLVMValueRef... values) {
        StringBuilder builder = new StringBuilder(prefix);
        for (LLVMValueRef value : values) {
            builder.append(" ");
            builder.append(LLVM.LLVMPrintValueToString(value).getString());
        }
        return builder.toString();
    }

    public static String dumpTypes(String prefix, LLVMTypeRef... types) {
        StringBuilder builder = new StringBuilder(prefix);
        for (LLVMTypeRef type : types) {
            builder.append(" ");
            builder.append(LLVM.LLVMPrintTypeToString(type).getString());
        }
        return builder.toString();
    }

    public static class LLVMVariable extends Variable implements LLVMValueWrapper {
        private static int id = 0;

        private LLVMValueRef value;

        public LLVMVariable(ValueKind<?> kind) {
            super(kind, id++);
        }

        public LLVMVariable(LLVMValueRef value) {
            this(LLVMKind.toLIRKind(LLVMTypeOf(value)));

            this.value = value;
        }

        public void set(LLVMValueRef value) {
            assert this.value == null;
            this.value = value;
        }

        @Override
        public LLVMValueRef get() {
            return value;
        }
    }

    public static class LLVMConstant extends ConstantValue implements LLVMValueWrapper {
        private final LLVMValueRef value;

        public LLVMConstant(LLVMValueRef value, Constant constant) {
            super(LLVMKind.toLIRKind(LLVMIRBuilder.typeOf(value)), constant);
            this.value = value;
        }

        @Override
        public LLVMValueRef get() {
            return value;
        }
    }

    public static class LLVMStackSlot extends VirtualStackSlot implements LLVMValueWrapper {
        private static int id = 0;

        private LLVMValueRef value;

        public LLVMStackSlot(LLVMValueRef value) {
            super(id++, LLVMKind.toLIRKind(LLVM.LLVMTypeOf(value)));

            this.value = value;
        }

        @Override
        public LLVMValueRef get() {
            return value;
        }
    }

    /**
     * Delays reading a reserved register until the value is used.
     * <p>
     * A {@code ReadRegisterFloatingNode} may get hoisted above a write to the same reserved
     * register in methods that modify reserved registers. Delaying the read keeps the generated LLVM
     * IR in the original order.
     * <p>
     * A different mode is used for ordinary carrier thread register reads around virtual-thread
     * yields: a continuation may resume on a different carrier thread, so LLVM must not keep a value
     * derived from the old carrier's reserved register live across the yielding call. For
     * constant-offset memory accesses, the backend can emit a side-effecting fixed-register
     * load/store at the use site instead of materializing an ordinary pointer value that LLVM could
     * spill or reuse after resume. This fixed-register mode is deliberately not used for entry-point
     * prologues or methods that modify reserved registers, because those paths need LLVM to see the
     * ordering between {@code llvm.write_register} and {@code llvm.read_register}. For example, a
     * C entry-point prologue first writes the incoming {@code IsolateThread} to the reserved thread
     * register and then initializes the heap-base register from a thread-local load. If that load is
     * emitted as fixed-register inline assembly, LLVM cannot see that it depends on the preceding
     * thread-register write, and the heap-base register can be initialized from the wrong thread
     * value or left uninitialized.
     */
    public static class LLVMPendingSpecialRegisterRead extends LLVMVariable implements LLVMValueWrapper {
        private final LLVMGenerator gen;
        private final LLVMValueRef register;
        private final String registerName;
        private final LLVMValueRef offsetValue;
        private final Integer constantOffset;
        private final boolean useFixedRegisterAccess;

        public LLVMPendingSpecialRegisterRead(LLVMGenerator gen, LLVMValueRef register, String registerName, boolean useFixedRegisterAccess) {
            this(gen, register, registerName, null, null, useFixedRegisterAccess);
        }

        public LLVMPendingSpecialRegisterRead(LLVMPendingSpecialRegisterRead pendingRead, Value offset) {
            this(pendingRead.gen, pendingRead.register, pendingRead.registerName, getVal(offset), asIntOffset(offset), pendingRead.useFixedRegisterAccess);
        }

        private LLVMPendingSpecialRegisterRead(LLVMGenerator gen, LLVMValueRef register, String registerName, LLVMValueRef offsetValue, Integer constantOffset, boolean useFixedRegisterAccess) {
            super(LLVMKind.toLIRKind(gen.getBuilder().wordType()));
            this.gen = gen;
            this.register = register;
            this.registerName = registerName;
            this.offsetValue = offsetValue;
            this.constantOffset = constantOffset;
            this.useFixedRegisterAccess = useFixedRegisterAccess;
        }

        private static Integer asIntOffset(Value offset) {
            if (offset instanceof ConstantValue constantValue && constantValue.isJavaConstant()) {
                JavaConstant constant = constantValue.getJavaConstant();
                long value = constant.asLong();
                if (NumUtil.isInt(value)) {
                    return (int) value;
                }
            }
            return null;
        }

        public String getRegisterName() {
            return registerName;
        }

        public boolean hasConstantOffset() {
            return constantOffset != null;
        }

        public int getConstantOffset() {
            assert hasConstantOffset();
            return constantOffset;
        }

        public boolean useFixedRegisterAccess() {
            return useFixedRegisterAccess;
        }

        @Override
        public LLVMValueRef get() {
            LLVMIRBuilder builder = gen.getBuilder();
            LLVMValueRef value;
            if (useFixedRegisterAccess) {
                /*
                 * Keep the read side-effecting. LLVM may otherwise common a plain llvm.read_register
                 * value and keep a derived thread-local address live across a continuation yield.
                 */
                value = gen.buildInlineGetRegister(registerName);
            } else {
                value = builder.buildReadRegister(register);
            }
            return offsetValue == null ? value : builder.buildGEP(builder.buildIntToPtr(value, builder.rawPointerType()), offsetValue);
        }

        @Override
        public LLVMTypeRef getType() {
            return offsetValue == null ? gen.getBuilder().wordType() : gen.getBuilder().rawPointerType();
        }
    }

    public static class LLVMPendingPtrToInt extends LLVMVariable implements LLVMValueWrapper {
        private final LLVMGenerator gen;
        private final LLVMValueRef val;

        public LLVMPendingPtrToInt(LLVMGenerator gen, LLVMValueRef val) {
            super(LLVMKind.toLIRKind(gen.getBuilder().wordType()));
            this.gen = gen;
            this.val = val;
        }

        @Override
        public LLVMValueRef get() {
            LLVMIRBuilder builder = gen.getBuilder();
            return builder.buildPtrToInt(val);
        }

        @Override
        public LLVMTypeRef getType() {
            return gen.getBuilder().wordType();
        }
    }

    public static class LLVMKindTool implements LIRKindTool {
        private LLVMIRBuilder builder;

        public LLVMKindTool(LLVMIRBuilder builder) {
            this.builder = builder;
        }

        @Override
        public LIRKind getIntegerKind(int bits) {
            return LIRKind.value(new LLVMKind(builder.integerType(bits)));
        }

        @Override
        public LIRKind getFloatingKind(int bits) {
            switch (bits) {
                case 32:
                    return LIRKind.value(new LLVMKind(builder.floatType()));
                case 64:
                    return LIRKind.value(new LLVMKind(builder.doubleType()));
                default:
                    throw shouldNotReachHere("invalid float type"); // ExcludeFromJacocoGeneratedReport
            }
        }

        @Override
        public LIRKind getObjectKind() {
            return LIRKind.reference(new LLVMKind(builder.objectType(false)));
        }

        @Override
        public LIRKind getWordKind() {
            return LIRKind.value(new LLVMKind(builder.wordType()));
        }

        @Override
        public LIRKind getNarrowOopKind() {
            return LIRKind.compressedReference(new LLVMKind(builder.objectType(true)));
        }

        @Override
        public LIRKind getNarrowPointerKind() {
            throw unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
        }
    }

    public static final class LLVMKind implements PlatformKind, LLVMTypeWrapper {
        private final LLVMTypeRef type;

        private LLVMKind(LLVMTypeRef type) {
            this.type = type;
        }

        static LIRKind toLIRKind(LLVMTypeRef type) {
            if (LLVMIRBuilder.isPointerType(type)) {
                if (LLVMIRBuilder.isTrackedPointerType(type)) {
                    if (LLVMIRBuilder.isCompressedPointerType(type)) {
                        return LIRKind.compressedReference(new LLVMKind(type));
                    } else {
                        return LIRKind.reference(new LLVMKind(type));
                    }
                }
            }
            return LIRKind.value(new LLVMKind(type));
        }

        @Override
        public LLVMTypeRef get() {
            return type;
        }

        @Override
        public String name() {
            return LLVM.LLVMPrintTypeToString(type).getString();
        }

        @Override
        public Key getKey() {
            throw unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public int getSizeInBytes() {
            switch (LLVM.LLVMGetTypeKind(type)) {
                case LLVM.LLVMIntegerTypeKind:
                    return NumUtil.roundUp(LLVM.LLVMGetIntTypeWidth(type), 8) / 8;
                case LLVM.LLVMFloatTypeKind:
                    return 4;
                case LLVM.LLVMDoubleTypeKind:
                    return 8;
                case LLVM.LLVMPointerTypeKind:
                    if (LLVMIRBuilder.isObjectType(type) && LLVMIRBuilder.isCompressedPointerType(type)) {
                        return ObjectLayout.singleton().getReferenceSize();
                    }
                    return SubstrateTarget.getWordSize();
                default:
                    throw shouldNotReachHere("invalid kind"); // ExcludeFromJacocoGeneratedReport
            }
        }

        @Override
        public int getVectorLength() {
            return 1;
        }

        @Override
        public char getTypeChar() {
            throw unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
        }
    }
}
