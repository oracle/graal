package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.runtime.AttributeInfo;

import java.util.Arrays;
import java.util.List;

public class InnerClassesAttribute extends AttributeInfo {

    public static class Entry {
        public final int innerClassIndex;
        public final int outerClassIndex;
        public final int innerNameIndex;
        public final int innerClassAccessFlags;
        public Entry(int innerClassIndex, int outerClassIndex, int innerNameIndex, int innerClassAccessFlags) {
            this.innerClassIndex = innerClassIndex;
            this.outerClassIndex = outerClassIndex;
            this.innerNameIndex = innerNameIndex;
            this.innerClassAccessFlags = innerClassAccessFlags;
        }
    }
    private final Entry[] entries;

    public List<Entry> entries() {
        return Arrays.asList(entries);
    }

    public InnerClassesAttribute(String name, Entry[] entries) {
        super(name, null);
        this.entries = entries;
    }
}
