package com.oracle.truffle.llvm.parser.base.model.metadata.subtypes;

public interface MSTSizeAlignOffset {
    long getSize();

    void setSize(long size);

    long getAlign();

    void setAlign(long align);

    long getOffset();

    void setOffset(long offset);
}
