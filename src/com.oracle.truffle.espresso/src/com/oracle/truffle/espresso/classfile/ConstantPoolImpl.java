package com.oracle.truffle.espresso.classfile;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Immutable constant pool implementation backed by an array of constants.
 */
final class ConstantPoolImpl extends ConstantPool {

    @CompilationFinal(dimensions = 1) //
    private final PoolConstant[] constants;

    ConstantPoolImpl(PoolConstant[] constants) {
        this.constants = Objects.requireNonNull(constants);
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
}
