package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class MethodParametersAttribute extends Attribute {

    public static final Symbol<Name> NAME = Name.MethodParameters;

    public static final MethodParametersAttribute EMPTY = new MethodParametersAttribute(NAME, Entry.EMPTY_ARRAY);

    public Entry[] getEntries() {
        return entries;
    }

    public static final class Entry {

        public static final Entry[] EMPTY_ARRAY = new Entry[0];

        private final int nameIndex;
        private final int accessFlags;

        Entry(int nameIndex, int accessFlags) {
            this.nameIndex = nameIndex;
            this.accessFlags = accessFlags;
        }

        public int getNameIndex() {
            return nameIndex;
        }

        public int getAccessFlags() {
            return accessFlags;
        }
    }

    private final Entry[] entries;

    public MethodParametersAttribute(Symbol<Name> name, Entry[] entries) {
        super(name, null);
        this.entries = entries;
    }
}
