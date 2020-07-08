package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.LLVMVarArgCompoundValue;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.X86_64BitVarArgs;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;

@ExportLibrary(LLVMManagedReadLibrary.class)
@ExportLibrary(LLVMManagedWriteLibrary.class)
@ExportLibrary(LLVMVaListLibrary.class)
public final class LLVMX86_64VaListStorage {

    @CompilationFinal private int numberOfExplicitArguments;
    @CompilationFinal private int gpOffset;
    @CompilationFinal private int fpOffset;
    @CompilationFinal private LLVMPointer regSaveAreaPtr;
    @CompilationFinal private OverflowArgArea overflowArgArea;

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
    void initialize(Object[] realArguments, int numOfExpArgs) {
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
