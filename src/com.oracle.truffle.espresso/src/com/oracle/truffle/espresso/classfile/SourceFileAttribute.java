package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class SourceFileAttribute extends Attribute {

    public static final Symbol<Name> NAME = Name.SourceFile;

    private final int sourceFileIndex;

    public SourceFileAttribute(Symbol<Name> name, int sourceFileIndex) {
        super(name, null);
        this.sourceFileIndex = sourceFileIndex;
    }

    public int getSourceFileIndex() {
        return sourceFileIndex;
    }
}
