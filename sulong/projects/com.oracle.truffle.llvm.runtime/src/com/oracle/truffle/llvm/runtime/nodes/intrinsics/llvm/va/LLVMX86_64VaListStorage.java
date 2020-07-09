package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va;

import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.LLVMVarArgCompoundValue;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.X86_64BitVarArgs;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativeVarargsAreaStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeProfiledMemMoveNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;

@ExportLibrary(LLVMManagedReadLibrary.class)
@ExportLibrary(LLVMManagedWriteLibrary.class)
@ExportLibrary(LLVMVaListLibrary.class)
@ExportLibrary(InteropLibrary.class)
public final class LLVMX86_64VaListStorage implements TruffleObject {

    private final Supplier<LLVMExpressionNode> allocaNodeFactory;

    @CompilationFinal Object[] realArguments;
    @CompilationFinal private int numberOfExplicitArguments;
    @CompilationFinal private int gpOffset;
    @CompilationFinal private int fpOffset;
    @CompilationFinal private LLVMPointer regSaveAreaPtr;
    @CompilationFinal private OverflowArgArea overflowArgArea;

    private LLVMNativePointer nativized;

    public LLVMX86_64VaListStorage(Supplier<LLVMExpressionNode> allocaNodeFactory) {
        this.allocaNodeFactory = allocaNodeFactory;
    }

    @ExportMessage(name = "isReadable")
    @ExportMessage(name = "isWritable")
    @SuppressWarnings("static-method")
    boolean isAccessible() {
        return true;
    }

    @ExportMessage
    byte readI8(@SuppressWarnings("unused") long offset) {
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    short readI16(@SuppressWarnings("unused") long offset) {
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    int readI32(long offset) {
        switch ((int) offset) {
            case X86_64BitVarArgs.GP_OFFSET:
                return gpOffset;
            case X86_64BitVarArgs.FP_OFFSET:
                return fpOffset;
            default:
                throw new UnsupportedOperationException("Should not get here");
        }
    }

    @ExportMessage
    LLVMPointer readPointer(long offset) {
        switch ((int) offset) {
            case X86_64BitVarArgs.OVERFLOW_ARG_AREA:
                return (LLVMPointer) overflowArgArea.getCurrentArg();
            case X86_64BitVarArgs.REG_SAVE_AREA:
                return regSaveAreaPtr;
            default:
                throw new UnsupportedOperationException("Should not get here");
        }
    }

    @ExportMessage
    Object readGenericI64(@SuppressWarnings("unused") long offset) {
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    @SuppressWarnings("unused")
    void writeI8(long offset, byte value) {
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    @SuppressWarnings("unused")
    void writeI16(long offset, short value) {
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    @SuppressWarnings("unused")
    void writeI32(long offset, int value) {
        switch ((int) offset) {
            case X86_64BitVarArgs.GP_OFFSET:
                gpOffset = value;
                break;
            case X86_64BitVarArgs.FP_OFFSET:
                fpOffset = value;
                break;
            default:
                throw new UnsupportedOperationException("Should not get here");
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
    void writeGenericI64(long offset, Object value) {
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    void writePointer(long offset, LLVMPointer value) {
        switch ((int) offset) {
            case X86_64BitVarArgs.OVERFLOW_ARG_AREA:
                // Assume that updating the overflowArea pointer means shifting the current argument
                this.overflowArgArea.shift();
                break;
            case X86_64BitVarArgs.REG_SAVE_AREA:
                this.regSaveAreaPtr = value;
                break;
            default:
                throw new UnsupportedOperationException("Should not get here");
        }
    }

    private enum VarArgArea {
        GP_AREA,
        FP_AREA,
        OVERFLOW_AREA;
    }

    private static VarArgArea getVarArgArea(Object arg) {
        if (arg instanceof Boolean) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Byte) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Short) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Integer) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Long) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Float) {
            return VarArgArea.FP_AREA;
        } else if (arg instanceof Double) {
            return VarArgArea.FP_AREA;
        } else if (arg instanceof LLVMVarArgCompoundValue) {
            return VarArgArea.OVERFLOW_AREA;
        } else if (LLVMPointer.isInstance(arg)) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof LLVM80BitFloat) {
            return VarArgArea.OVERFLOW_AREA;
        } else if (arg instanceof LLVMFloatVector && ((LLVMFloatVector) arg).getLength() <= 2) {
            return VarArgArea.FP_AREA;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(arg);
        }
    }

    private int calculateUsedFpArea(Object[] realArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int usedFpArea = 0;
        final int fpAreaLimit = X86_64BitVarArgs.FP_LIMIT - X86_64BitVarArgs.GP_LIMIT;
        for (int i = 0; i < numberOfExplicitArguments && usedFpArea < fpAreaLimit; i++) {
            if (getVarArgArea(realArguments[i]) == VarArgArea.FP_AREA) {
                usedFpArea += X86_64BitVarArgs.FP_STEP;
            }
        }
        return usedFpArea;
    }

    @ExplodeLoop
    private int calculateUsedGpArea(Object[] realArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int usedGpArea = 0;
        for (int i = 0; i < numberOfExplicitArguments && usedGpArea < X86_64BitVarArgs.GP_LIMIT; i++) {
            if (getVarArgArea(realArguments[i]) == VarArgArea.GP_AREA) {
                usedGpArea += X86_64BitVarArgs.GP_STEP;
            }
        }

        return usedGpArea;
    }

    @ExportMessage
    void initialize(Object[] realArgs, int numOfExpArgs) {
        this.realArguments = realArgs;
        this.numberOfExplicitArguments = numOfExpArgs;
        assert numberOfExplicitArguments <= realArguments.length;

        int usedGPByExplicitArgs = calculateUsedGpArea(realArguments);
        this.gpOffset = usedGPByExplicitArgs;
        int usedFPByExplicitArgs = X86_64BitVarArgs.GP_LIMIT + calculateUsedFpArea(realArguments);
        this.fpOffset = usedFPByExplicitArgs;

        int overflowArea = 0;
        int[] gpIdx = new int[realArguments.length];
        int[] fpIdx = new int[realArguments.length];
        Object[] overflowArgs = new Object[realArguments.length];
        long[] overflowAreaArgOffsets = new long[realArguments.length];
        int oi = 0;
        for (int i = numberOfExplicitArguments; i < realArguments.length; i++) {
            final Object arg = realArguments[i];
            final VarArgArea area = getVarArgArea(arg);
            if (area == VarArgArea.GP_AREA && usedGPByExplicitArgs < X86_64BitVarArgs.GP_LIMIT) {
                gpIdx[usedGPByExplicitArgs / X86_64BitVarArgs.GP_STEP] = i;
                usedGPByExplicitArgs += X86_64BitVarArgs.GP_STEP;
            } else if (area == VarArgArea.FP_AREA && usedFPByExplicitArgs < X86_64BitVarArgs.FP_LIMIT) {
                fpIdx[(usedFPByExplicitArgs - X86_64BitVarArgs.GP_LIMIT) / X86_64BitVarArgs.FP_STEP] = i;
                usedFPByExplicitArgs += X86_64BitVarArgs.FP_STEP;
            } else if (area != VarArgArea.OVERFLOW_AREA) {
                overflowAreaArgOffsets[oi] = overflowArea;
                overflowArea += X86_64BitVarArgs.STACK_STEP;
                overflowArgs[oi++] = arg;
            } else if (arg instanceof LLVM80BitFloat) {
                overflowAreaArgOffsets[oi] = overflowArea;
                overflowArea += 16;
                overflowArgs[oi++] = arg;
            } else if (arg instanceof LLVMVarArgCompoundValue) {
                overflowAreaArgOffsets[oi] = overflowArea;
                LLVMVarArgCompoundValue obj = (LLVMVarArgCompoundValue) arg;
                overflowArea += obj.getSize();
                overflowArgs[oi++] = arg;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(arg);
            }
        }

        this.regSaveAreaPtr = LLVMManagedPointer.create(new RegSaveArea(realArguments, gpIdx, fpIdx));
        this.overflowArgArea = new OverflowArgArea(overflowArgs, overflowAreaArgOffsets);
    }

    LLVMExpressionNode createAllocaNode() {
        return allocaNodeFactory.get();
    }

    static LLVMStoreNode createI64StoreNode() {
        return LLVMI64StoreNodeGen.create(null, null);
    }

    static LLVMStoreNode createI32StoreNode() {
        return LLVMI32StoreNodeGen.create(null, null);
    }

    static LLVMStoreNode create80BitFloatStoreNode() {
        return LLVM80BitFloatStoreNodeGen.create(null, null);
    }

    static LLVMStoreNode createPointerStoreNode() {
        return LLVMPointerStoreNodeGen.create(null, null);
    }

    static LLVMMemMoveNode createMemMoveNode() {
        return NativeProfiledMemMoveNodeGen.create();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    void toNative(@Cached(value = "this.createAllocaNode()", uncached = "this.createAllocaNode()") LLVMExpressionNode allocaNode,
                    @Cached(value = "create()", uncached = "create()") LLVMNativeVarargsAreaStackAllocationNode stackAllocationNode,
                    @Cached(value = "createI64StoreNode()", uncached = "createI64StoreNode()") LLVMStoreNode i64RegSaveAreaStore,
                    @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode i32RegSaveAreaStore,
                    @Cached(value = "create80BitFloatStoreNode()", uncached = "create80BitFloatStoreNode()") LLVMStoreNode fp80bitRegSaveAreaStore,
                    @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode pointerRegSaveAreaStore,
                    @Cached(value = "createI64StoreNode()", uncached = "createI64StoreNode()") LLVMStoreNode i64OverflowArgAreaStore,
                    @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode i32OverflowArgAreaStore,
                    @Cached(value = "create80BitFloatStoreNode()", uncached = "create80BitFloatStoreNode()") LLVMStoreNode fp80bitOverflowArgAreaStore,
                    @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode pointerOverflowArgAreaStore,
                    @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode gpOffsetStore,
                    @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode fpOffsetStore,
                    @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode overflowArgAreaStore,
                    @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode regSaveAreaStore,
                    @Cached(value = "createMemMoveNode()", uncached = "createMemMoveNode()") LLVMMemMoveNode memMove,
                    @Cached BranchProfile nativizedProfile) {
        if (nativized != null) {
            nativizedProfile.enter();
            return;
        }

        VirtualFrame frame = (VirtualFrame) Truffle.getRuntime().getCurrentFrame().getFrame(FrameAccess.READ_WRITE);
        nativized = (LLVMNativePointer) allocaNode.executeGeneric(frame);

        final int vaLength = this.realArguments.length - numberOfExplicitArguments;

        LLVMPointer regSaveArea = stackAllocationNode.executeWithTarget(frame,
                        X86_64BitVarArgs.FP_LIMIT);
        int overflowArgAreaSize = computeOverflowArgAreaSize();
        LLVMPointer overflowArgAreaPtr = stackAllocationNode.executeWithTarget(frame,
                        overflowArgAreaSize);

        Object p = nativized.increment(X86_64BitVarArgs.GP_OFFSET);
        gpOffsetStore.executeWithTarget(p, gpOffset);

        p = nativized.increment(X86_64BitVarArgs.FP_OFFSET);
        fpOffsetStore.executeWithTarget(p, fpOffset);

        p = nativized.increment(X86_64BitVarArgs.OVERFLOW_ARG_AREA);
        overflowArgAreaStore.executeWithTarget(p, overflowArgAreaPtr);

        p = nativized.increment(X86_64BitVarArgs.REG_SAVE_AREA);
        regSaveAreaStore.executeWithTarget(p, regSaveArea);

        // reconstruct register_save_area and overflow_arg_area according to AMD64 ABI
        if (vaLength > 0) {
            int overflowOffset = 0;

            // TODO (chaeubl): this generates pretty bad machine code as we don't know anything
            // about the arguments
            for (int i = 0; i < vaLength; i++) {
                final Object object = realArguments[numberOfExplicitArguments + i];
                final VarArgArea area = getVarArgArea(object);

                if (area == VarArgArea.GP_AREA && gpOffset < X86_64BitVarArgs.GP_LIMIT) {
                    storeArgument(regSaveArea, gpOffset, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore,
                                    fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object);
                    gpOffset += X86_64BitVarArgs.GP_STEP;
                } else if (area == VarArgArea.FP_AREA && fpOffset < X86_64BitVarArgs.FP_LIMIT) {
                    storeArgument(regSaveArea, fpOffset, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore,
                                    fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object);
                    fpOffset += X86_64BitVarArgs.FP_STEP;
                } else {
                    assert overflowArgAreaSize >= overflowOffset;
                    overflowOffset += storeArgument(overflowArgAreaPtr, overflowOffset, memMove,
                                    i64OverflowArgAreaStore, i32OverflowArgAreaStore,
                                    fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, object);
                }
            }
        }
    }

    private int computeOverflowArgAreaSize() {
        assert numberOfExplicitArguments <= realArguments.length;

        int overflowArea = 0;
        for (int i = numberOfExplicitArguments; i < realArguments.length; i++) {
            final Object arg = realArguments[i];
            final VarArgArea area = getVarArgArea(arg);
            if (area == VarArgArea.GP_AREA && gpOffset < X86_64BitVarArgs.GP_LIMIT) {
                gpOffset += X86_64BitVarArgs.GP_STEP;
            } else if (area == VarArgArea.FP_AREA && fpOffset < X86_64BitVarArgs.FP_LIMIT) {
                fpOffset += X86_64BitVarArgs.FP_STEP;
            } else if (area != VarArgArea.OVERFLOW_AREA) {
                overflowArea += X86_64BitVarArgs.STACK_STEP;
            } else if (arg instanceof LLVM80BitFloat) {
                overflowArea += 16;
            } else if (arg instanceof LLVMVarArgCompoundValue) {
                LLVMVarArgCompoundValue obj = (LLVMVarArgCompoundValue) arg;
                overflowArea += obj.getSize();
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(arg);
            }
        }
        return overflowArea;

    }

    private static long storeArgument(LLVMPointer ptr, long offset, LLVMMemMoveNode memmove, LLVMStoreNode storeI64Node,
                    LLVMStoreNode storeI32Node, LLVMStoreNode storeFP80Node, LLVMStoreNode storePointerNode, Object object) {
        if (object instanceof Number) {
            return doPrimitiveWrite(ptr, offset, storeI64Node, object);
        } else if (object instanceof LLVMVarArgCompoundValue) {
            LLVMVarArgCompoundValue obj = (LLVMVarArgCompoundValue) object;
            Object currentPtr = ptr.increment(offset);
            memmove.executeWithTarget(currentPtr, obj.getAddr(), obj.getSize());
            return obj.getSize();
        } else if (LLVMPointer.isInstance(object)) {
            Object currentPtr = ptr.increment(offset);
            storePointerNode.executeWithTarget(currentPtr, object);
            return X86_64BitVarArgs.STACK_STEP;
        } else if (object instanceof LLVM80BitFloat) {
            Object currentPtr = ptr.increment(offset);
            storeFP80Node.executeWithTarget(currentPtr, object);
            return 16;
        } else if (object instanceof LLVMFloatVector) {
            final LLVMFloatVector floatVec = (LLVMFloatVector) object;
            for (int i = 0; i < floatVec.getLength(); i++) {
                Object currentPtr = ptr.increment(offset + i * Float.BYTES);
                storeI32Node.executeWithTarget(currentPtr, Float.floatToIntBits(floatVec.getValue(i)));
            }
            return floatVec.getLength() * Float.BYTES;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(object);
        }
    }

    private static int doPrimitiveWrite(LLVMPointer ptr, long offset, LLVMStoreNode storeNode, Object arg) throws AssertionError {
        Object currentPtr = ptr.increment(offset);
        long value;
        if (arg instanceof Boolean) {
            value = ((boolean) arg) ? 1L : 0L;
        } else if (arg instanceof Byte) {
            value = Integer.toUnsignedLong((byte) arg);
        } else if (arg instanceof Short) {
            value = Integer.toUnsignedLong((short) arg);
        } else if (arg instanceof Integer) {
            value = Integer.toUnsignedLong((int) arg);
        } else if (arg instanceof Long) {
            value = (long) arg;
        } else if (arg instanceof Float) {
            value = Integer.toUnsignedLong(Float.floatToIntBits((float) arg));
        } else if (arg instanceof Double) {
            value = Double.doubleToRawLongBits((double) arg);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(arg);
        }
        storeNode.executeWithTarget(currentPtr, value);
        return X86_64BitVarArgs.STACK_STEP;
    }

    @ExportMessage
    boolean isPointer() {
        return nativized != null;
    }

    @ExportMessage
    long asPointer() {
        return nativized == null ? 0L : nativized.asNative();
    }

    @ExportLibrary(LLVMManagedReadLibrary.class)
    static abstract class ArgsArea {
        protected final Object[] args;

        ArgsArea(Object[] args) {
            this.args = args;
        }

        protected abstract int offsetToIndex(long offset);

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isReadable() {
            return true;
        }

        @ExportMessage
        byte readI8(long offset) {
            return (Byte) args[offsetToIndex(offset)];
        }

        @ExportMessage
        short readI16(long offset) {
            return (Short) args[offsetToIndex(offset)];
        }

        @ExportMessage
        int readI32(long offset) {
            return (Integer) args[offsetToIndex(offset)];
        }

        @ExportMessage
        LLVMPointer readPointer(long offset) {
            return (LLVMPointer) args[offsetToIndex(offset)];
        }

        @ExportMessage
        Object readGenericI64(long offset) {
            return args[offsetToIndex(offset)];
        }
    }

    @ExportLibrary(LLVMManagedReadLibrary.class)
    static final class RegSaveArea extends ArgsArea {

        private final int[] gpIdx;
        private final int[] fpIdx;

        RegSaveArea(Object[] args, int[] gpIdx, int[] fpIdx) {
            super(args);
            this.gpIdx = gpIdx;
            this.fpIdx = fpIdx;
        }

        @Override
        protected int offsetToIndex(long offset) {
            if (offset < X86_64BitVarArgs.GP_LIMIT) {
                int i = (int) offset / X86_64BitVarArgs.GP_STEP;
                return gpIdx[i];
            } else {
                assert offset < X86_64BitVarArgs.FP_LIMIT;
                int i = (int) (offset - X86_64BitVarArgs.GP_LIMIT) / (X86_64BitVarArgs.FP_STEP);
                return fpIdx[i];
            }
        }

    }

    @ExportLibrary(LLVMManagedReadLibrary.class)
    static final class OverflowArgArea extends ArgsArea {
        private final long[] offsets;
        private int current = 0;

        OverflowArgArea(Object[] args, long[] offsets) {
            super(args);
            this.offsets = offsets;
        }

        @Override
        protected int offsetToIndex(long offset) {
            for (int i = 0; i < offsets.length; i++) {
                if (offsets[i] == offset) {
                    return i;
                }
            }
            throw new UnsupportedOperationException("Should not get here.");
        }

        void shift() {
            current++;
        }

        Object getCurrentArg() {
            Object curArg = args[current];
            if (curArg instanceof LLVMVarArgCompoundValue) {
                return ((LLVMVarArgCompoundValue) curArg).getAddr();
            } else {
                return LLVMManagedPointer.create(this, offsets[current]);
            }
        }

    }

}
