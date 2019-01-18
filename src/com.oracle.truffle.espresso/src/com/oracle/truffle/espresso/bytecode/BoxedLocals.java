package com.oracle.truffle.espresso.bytecode;

import com.oracle.truffle.espresso.runtime.ReturnAddress;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class BoxedLocals implements Locals {
    private final Object[] slots;

    public BoxedLocals(int maxLocals) {
        this.slots = new Object[maxLocals];
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
        slots[slot] = value;
    }

    @Override
    public void putLong(int slot, long value) {
        slots[slot] = value;
    }

    @Override
    public void putFloat(int slot, float value) {
        slots[slot] = value;
    }

    @Override
    public void putDouble(int slot, double value) {
        slots[slot] = value;
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
        return (int) slots[slot];
    }

    @Override
    public float getFloat(int slot) {
        return (float) slots[slot];
    }

    @Override
    public long getLong(int slot) {
        return (long) slots[slot];
    }

    @Override
    public double getDouble(int slot) {
        return (double) slots[slot];
    }
}
