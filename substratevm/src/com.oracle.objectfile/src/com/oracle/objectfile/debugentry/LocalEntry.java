package com.oracle.objectfile.debugentry;

import jdk.vm.ci.meta.JavaKind;

public record LocalEntry(String name, TypeEntry type, JavaKind kind, int slot, int line) {

    @Override
    public String toString() {
        return String.format("Local(%s type=%s slot=%d line=%d)", name, type.getTypeName(), slot, line);
    }
}
