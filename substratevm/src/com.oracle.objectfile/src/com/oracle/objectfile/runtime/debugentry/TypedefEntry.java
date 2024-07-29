package com.oracle.objectfile.runtime.debugentry;

import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugTypeInfo.DebugTypeKind;

public class TypedefEntry extends StructureTypeEntry {
    public TypedefEntry(String typeName, int size) {
        super(typeName, size);
    }

    @Override
    public DebugTypeKind typeKind() {
        return DebugTypeKind.TYPEDEF;
    }

    public int getFileIdx(FileEntry fileEntry) {
        return 0;
    }
}
