package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;
import sun.misc.Unsafe;

@EspressoSubstitutions
public class Target_com_oracle_truffle_espresso_InternalUtils {

    @Substitution
    public static @Host(byte[].class) StaticObject getUnderlyingFieldArray(@Host(Object.class) StaticObject self) {
        return new StaticObject(self.getKlass().getMeta().Byte.getArrayClass(), self.cloneFields());
    }

    @Substitution
    public static @Host(String.class) StaticObject toVerboseString(@Host(Object.class) StaticObject self) {
        return self.getKlass().getMeta().toGuestString(self.toVerboseString());
    }

    @Substitution
    public static int bytesUsed(@Host(Class.class) StaticObject clazz) {
        Klass k = clazz.getMirrorKlass();
        int total = 0;
        if (k.isArray()) {
            // ArrayKlass reference
            total += JavaKind.Int.getByteCount();
            // null reference for primitive field array
            total += JavaKind.Int.getByteCount();
            // Header of the Object field array + storing its reference
            total += Unsafe.ARRAY_OBJECT_BASE_OFFSET + JavaKind.Int.getByteCount();
            return total;
        } else {
            ObjectKlass klass = (ObjectKlass) k;
            // Bytes used by the primitive fields
            total += klass.getPrimitiveFieldTotalByteCount();
            // Bytes used by the Object fields
            total += klass.getObjectFieldsCount() * JavaKind.Int.getByteCount();
            // Header of the primitive field array + storing its reference
            total += Unsafe.ARRAY_BYTE_BASE_OFFSET + JavaKind.Int.getByteCount();
            // Header of the Object field array + storing its reference
            total += Unsafe.ARRAY_OBJECT_BASE_OFFSET + JavaKind.Int.getByteCount();
            // Reference to the Klass object.
            total += JavaKind.Int.getByteCount();
            return total;
        }
    }
}
