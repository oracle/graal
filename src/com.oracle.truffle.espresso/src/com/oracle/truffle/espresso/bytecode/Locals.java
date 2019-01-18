package com.oracle.truffle.espresso.bytecode;

import com.oracle.truffle.espresso.runtime.ReturnAddress;
import com.oracle.truffle.espresso.runtime.StaticObject;

public interface Locals {
    void putObject(int slot, StaticObject value);

    void putObjectOrReturnAddress(int slot, Object value);

    void putInt(int slot, int value);

    void putLong(int slot, long value);

    void putFloat(int slot, float value);

    void putDouble(int slot, double value);

    StaticObject getObject(int slot);

    ReturnAddress getReturnAddress(int slot);

    int getInt(int slot);

    float getFloat(int slot);

    long getLong(int slot);

    double getDouble(int slot);
}
