package com.oracle.truffle.espresso.runtime;

import java.util.Arrays;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.PoolConstant;
import com.oracle.truffle.espresso.classfile.Utf8Constant;
import com.oracle.truffle.espresso.types.TypeDescriptor;

public class FieldInfo {

    private final char[] fieldData;

    /**
     * Index in {@link #fieldData} for the field inspected by this object.
     */
    private int baseIndex;

    private final ConstantPool pool;

    public enum Offset {
        FLAGS(0),
        NAME(1),
        TYPE(2),
        CONSTANT_VALUE(3);
        private final int value;

        Offset(int value) {
            this.value = value;
        }

        static final int LENGTH = values().length;
    }

    public FieldInfo(ConstantPool pool, char[] fields) {
        this.fieldData = fields;
        this.pool = pool;
    }

    public void initForField(int fieldIndex) {
        baseIndex = fieldIndex * Offset.LENGTH;
    }

    public void set(Offset key, int value) {
        fieldData[baseIndex + key.value] = PoolConstant.u2(value);
    }

    private int get(Offset key) {
        return fieldData[baseIndex + key.value];
    }

    public Klass getDeclaringClass() {
        return null;
    }

    public Utf8Constant getName() {
        return pool.utf8At(get(Offset.NAME));
    }

    public TypeDescriptor getType() {
        return pool.makeTypeDescriptor(pool.utf8At(get(Offset.NAME)).getValue());
    }

    public static char[] allocateFieldData(int count) {
        char[] data = new char[(count * Offset.LENGTH) + 1];
        data[data.length - 1] = PoolConstant.u2(count);
        return data;
    }

    public static char[] allocateFieldGenericSignatureData(int count) {
        return new char[count];
    }

    public static char[] mergeFieldData(char[] fixedFieldData, char[] genericSignatureData, int genericSignatureDataCount) {
        int count = fixedFieldData[fixedFieldData.length - 1];
        int length = (count * Offset.LENGTH) + genericSignatureDataCount + 1;
        char[] data = Arrays.copyOf(fixedFieldData, length);
        System.arraycopy(genericSignatureData, 0, data, fixedFieldData.length - 1, genericSignatureDataCount);
        data[data.length - 1] = PoolConstant.u2(count);
        return data;
    }
}
