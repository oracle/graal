package com.oracle.truffle.espresso.bytecode;

import com.oracle.truffle.espresso.runtime.ReturnAddress;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class DualLocals implements Locals {
    private final Object[] slots;
    private final long[] primitiveSlots;

    public DualLocals(int maxLocals) {
        this.slots = new Object[maxLocals];
        this.primitiveSlots = new long[maxLocals];
    }

    @Override
    public void putObject(int slot, StaticObject value) {
        slots[slot] = value;
    }

    @Override
    public void putObjectOrReturnAddress(int slot, Object value) {
        assert value instanceof StaticObject || value instanceof ReturnAddress;
        slots[slot] = value;
    }

    @Override
    public void putInt(int slot, int value) {
        primitiveSlots[slot] = value;
    }

    @Override
    public void putLong(int slot, long value) {
        primitiveSlots[slot] = value;
    }

    @Override
    public void putFloat(int slot, float value) {
        primitiveSlots[slot] = Float.floatToRawIntBits(value);
    }

    @Override
    public void putDouble(int slot, double value) {
        primitiveSlots[slot] = Double.doubleToRawLongBits(value);
    }

    @Override
    public StaticObject getObject(int slot) {
        return (StaticObject) slots[slot];
    }

    @Override
    public ReturnAddress getReturnAddress(int slot) {
        return (ReturnAddress) slots[slot];
    }

    @Override
    public int getInt(int slot) {
        return (int) primitiveSlots[slot];
    }

    @Override
    public float getFloat(int slot) {
        return Float.intBitsToFloat((int) primitiveSlots[slot]);
    }

    @Override
    public long getLong(int slot) {
        return primitiveSlots[slot];
    }

    @Override
    public double getDouble(int slot) {
        return Double.longBitsToDouble(primitiveSlots[slot]);
    }
}