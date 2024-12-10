package com.oracle.objectfile.debugentry;

public record LocalEntry(String name, TypeEntry type, int slot, int line) {

    @Override
    public String toString() {
        return String.format("Local(%s type=%s slot=%d line=%d)", name, type.getTypeName(), slot, line);
    }
}
