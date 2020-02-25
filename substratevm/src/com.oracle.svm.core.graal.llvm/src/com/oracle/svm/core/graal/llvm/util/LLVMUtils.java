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
import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;
import static org.graalvm.compiler.debug.GraalError.unimplemented;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;

import com.oracle.svm.core.graal.llvm.LLVMGenerator;
import com.oracle.svm.core.graal.llvm.LLVMGenerator.SpecialRegister;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMTypeRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.global.LLVM;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class LLVMUtils {
    static final int FALSE = 0;
    static final int TRUE = 1;

    public interface LLVMValueWrapper {
        LLVMValueRef get();
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

    /*
     * Due to the fact that the LLVM backend handles reading special registers in methods that can
     * modify them as a stack slot load instead of a direct register access, a
     * ReadRegisterFloatingNode may get hoisted above where the thread pointer gets stored in the
     * stack slot, and getting the contents of the stack slot at that point will return an incorrect
     * value. Wrapping this read prevents this by delaying reading the value of the special register
     * until when it's actually needed.
     */
    public static class LLVMPendingSpecialRegisterRead extends LLVMVariable implements LLVMValueWrapper {
        private final LLVMGenerator gen;
        private final SpecialRegister reg;
        private final LLVMValueRef offset;

        public LLVMPendingSpecialRegisterRead(LLVMGenerator gen, SpecialRegister reg) {
            this(gen, reg, null);
        }

        public LLVMPendingSpecialRegisterRead(LLVMPendingSpecialRegisterRead pendingRead, LLVMValueRef offset) {
            this(pendingRead.gen, pendingRead.reg, offset);
        }

        private LLVMPendingSpecialRegisterRead(LLVMGenerator gen, SpecialRegister reg, LLVMValueRef offset) {
            super(LLVMKind.toLIRKind(gen.getBuilder().wordType()));
            this.gen = gen;
            this.reg = reg;
            this.offset = offset;
        }

        @Override
        public LLVMValueRef get() {
            LLVMIRBuilder builder = gen.getBuilder();
            LLVMValueRef register = gen.getSpecialRegister(reg);
            return offset == null ? register : builder.buildGEP(builder.buildIntToPtr(register, builder.rawPointerType()), offset);
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
                    throw shouldNotReachHere("invalid float type");
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
            throw unimplemented();
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
            throw unimplemented();
        }

        @Override
        public int getSizeInBytes() {
            switch (LLVM.LLVMGetTypeKind(type)) {
                case LLVM.LLVMIntegerTypeKind:
                    return NumUtil.roundUp(LLVM.LLVMGetIntTypeWidth(type), 8) / 8;
                case LLVM.LLVMFloatTypeKind:
                    return 4;
                case LLVM.LLVMDoubleTypeKind:
                case LLVM.LLVMPointerTypeKind:
                    return 8;
                default:
                    throw shouldNotReachHere("invalid kind");
            }
        }

        @Override
        public int getVectorLength() {
            return 1;
        }

        @Override
        public char getTypeChar() {
            throw unimplemented();
        }
    }
}
