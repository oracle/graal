package com.oracle.svm.core.jfr.events;

import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

@RawStructure
public interface PointerArray extends PointerBase {
    @RawField
    int getSize();

    @RawField
    void setSize(int value);

    @RawField
    WordPointer getData();

    @RawField
    void setData(WordPointer value);
}
