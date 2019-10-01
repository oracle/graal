package com.oracle.truffle.espresso.classfile;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Immutable constant pool implementation backed by an array of constants.
 */
final class ConstantPoolImpl extends ConstantPool {

    private final int majorVersion;

    @CompilationFinal(dimensions = 1) //
    private final PoolConstant[] constants;

    ConstantPoolImpl(PoolConstant[] constants, int majorVersion) {
        this.constants = Objects.requireNonNull(constants);
        this.majorVersion = majorVersion;
    }

    @Override
    public int length() {
        return constants.length;
    }

    @Override
    public PoolConstant at(int index, String description) {
        try {
            return constants[index];
        } catch (IndexOutOfBoundsException exception) {
            throw ConstantPool.classFormatError("Constant pool index (" + index + ")" + (description == null ? "" : " for " + description) + " is out of range");
        }
    }

    @Override
    public int getMajorVersion() {
        return majorVersion;
    }
}
