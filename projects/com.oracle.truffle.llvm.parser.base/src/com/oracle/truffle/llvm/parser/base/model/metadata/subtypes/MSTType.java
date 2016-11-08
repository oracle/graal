package com.oracle.truffle.llvm.parser.base.model.metadata.subtypes;

import com.oracle.truffle.llvm.parser.base.model.blocks.MetadataBlock.MetadataReference;

public interface MSTType {
    MetadataReference getType();

    void setType(MetadataReference type);
}
