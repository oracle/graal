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
package org.graalvm.compiler.core.llvm;

import static org.bytedeco.javacpp.LLVM.LLVMTypeOf;
import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;
import static org.graalvm.compiler.debug.GraalError.unimplemented;

import org.bytedeco.javacpp.LLVM;
import org.bytedeco.javacpp.LLVM.LLVMContextRef;
import org.bytedeco.javacpp.LLVM.LLVMTypeRef;
import org.bytedeco.javacpp.LLVM.LLVMValueRef;
import org.bytedeco.javacpp.Pointer;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.nativeimage.ImageSingletons;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class LLVMUtils {
    public static final int FALSE = 0;
    public static final int TRUE = 1;
    public static final Pointer NULL = null;
    static final int UNTRACKED_POINTER_ADDRESS_SPACE = 0;
    static final int TRACKED_POINTER_ADDRESS_SPACE = 1;
    public static final long DEFAULT_PATCHPOINT_ID = 0xABCDEF00L;
    public static final String ALWAYS_INLINE = "alwaysinline";
    public static final String GC_REGISTER_FUNCTION_NAME = "__llvm_gc_register";
    public static final String GC_LEAF_FUNCTION_NAME = "gc-leaf-function";
    public static final String JNI_WRAPPER_PREFIX = "__llvm_jni_wrapper_";

    public static final class DebugLevel {
        public static final int NONE = 0;
        public static final int FUNCTION = 1;
        public static final int BLOCK = 2;
        public static final int NODE = 3;
    }

    /**
     * LLVM target-specific inline assembly snippets.
     */
    public abstract static class TargetSpecific {
        public static TargetSpecific get() {
            return ImageSingletons.lookup(TargetSpecific.class);
        }

        /**
         * Snippet that gets the value of an arbitrary register.
         */
        public abstract String getRegisterInlineAsm(String register);

        /**
         * Snippet that jumps to a runtime-computed address.
         */
        public abstract String getJumpInlineAsm();
    }

    static int getLLVMIntCond(Condition cond) {
        switch (cond) {
            case EQ:
                return LLVM.LLVMIntEQ;
            case NE:
                return LLVM.LLVMIntNE;
            case LT:
                return LLVM.LLVMIntSLT;
            case LE:
                return LLVM.LLVMIntSLE;
            case GT:
                return LLVM.LLVMIntSGT;
            case GE:
                return LLVM.LLVMIntSGE;
            case AE:
                return LLVM.LLVMIntUGE;
            case BE:
                return LLVM.LLVMIntULE;
            case AT:
                return LLVM.LLVMIntUGT;
            case BT:
                return LLVM.LLVMIntULT;
            default:
                throw shouldNotReachHere("invalid condition");
        }
    }

    static int getLLVMRealCond(Condition cond, boolean unordered) {
        switch (cond) {
            case EQ:
                return (unordered) ? LLVM.LLVMRealUEQ : LLVM.LLVMRealOEQ;
            case NE:
                return (unordered) ? LLVM.LLVMRealUNE : LLVM.LLVMRealONE;
            case LT:
                return (unordered) ? LLVM.LLVMRealULT : LLVM.LLVMRealOLT;
            case LE:
                return (unordered) ? LLVM.LLVMRealULE : LLVM.LLVMRealOLE;
            case GT:
                return (unordered) ? LLVM.LLVMRealUGT : LLVM.LLVMRealOGT;
            case GE:
                return (unordered) ? LLVM.LLVMRealUGE : LLVM.LLVMRealOGE;
            default:
                throw shouldNotReachHere("invalid condition");
        }
    }

    public interface LLVMValueWrapper {
        LLVMValueRef get();
    }

    interface LLVMTypeWrapper {
        LLVMTypeRef get();
    }

    public static LLVMValueRef getVal(Value value) {
        return ((LLVMValueWrapper) value).get();
    }

    static LLVMTypeRef getType(ValueKind<?> kind) {
        return ((LLVMTypeWrapper) kind.getPlatformKind()).get();
    }

    static String dumpValues(String prefix, LLVMValueRef... values) {
        StringBuilder builder = new StringBuilder(prefix);
        for (LLVMValueRef value : values) {
            builder.append(" ");
            builder.append(LLVM.LLVMPrintValueToString(value).getString());
        }
        return builder.toString();
    }

    static String dumpTypes(String prefix, LLVMTypeRef... types) {
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

        LLVMVariable(ValueKind<?> kind) {
            super(kind, id++);
        }

        LLVMVariable(LLVMTypeRef type) {
            this(LLVMKind.toLIRKind(type));
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

        @Override
        public String toString() {
            return LLVM.LLVMPrintValueToString(value).getString();
        }
    }

    static class LLVMConstant extends ConstantValue implements LLVMValueWrapper {
        private final LLVMValueRef value;

        LLVMConstant(LLVMValueRef value, Constant constant) {
            super(LLVMKind.toLIRKind(LLVMIRBuilder.typeOf(value)), constant);
            this.value = value;
        }

        @Override
        public LLVMValueRef get() {
            return value;
        }

        @Override
        public String toString() {
            return LLVM.LLVMPrintValueToString(value).getString();
        }
    }

    public static class LLVMStackSlot extends VirtualStackSlot implements LLVMValueWrapper {
        private static int id = 0;

        private LLVMValueRef value;
        private final LLVMVariable address;

        LLVMStackSlot(LLVMValueRef value) {
            super(id++, LLVMKind.toLIRKind(LLVM.LLVMTypeOf(value)));

            this.value = value;
            this.address = new LLVMVariable(value);
        }

        @Override
        public LLVMValueRef get() {
            return value;
        }

        public LLVMVariable address() {
            return address;
        }

        @Override
        public String toString() {
            return LLVM.LLVMPrintValueToString(value).getString();
        }
    }

    public static class LLVMKindTool implements LIRKindTool {
        private LLVMContextRef context;

        public LLVMKindTool(LLVMContextRef context) {
            this.context = context;
        }

        @Override
        public LIRKind getIntegerKind(int bits) {
            return LIRKind.value(new LLVMKind(LLVM.LLVMIntTypeInContext(context, bits)));
        }

        @Override
        public LIRKind getFloatingKind(int bits) {
            switch (bits) {
                case 32:
                    return LIRKind.value(new LLVMKind(LLVM.LLVMFloatTypeInContext(context)));
                case 64:
                    return LIRKind.value(new LLVMKind(LLVM.LLVMDoubleTypeInContext(context)));
                default:
                    throw shouldNotReachHere("invalid float type");
            }
        }

        @Override
        public LIRKind getObjectKind() {
            return LIRKind.reference(new LLVMKind(LLVM.LLVMPointerType(LLVM.LLVMInt8TypeInContext(context), TRACKED_POINTER_ADDRESS_SPACE)));
        }

        @Override
        public LIRKind getWordKind() {
            return LIRKind.value(new LLVMKind(LLVM.LLVMInt64TypeInContext(context)));
        }

        @Override
        public LIRKind getNarrowOopKind() {
            throw unimplemented();
        }

        @Override
        public LIRKind getNarrowPointerKind() {
            throw unimplemented();
        }
    }

    static final class LLVMKind implements PlatformKind, LLVMTypeWrapper {
        private final LLVMTypeRef type;

        private LLVMKind(LLVMTypeRef type) {
            this.type = type;
        }

        static LIRKind toLIRKind(LLVMTypeRef type) {
            if (LLVM.LLVMGetTypeKind(type) == LLVM.LLVMPointerTypeKind && LLVM.LLVMGetPointerAddressSpace(type) == TRACKED_POINTER_ADDRESS_SPACE) {
                return LIRKind.reference(new LLVMKind(type));
            } else {
                return LIRKind.value(new LLVMKind(type));
            }
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
                    return 8;
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

    public static class LLVMAddressValue extends Value {

        private final Value base;
        private final Value index;

        public LLVMAddressValue(ValueKind<?> kind, Value base, Value index) {
            super(kind);
            this.base = base;
            this.index = index;
        }

        public Value getBase() {
            return base;
        }

        public Value getIndex() {
            return index;
        }
    }
}
