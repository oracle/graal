/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.analysis.liveness.LivenessAnalysis;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.LinkedKlass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.staticobject.StaticProperty;
import sun.misc.Unsafe;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

@EspressoSubstitutions
public class Target_com_oracle_truffle_espresso_InternalUtils {

    @Substitution
    public static @Host(String[].class) StaticObject getUnderlyingPrimitiveFieldArray(@Host(Class.class) StaticObject clazz) {
        int i = 0;
        Klass k = clazz.getMirrorKlass();
        int maxLen;
        if (k instanceof ObjectKlass) {
            maxLen = getPrimitiveInstanceFieldLastOffset(((ObjectKlass) k).getLinkedKlass());
        } else {
            return StaticObject.createArray(k.getMeta().java_lang_String.getArrayClass(), StaticObject.EMPTY_ARRAY);
        }
        StaticObject[] result = new StaticObject[maxLen];
        Meta meta = k.getMeta();
        StaticObject unused = meta.toGuestString("<>");
        Arrays.fill(result, unused);
        try {
            while (true) {
                Field f = k.lookupFieldTable(i);
                if (!f.isStatic() && f.getKind().isPrimitive()) {
                    for (int j = getOffset(f); j < getOffset(f) + f.getKind().getByteCount(); j++) {
                        result[j] = meta.toGuestString(f.getName());
                    }
                }
                i++;
            }
        } catch (AssertionError | IndexOutOfBoundsException e) {

        }
        return StaticObject.createArray(k.getMeta().java_lang_String.getArrayClass(), result);
    }

    @Substitution
    public static int getPrimitiveFieldByteCount(@Host(Class.class) StaticObject clazz) {
        Klass k = clazz.getMirrorKlass();
        if (k instanceof ObjectKlass) {
            return getPrimitiveInstanceFieldLastOffset(((ObjectKlass) k).getLinkedKlass());
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
            LinkedKlass lk = ((ObjectKlass) k).getLinkedKlass();
            // Bytes used by the primitive fields
            total += getPrimitiveInstanceFieldLastOffset(lk);
            // Bytes used by the Object fields
            total += getObjectFieldsCount(lk) * JavaKind.Int.getByteCount();
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

    @CompilerDirectives.TruffleBoundary
    @Substitution
    public static void triggerLivenessAnalysis(@Host(java.lang.reflect.Method.class) StaticObject method, @InjectMeta Meta meta) {
        Method m = Method.getHostReflectiveMethodRoot(method, meta);
        LivenessAnalysis.analyze(m);
    }

    private static int getOffset(Field f) {
        try {
            java.lang.reflect.Field linkedFieldField = Field.class.getDeclaredField("linkedField");
            linkedFieldField.setAccessible(true);
            Object linkedField = linkedFieldField.get(f);
            java.lang.reflect.Field offsetField = StaticProperty.class.getDeclaredField("offset");
            offsetField.setAccessible(true);
            return offsetField.getInt(linkedField);
        } catch(IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static int getPrimitiveInstanceFieldLastOffset(LinkedKlass lk) {
        return getLayoutProperty(lk, "lastOffset");
    }

    private static int getObjectFieldsCount(LinkedKlass lk) {
        return getLayoutProperty(lk, "objectArraySize");
    }

    private static int getLayoutProperty(LinkedKlass lk, String propertyName) {
        try {
            java.lang.reflect.Method getPropertyLayout = Class.forName("com.oracle.truffle.espresso.staticobject.ArrayBasedStaticShape").getDeclaredMethod("getPropertyLayout");
            getPropertyLayout.setAccessible(true);
            Object propertyLayout = getPropertyLayout.invoke(lk.getShape(false));
            java.lang.reflect.Field lastOffset = propertyLayout.getClass().getDeclaredField(propertyName);
            lastOffset.setAccessible(true);
            return lastOffset.getInt(propertyLayout);
        } catch(ClassNotFoundException | IllegalAccessException | InvocationTargetException | NoSuchFieldException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
