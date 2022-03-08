package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

import java.util.ArrayList;
import java.util.List;

public class DataSectionFactory {

    @CompilationFinal(dimensions = 1) private final int[] globalOffsets;
    @CompilationFinal(dimensions = 1) private final int[] threadLocalGlobalOffsets;
    @CompilationFinal(dimensions = 1) private final boolean[] globalIsReadOnly;

    private DataSection roSection;
    private DataSection rwSection;
    private DataSection threadLocalSection;

    public DataSectionFactory(LLVMParserResult result) throws Type.TypeOverflowException {
        DataLayout dataLayout = result.getDataLayout();
        int globalsCount = result.getDefinedGlobals().size();
        int threadLocalGlobalsCount = result.getThreadLocalGlobals().size();

        this.globalOffsets = new int[globalsCount];
        this.threadLocalGlobalOffsets = new int[threadLocalGlobalsCount];
        this.globalIsReadOnly = new boolean[globalsCount];

        List<GlobalVariable> definedGlobals = result.getDefinedGlobals();
        List<GlobalVariable> threadLocalGlobals = result.getThreadLocalGlobals();
        roSection = new DataSection(dataLayout);
        rwSection = new DataSection(dataLayout);
        threadLocalSection = new DataSection(dataLayout);

        for (int i = 0; i < globalsCount; i++) {
            GlobalVariable global = definedGlobals.get(i);
            Type type = global.getType().getPointeeType();
            if (isSpecialGlobalSlot(type)) {
                globalOffsets[i] = -1; // pointer type
            } else {
                // allocate at least one byte per global (to make the pointers unique)
                if (type.getSize(dataLayout) == 0) {
                    type = PrimitiveType.getIntegerType(8);
                }
                globalIsReadOnly[i] = global.isReadOnly();
                DataSection dataSection = globalIsReadOnly[i] ? roSection : rwSection;
                long offset = dataSection.add(global, type);
                assert offset >= 0;
                if (offset > Integer.MAX_VALUE) {
                    throw CompilerDirectives.shouldNotReachHere("globals section >2GB not supported");
                }
                globalOffsets[i] = (int) offset;
            }
        }

        for (int i = 0; i < threadLocalGlobalsCount; i++) {
            GlobalVariable tlGlobals = threadLocalGlobals.get(i);
            Type type = tlGlobals.getType().getPointeeType();
            long offset = threadLocalSection.add(tlGlobals, type);
            assert offset >= 0;
            if (offset > Integer.MAX_VALUE) {
                throw CompilerDirectives.shouldNotReachHere("globals section >2GB not supported");
            }
            threadLocalGlobalOffsets[i] = (int) offset;
        }

    }

    DataSection getRoSection() {
        return roSection;
    }

    DataSection getRwSection() {
        return rwSection;
    }

    DataSection getThreadLocalSection() {
        return threadLocalSection;
    }

    int[] getGlobalOffsets() {
        return globalOffsets;
    }

    boolean[] getGlobalIsReadOnly() {
        return globalIsReadOnly;
    }

    int[] getThreadLocalGlobalOffsets() {
        return threadLocalGlobalOffsets;
    }

    static final class DataSection {

        final DataLayout dataLayout;
        final ArrayList<Type> types = new ArrayList<>();

        private long offset = 0;

        DataSection(DataLayout dataLayout) {
            this.dataLayout = dataLayout;
        }

        long add(GlobalVariable global, Type type) throws Type.TypeOverflowException {
            int alignment = getAlignment(dataLayout, global, type);
            int padding = Type.getPadding(offset, alignment);
            addPaddingTypes(types, padding);
            offset = Type.addUnsignedExact(offset, padding);
            long ret = offset;
            types.add(type);
            offset = Type.addUnsignedExact(offset, type.getSize(dataLayout));
            return ret;
        }

        LLVMAllocateNode createAllocateNode(NodeFactory factory, String typeName, boolean readOnly) {
            if (offset > 0) {
                StructureType structType = StructureType.createNamedFromList(typeName, true, types);
                return factory.createAllocateGlobalsBlock(structType, readOnly);
            } else {
                return null;
            }
        }
    }

    private static void addPaddingTypes(ArrayList<Type> result, int padding) {
        assert padding >= 0;
        int remaining = padding;
        while (remaining > 0) {
            int size = Math.min(Long.BYTES, Integer.highestOneBit(remaining));
            result.add(PrimitiveType.getIntegerType(size * Byte.SIZE));
            remaining -= size;
        }
    }

    /**
     * Globals of pointer type need to be handles specially because they can potentially contain a
     * foreign object.
     */
    private static boolean isSpecialGlobalSlot(Type type) {
        return type instanceof PointerType;
    }

    private static int getAlignment(DataLayout dataLayout, GlobalVariable global, Type type) {
        return global.getAlign() > 0 ? 1 << (global.getAlign() - 1) : type.getAlignment(dataLayout);
    }
}
