package com.oracle.truffle.espresso.runtime;

public class BootstrapMethodsAttribute extends AttributeInfo {

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

    public BootstrapMethodsAttribute(String name, Entry[] entries) {
        super(name, null);
        this.entries = entries;
    }
}
