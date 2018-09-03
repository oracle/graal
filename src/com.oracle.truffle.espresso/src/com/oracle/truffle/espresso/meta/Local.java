package com.oracle.truffle.espresso.meta;

import com.oracle.truffle.espresso.types.TypeDescriptor;

/**
 * Describes the type and bytecode index range in which a local variable is live.
 */
public class Local {

    private final String name;
    private final int startBci;
    private final int endBci;
    private final int slot;
    private final TypeDescriptor type;

    public Local(String name, TypeDescriptor type, int startBci, int endBci, int slot) {
        this.name = name;
        this.startBci = startBci;
        this.endBci = endBci;
        this.slot = slot;
        this.type = type;
    }

    public int getStartBCI() {
        return startBci;
    }

    public int getEndBCI() {
        return endBci;
    }

    public String getName() {
        return name;
    }

    public TypeDescriptor getType() {
        return type;
    }

    public int getSlot() {
        return slot;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Local)) {
            return false;
        }
        Local that = (Local) obj;
        return this.name.equals(that.name) && this.startBci == that.startBci && this.endBci == that.endBci && this.slot == that.slot && this.type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "LocalImpl<name=" + name + ", type=" + type + ", startBci=" + startBci + ", endBci=" + endBci + ", slot=" + slot + ">";
    }
}
