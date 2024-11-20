package com.oracle.objectfile.debugentry;

import jdk.vm.ci.meta.JavaKind;

import java.util.Objects;

public final class LocalEntry {
    private final String name;
    private final TypeEntry type;
    private final JavaKind kind;
    private final int slot;
    private int line;

    public LocalEntry(String name, TypeEntry type, JavaKind kind, int slot, int line) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.slot = slot;
        this.line = line;
    }

    public String name() {
        return name;
    }

    public TypeEntry type() {
        return type;
    }

    public JavaKind kind() {
        return kind;
    }

    public int slot() {
        return slot;
    }

    public int line() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (LocalEntry) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.type, that.type) &&
                Objects.equals(this.kind, that.kind) &&
                this.slot == that.slot &&
                this.line == that.line;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, kind, slot, line);
    }

    @Override
    public String toString() {
        return String.format("Local(%s type=%s slot=%d line=%d)", name, type.getTypeName(), slot, line);
    }
}
