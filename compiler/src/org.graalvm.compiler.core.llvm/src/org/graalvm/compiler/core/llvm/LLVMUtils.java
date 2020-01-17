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

import static com.oracle.svm.shadowed.org.bytedeco.llvm.global.LLVM.LLVMTypeOf;
import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;
import static org.graalvm.compiler.debug.GraalError.unimplemented;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.home.HomeFinder;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.shadowed.org.bytedeco.javacpp.Pointer;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMContextRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMTypeRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.global.LLVM;

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
    static final int COMPRESSED_POINTER_ADDRESS_SPACE = 2;
    public static final long DEFAULT_PATCHPOINT_ID = 0xABCDEF00L;
    public static final String ALWAYS_INLINE = "alwaysinline";
    public static final String COMPRESS_FUNCTION_NAME = "__llvm_compress";
    public static final String UNCOMPRESS_FUNCTION_NAME = "__llvm_uncompress";
    public static final String GC_REGISTER_FUNCTION_NAME = "__llvm_gc_register";
    public static final String GC_REGISTER_COMPRESSED_FUNCTION_NAME = "__llvm_gc_register_compressed";
    public static final String ATOMIC_OBJECT_XCHG_FUNCTION_NAME = "__llvm_atomic_object_xchg";
    public static final String ATOMIC_COMPRESSED_OBJECT_XCHG_FUNCTION_NAME = "__llvm_atomic_compressed_object_xchg";
    public static final String LOAD_OBJECT_FROM_UNTRACKED_POINTER_FUNCTION_NAME = "__llvm_load_object_from_untracked_pointer";
    public static final String LOAD_COMPRESSED_OBJECT_FROM_UNTRACKED_POINTER_FUNCTION_NAME = "__llvm_load_compressed_object_from_untracked_pointer";
    public static final String GC_LEAF_FUNCTION_NAME = "gc-leaf-function";
    public static final String JNI_WRAPPER_PREFIX = "__llvm_jni_wrapper_";

    public static final class DebugLevel {
        public static final int NONE = 0;
        public static final int FUNCTION = 1;
        public static final int BLOCK = 2;
        public static final int NODE = 3;
    }

    public static Path getLLVMBinDir() {
        final String property = System.getProperty("llvm.bin.dir");
        if (property != null) {
            return Paths.get(property);
        }

        // TODO (GR-18389): Set only for standalones currently
        Path toolchainHome = HomeFinder.getInstance().getLanguageHomes().get("llvm-toolchain");
        if (toolchainHome != null) {
            return toolchainHome.resolve("bin");
        }

        return getRuntimeDir().resolve("lib").resolve("llvm").resolve("bin");
    }

    private static boolean hasJreDir = System.getProperty("java.specification.version").startsWith("1.");

    private static Path getRuntimeDir() {
        Path runtimeDir = HomeFinder.getInstance().getHomeFolder();
        if (runtimeDir == null) {
            throw new IllegalStateException("Could not find GraalVM home");
        }
        if (hasJreDir) {
            runtimeDir = runtimeDir.resolve("jre");
        }
        return runtimeDir;
    }

    public enum LLVMIntrinsicOperation {
        LOG(1),
        LOG10(1),
        EXP(1),
        POW(2),
        SIN(1),
        COS(1),
        SQRT(1),
        ABS(1),
        ROUND(1),
        RINT(1),
        CEIL(1),
        FLOOR(1),
        MIN(2),
        MAX(2),
        COPYSIGN(2),
        FMA(3),
        CTLZ(1),
        CTTZ(1),
        CTPOP(1);

        private int argCount;

        LLVMIntrinsicOperation(int argCount) {
            this.argCount = argCount;
        }

        public int argCount() {
            return argCount;
        }
    }

    /**
     * LLVM target-specific inline assembly snippets and information.
     */
    public interface TargetSpecific {
        static TargetSpecific get() {
            return ImageSingletons.lookup(TargetSpecific.class);
        }

        /**
         * Snippet that gets the value of an arbitrary register.
         */
        String getRegisterInlineAsm(String register);

        /**
         * Snippet that jumps to a runtime-computed address.
         */
        String getJumpInlineAsm();

        /**
         * Name of the architecture to be passed to the LLVM compiler.
         */
        String getLLVMArchName();

        /**
         * Number of bytes separating two adjacent call frames. A call frame starts at the stack
         * pointer and its size is as given by the LLVM stack map.
         */
        int getCallFrameSeparation();

        /**
         * Offset of the frame pointer relative to the first address outside the current call frame.
         * This offset should be negative.
         */
        int getFramePointerOffset();

        /**
         * Register number of the stack pointer used by the LLVM stack maps.
         */
        int getStackPointerDwarfRegNum();

        /**
         * Register number of the frame pointer used by the LLVM stack maps.
         */
        int getFramePointerDwarfRegNum();

        /**
         * Additional target-specific options to be passed to the LLVM compiler.
         */
        default List<String> getLLCAdditionalOptions() {
            return Collections.emptyList();
        }

        /**
         * Transformation to be applied to the name of a register given by Graal to obtain the
         * corresponding name in assembly.
         */
        default String getLLVMRegisterName(String register) {
            return register;
        }
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
            return LIRKind.compressedReference(new LLVMKind(LLVM.LLVMPointerType(LLVM.LLVMInt8TypeInContext(context), COMPRESSED_POINTER_ADDRESS_SPACE)));
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
            if (LLVM.LLVMGetTypeKind(type) == LLVM.LLVMPointerTypeKind) {
                if (LLVM.LLVMGetPointerAddressSpace(type) == TRACKED_POINTER_ADDRESS_SPACE) {
                    return LIRKind.reference(new LLVMKind(type));
                } else if (LLVM.LLVMGetPointerAddressSpace(type) == COMPRESSED_POINTER_ADDRESS_SPACE) {
                    return LIRKind.compressedReference(new LLVMKind(type));
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
