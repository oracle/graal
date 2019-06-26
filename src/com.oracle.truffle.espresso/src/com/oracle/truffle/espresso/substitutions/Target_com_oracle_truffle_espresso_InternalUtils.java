package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import sun.misc.Unsafe;

import java.util.Arrays;

@EspressoSubstitutions
public class Target_com_oracle_truffle_espresso_InternalUtils {

    @Substitution
    public static @Host(String[].class) StaticObject getUnderlyingPrimitiveFieldArray(@Host(Class.class) StaticObject clazz) {
        int i = 0;
        Klass k = clazz.getMirrorKlass();
        int maxLen;
        if (k instanceof ObjectKlass) {
            maxLen = ((ObjectKlass) k).getPrimitiveFieldTotalByteCount();
        } else {
            return new StaticObject(k.getMeta().String.getArrayClass(), StaticObject.EMPTY_ARRAY);
        }
        StaticObject[] result = new StaticObject[maxLen];
        Meta meta = k.getMeta();
        StaticObject unused = meta.toGuestString("<>");
        Arrays.fill(result, unused);
        try {
            while (true) {
                Field f = k.lookupFieldTable(i);
                if (!f.isStatic() && f.getKind().isPrimitive()) {
                    for (int j = f.getFieldIndex(); j < f.getFieldIndex() + f.getKind().getByteCount(); j++) {
                        result[j] = meta.toGuestString(f.getName());
                    }
                }
                i++;
            }
        } catch (AssertionError | IndexOutOfBoundsException e) {

        }
        return new StaticObject(k.getMeta().String.getArrayClass(), result);
    }

    @Substitution
    public static int getPrimitiveFieldByteCount(@Host(Class.class) StaticObject clazz) {
        Klass k = clazz.getMirrorKlass();
        if (k instanceof ObjectKlass) {
            return ((ObjectKlass) k).getPrimitiveFieldTotalByteCount();
        } else {
            return 0;
        }
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

    @Substitution
    public static boolean inEspresso() {
        return true;
    }
}
