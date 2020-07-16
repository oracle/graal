package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMVarArgCompoundValue;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativeVarargsAreaStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeProfiledMemMoveNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;

@ExportLibrary(LLVMManagedReadLibrary.class)
@ExportLibrary(LLVMManagedWriteLibrary.class)
@ExportLibrary(LLVMVaListLibrary.class)
@ExportLibrary(InteropLibrary.class)
public final class LLVMX86_64VaListStorage implements TruffleObject {

    private static final DataLayout DEFAULT_LAYOUT = new DataLayout("e-i64:64-f80:128-n8:16:32:64-S128");

    public static final ArrayType VA_LIST_TYPE = new ArrayType(StructureType.createNamedFromList("struct.__va_list_tag", false,
                    new ArrayList<>(Arrays.asList(PrimitiveType.I32, PrimitiveType.I32, PointerType.I8, PointerType.I8))), 1);

    private static final String GET_MEMBER = "get";

    private Object[] realArguments;
    private int numberOfExplicitArguments;
    private int initGPOffset;
    private int gpOffset;
    private int initFPOffset;
    private int fpOffset;
    private RegSaveArea regSaveArea;
    private LLVMPointer regSaveAreaPtr;
    private OverflowArgArea overflowArgArea;

    LLVMNativePointer nativized;
    private LLVMNativePointer overflowArgAreaBaseNativePtr;

    public LLVMX86_64VaListStorage() {
    }

    public boolean isNativized() {
        return nativized != null;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean hasMembers() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new String[]{GET_MEMBER};
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean isMemberInvocable(String member) {
        return GET_MEMBER.equals(member);
    }

    @ExportMessage
    public Object invokeMember(String member, Object[] arguments) throws ArityException, UnknownIdentifierException {
        if (GET_MEMBER.equals(member)) {
            if (arguments.length == 2) {
                if (!(arguments[0] instanceof Integer)) {
                    throw new IllegalArgumentException("Index argument must be an integer");
                }
                int i = (Integer) arguments[0];
                if (i >= realArguments.length - numberOfExplicitArguments) {
                    throw new ArrayIndexOutOfBoundsException(i);
                }
                Object arg = realArguments[numberOfExplicitArguments + i];
                if (!(arg instanceof LLVMPointer)) {
                    throw new IllegalArgumentException("The i-th argument must be a pointer");
                }
                LLVMPointer ptrArg = (LLVMPointer) arg;

                if (!(arguments[1] instanceof LLVMInteropType)) {
                    throw new IllegalArgumentException("Type argument must be an instance of LLVMInteropType");
                }
                LLVMInteropType type = (LLVMInteropType) arguments[1];

                return ptrArg.export(type);
            } else {
                throw ArityException.create(2, arguments.length);
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    public long getArraySize() {
        return realArguments.length - numberOfExplicitArguments;
    }

    @ExportMessage
    public boolean isArrayElementReadable(long index) {
        return index < realArguments.length - numberOfExplicitArguments;
    }

    @ExportMessage
    public Object readArrayElement(long index) {
        return realArguments[(int) index + numberOfExplicitArguments];
    }

    @ExportMessage(name = "isReadable")
    @ExportMessage(name = "isWritable")
    @SuppressWarnings("static-method")
    boolean isAccessible() {
        return true;
    }

    // LLVMManagedReadLibrary implementation

    @SuppressWarnings("static-method")
    @ExportMessage
    byte readI8(@SuppressWarnings("unused") long offset) {
        throw new UnsupportedOperationException("Should not get here");
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    short readI16(@SuppressWarnings("unused") long offset) {
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    static class ReadI32 {

        @Specialization(guards = "!vaList.isNativized()")
        static int readManagedI32(LLVMX86_64VaListStorage vaList, long offset) {
            switch ((int) offset) {
                case X86_64BitVarArgs.GP_OFFSET:
                    return vaList.gpOffset;
                case X86_64BitVarArgs.FP_OFFSET:
                    return vaList.fpOffset;
                default:
                    throw new UnsupportedOperationException("Should not get here");
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        static int readNativeI32(LLVMX86_64VaListStorage vaList, long offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary nativeReadLibrary) {
            return nativeReadLibrary.readI32(vaList.nativized, offset);
        }
    }

    @ExportMessage
    static class ReadPointer {

        @Specialization(guards = "!vaList.isNativized()")
        static LLVMPointer readManagedPointer(LLVMX86_64VaListStorage vaList, long offset) {
            switch ((int) offset) {
                case X86_64BitVarArgs.OVERFLOW_ARG_AREA:
                    return (LLVMPointer) vaList.overflowArgArea.getCurrentArgPtr();
                case X86_64BitVarArgs.REG_SAVE_AREA:
                    return vaList.regSaveAreaPtr;
                default:
                    throw new UnsupportedOperationException("Should not get here");
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        static LLVMPointer readNativePointer(LLVMX86_64VaListStorage vaList, long offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary nativeReadLibrary) {
            return nativeReadLibrary.readPointer(vaList.nativized, offset);
        }
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    Object readGenericI64(@SuppressWarnings("unused") long offset) {
        throw new UnsupportedOperationException("Should not get here");
    }

    // LLVMManagedWriteLibrary implementation

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    void writeI8(long offset, byte value) {
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    void writeI16(long offset, short value) {
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    static class WriteI32 {

        @Specialization(guards = "!vaList.isNativized()")
        static void writeManaged(LLVMX86_64VaListStorage vaList, long offset, int value) {
            switch ((int) offset) {
                case X86_64BitVarArgs.GP_OFFSET:
                    vaList.gpOffset = value;
                    break;
                case X86_64BitVarArgs.FP_OFFSET:
                    vaList.fpOffset = value;
                    break;
                default:
                    throw new UnsupportedOperationException("Should not get here");
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        static void writeNative(LLVMX86_64VaListStorage vaList, long offset, int value, @CachedLibrary(limit = "1") LLVMManagedWriteLibrary nativeWriteLibrary) {
            nativeWriteLibrary.writeI32(vaList.nativized, offset, value);
        }
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    void writeGenericI64(long offset, Object value) {
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    static class WritePointer {

        @Specialization(guards = "!vaList.isNativized()")
        static void writeManaged(LLVMX86_64VaListStorage vaList, long offset, @SuppressWarnings("unused") LLVMPointer value) {
            switch ((int) offset) {
                case X86_64BitVarArgs.OVERFLOW_ARG_AREA:
                    // Assume that updating the overflowArea pointer means shifting the current
                    // argument
                    vaList.overflowArgArea.shift();
                    break;
                default:
                    throw new UnsupportedOperationException("Should not get here");
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        static void writeNative(LLVMX86_64VaListStorage vaList, long offset, LLVMPointer value, @CachedLibrary(limit = "1") LLVMManagedWriteLibrary nativeWriteLibrary) {
            nativeWriteLibrary.writePointer(vaList.nativized, offset, value);
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

    private static VarArgArea getVarArgArea(Type type) {
        if (type == PrimitiveType.I1) {
            return VarArgArea.GP_AREA;
        } else if (type == PrimitiveType.I8) {
            return VarArgArea.GP_AREA;
        } else if (type == PrimitiveType.I16) {
            return VarArgArea.GP_AREA;
        } else if (type == PrimitiveType.I32) {
            return VarArgArea.GP_AREA;
        } else if (type == PrimitiveType.I64) {
            return VarArgArea.GP_AREA;
        } else if (type == PrimitiveType.FLOAT) {
            return VarArgArea.FP_AREA;
        } else if (type == PrimitiveType.DOUBLE) {
            return VarArgArea.FP_AREA;
        } else if (type == PrimitiveType.X86_FP80) {
            return VarArgArea.OVERFLOW_AREA;
        } else if (type instanceof VectorType && ((VectorType) type).getElementType() == PrimitiveType.FLOAT && ((VectorType) type).getNumberOfElements() <= 2) {
            return VarArgArea.FP_AREA;
        } else if (type instanceof PointerType) {
            return VarArgArea.GP_AREA;
        } else {
            return VarArgArea.OVERFLOW_AREA;
        }
    }

    private int calculateUsedFpArea() {
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
    private int calculateUsedGpArea() {
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

        int gp = calculateUsedGpArea();
        this.gpOffset = this.initGPOffset = gp;
        int fp = X86_64BitVarArgs.GP_LIMIT + calculateUsedFpArea();
        this.fpOffset = this.initFPOffset = fp;

        int overflowArea = 0;
        int[] gpIdx = new int[realArguments.length];
        Arrays.fill(gpIdx, -1);
        int[] fpIdx = new int[realArguments.length];
        Arrays.fill(fpIdx, -1);

        Object[] overflowArgs = new Object[realArguments.length];
        long[] overflowAreaArgOffsets = new long[realArguments.length];
        Arrays.fill(overflowAreaArgOffsets, -1);

        int oi = 0;
        for (int i = numberOfExplicitArguments; i < realArguments.length; i++) {
            final Object arg = realArguments[i];
            final VarArgArea area = getVarArgArea(arg);
            if (area == VarArgArea.GP_AREA && gp < X86_64BitVarArgs.GP_LIMIT) {
                gpIdx[gp / X86_64BitVarArgs.GP_STEP] = i;
                gp += X86_64BitVarArgs.GP_STEP;
            } else if (area == VarArgArea.FP_AREA && fp < X86_64BitVarArgs.FP_LIMIT) {
                fpIdx[(fp - X86_64BitVarArgs.GP_LIMIT) / X86_64BitVarArgs.FP_STEP] = i;
                fp += X86_64BitVarArgs.FP_STEP;
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

        this.regSaveArea = new RegSaveArea(realArguments, gpIdx, fpIdx);
        this.regSaveAreaPtr = LLVMManagedPointer.create(this.regSaveArea);
        this.overflowArgArea = new OverflowArgArea(overflowArgs, overflowAreaArgOffsets, overflowArea);
    }

    @ExportMessage
    void cleanup() {
        // nop
    }

    @ExportMessage
    static class Copy {

        @Specialization(guards = {"!source.isNativized()"})
        static void copyManaged(LLVMX86_64VaListStorage source, LLVMX86_64VaListStorage dest) {
            dest.realArguments = source.realArguments;
            dest.numberOfExplicitArguments = source.numberOfExplicitArguments;
            dest.initFPOffset = source.initFPOffset;
            dest.initGPOffset = source.initGPOffset;
            dest.fpOffset = source.fpOffset;
            dest.gpOffset = source.gpOffset;
            dest.regSaveAreaPtr = source.regSaveAreaPtr;
            dest.overflowArgArea = source.overflowArgArea.clone();
            dest.nativized = null;
            dest.overflowArgAreaBaseNativePtr = null;

        }

        @Specialization(guards = {"source.isNativized()"})
        static void copyNative(LLVMX86_64VaListStorage source, LLVMX86_64VaListStorage dest, @CachedLibrary("source") LLVMManagedReadLibrary srcReadLib) {

            // The destination va_list will be in the managed state, even if the source has been
            // nativized. We need to read some state from the native memory, though.

            copyManaged(source, dest);

            dest.fpOffset = srcReadLib.readI32(source, X86_64BitVarArgs.FP_OFFSET);
            dest.gpOffset = srcReadLib.readI32(source, X86_64BitVarArgs.GP_OFFSET);
            dest.overflowArgArea.current = getNativeShiftCount(source, srcReadLib);
        }
    }

    /**
     * Calculate the number of argument shifts in the overflow area.
     *
     * @param vaList
     * @param readLib
     */
    private static int getNativeShiftCount(LLVMX86_64VaListStorage vaList, LLVMManagedReadLibrary readLib) {
        LLVMNativePointer overflowAreaPtr = LLVMNativePointer.cast(readLib.readPointer(vaList, X86_64BitVarArgs.OVERFLOW_ARG_AREA));
        long curAddr = overflowAreaPtr.asNative();
        long baseAddr = vaList.overflowArgAreaBaseNativePtr.asNative();
        long shiftCnt = (curAddr - baseAddr) / 8;
        return (int) shiftCnt;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    Object shift(Type type,
                    @CachedLibrary("this") LLVMManagedReadLibrary readLib,
                    @CachedLibrary("this") LLVMManagedWriteLibrary writeLib) {
        int regSaveOffs = 0;
        int regSaveStep = 0;
        int regSaveLimit = 0;
        boolean lookIntoRegSaveArea = true;

        VarArgArea varArgArea = getVarArgArea(type);
        switch (varArgArea) {
            case GP_AREA:
                regSaveOffs = X86_64BitVarArgs.GP_OFFSET;
                regSaveStep = X86_64BitVarArgs.GP_STEP;
                regSaveLimit = X86_64BitVarArgs.GP_LIMIT;
                break;

            case FP_AREA:
                regSaveOffs = X86_64BitVarArgs.FP_OFFSET;
                regSaveStep = X86_64BitVarArgs.FP_STEP;
                regSaveLimit = X86_64BitVarArgs.FP_LIMIT;
                break;

            case OVERFLOW_AREA:
                lookIntoRegSaveArea = false;
                break;
        }

        if (lookIntoRegSaveArea) {
            int offs = readLib.readI32(this, regSaveOffs);
            if (offs < regSaveLimit) {
                writeLib.writeI32(this, regSaveOffs, offs + regSaveStep);
                int i = this.regSaveArea.offsetToIndex(offs);
                return this.regSaveArea.args[i];
            }
        }

        // overflow area
        if (isNativized()) {
            int shiftCnt = getNativeShiftCount(this, readLib);
            long shiftOffs = this.overflowArgArea.offsets[shiftCnt + 1];
            LLVMNativePointer shiftedOverflowAreaPtr = overflowArgAreaBaseNativePtr.increment(shiftOffs);
            writeLib.writePointer(this, X86_64BitVarArgs.OVERFLOW_ARG_AREA, shiftedOverflowAreaPtr);

            return this.overflowArgArea.args[shiftCnt];
        } else {
            Object currentArg = this.overflowArgArea.getCurrentArg();
            this.overflowArgArea.shift();
            return currentArg;
        }
    }

    @SuppressWarnings("static-method")
    LLVMExpressionNode createAllocaNode(LLVMContext llvmCtx) {
        return llvmCtx.getLanguage().getActiveConfiguration().createNodeFactory(llvmCtx, DEFAULT_LAYOUT).createAlloca(VA_LIST_TYPE, 16);
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
    void toNative(@SuppressWarnings("unused") @CachedContext(LLVMLanguage.class) LLVMContext llvmCtx,
                    @Cached(value = "this.createAllocaNode(llvmCtx)", uncached = "this.createAllocaNode(llvmCtx)") LLVMExpressionNode allocaNode,
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
        nativized = LLVMNativePointer.cast(allocaNode.executeGeneric(frame));

        final int vaLength = this.realArguments.length - numberOfExplicitArguments;

        LLVMPointer regSaveAreaNativePtr = stackAllocationNode.executeWithTarget(frame,
                        X86_64BitVarArgs.FP_LIMIT);
        this.overflowArgAreaBaseNativePtr = LLVMNativePointer.cast(stackAllocationNode.executeWithTarget(frame, overflowArgArea.overflowAreaSize));

        Object p = nativized.increment(X86_64BitVarArgs.GP_OFFSET);
        gpOffsetStore.executeWithTarget(p, gpOffset);

        p = nativized.increment(X86_64BitVarArgs.FP_OFFSET);
        fpOffsetStore.executeWithTarget(p, fpOffset);

        p = nativized.increment(X86_64BitVarArgs.OVERFLOW_ARG_AREA);
        overflowArgAreaStore.executeWithTarget(p, overflowArgAreaBaseNativePtr.increment(8 * overflowArgArea.current));

        p = nativized.increment(X86_64BitVarArgs.REG_SAVE_AREA);
        regSaveAreaStore.executeWithTarget(p, regSaveAreaNativePtr);

        // reconstruct register_save_area and overflow_arg_area according to AMD64 ABI

        int fp = this.initFPOffset;
        int gp = this.initGPOffset;

        if (vaLength > 0) {
            int overflowOffset = 0;

            // TODO (chaeubl): this generates pretty bad machine code as we don't know anything
            // about the arguments
            for (int i = 0; i < vaLength; i++) {
                final Object object = realArguments[numberOfExplicitArguments + i];
                final VarArgArea area = getVarArgArea(object);

                if (area == VarArgArea.GP_AREA && gp < X86_64BitVarArgs.GP_LIMIT) {
                    storeArgument(regSaveAreaNativePtr, gp, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore,
                                    fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object);
                    gp += X86_64BitVarArgs.GP_STEP;
                } else if (area == VarArgArea.FP_AREA && fp < X86_64BitVarArgs.FP_LIMIT) {
                    storeArgument(regSaveAreaNativePtr, fp, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore,
                                    fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object);
                    fp += X86_64BitVarArgs.FP_STEP;
                } else {
                    assert overflowArgArea.overflowAreaSize >= overflowOffset;
                    overflowOffset += storeArgument(overflowArgAreaBaseNativePtr, overflowOffset, memMove,
                                    i64OverflowArgAreaStore, i32OverflowArgAreaStore,
                                    fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, object);
                }
            }
        }
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
        final Object[] args;

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
            int i = offsetToIndex(offset);
            return i < 0 ? 0 : (Byte) args[offsetToIndex(offset)];
        }

        @ExportMessage
        short readI16(long offset) {
            int i = offsetToIndex(offset);
            return i < 0 ? 0 : (Short) args[offsetToIndex(offset)];
        }

        @ExportMessage
        int readI32(long offset) {
            int i = offsetToIndex(offset);
            return i < 0 ? 0 : (Integer) args[offsetToIndex(offset)];
        }

        @ExportMessage
        LLVMPointer readPointer(long offset) {
            return (LLVMPointer) args[offsetToIndex(offset)];
        }

        @ExportMessage
        Object readGenericI64(long offset) {
            int i = offsetToIndex(offset);
            return i < 0 ? Double.doubleToLongBits(0d) : args[i];
        }
    }

    @ExportLibrary(LLVMManagedReadLibrary.class)
    final static class RegSaveArea extends ArgsArea {

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
    static final class OverflowArgArea extends ArgsArea implements Cloneable {
        private final long[] offsets;
        final int overflowAreaSize;
        private int current = 0;

        OverflowArgArea(Object[] args, long[] offsets, int overflowAreaSize) {
            super(args);
            this.overflowAreaSize = overflowAreaSize;
            this.offsets = offsets;
        }

        @Override
        protected int offsetToIndex(long offset) {
            for (int i = 0; i < offsets.length; i++) {
                if (offsets[i] < 0) {
                    return -1;
                }
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
            return args[current];
        }

        Object getCurrentArgPtr() {
            Object curArg = args[current];
            if (curArg instanceof LLVMVarArgCompoundValue) {
                return ((LLVMVarArgCompoundValue) curArg).getAddr();
            } else {
                return LLVMManagedPointer.create(this, offsets[current]);
            }
        }

        @Override
        public OverflowArgArea clone() {
            OverflowArgArea cloned = new OverflowArgArea(args, offsets, overflowAreaSize);
            cloned.current = current;
            return cloned;
        }

    }

}
