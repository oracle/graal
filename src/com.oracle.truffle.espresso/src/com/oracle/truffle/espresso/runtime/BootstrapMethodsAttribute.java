package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.impl.ByteString;
import com.oracle.truffle.espresso.impl.ByteString.Name;

public class BootstrapMethodsAttribute extends Attribute {

    public Entry[] getEntries() {
        return entries;
    }

    public static class Entry {
        final char bootstrapMethodRef;
        final char[] bootstrapArguments;

        public int numBootstrapArguments() {
            return bootstrapArguments.length;
        }

        public Entry(char bootstrapMethodRef, char[] bootstrapArguments) {
            this.bootstrapMethodRef = bootstrapMethodRef;
            this.bootstrapArguments = bootstrapArguments;
        }
    }

    private final Entry[] entries;

    public BootstrapMethodsAttribute(ByteString<Name> name, Entry[] entries) {
        super(name, null);
        this.entries = entries;
    }
}
