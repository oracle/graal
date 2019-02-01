/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import sun.misc.Unsafe;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.Arrays;

import static com.oracle.truffle.espresso.meta.Meta.meta;

@EspressoSubstitutions
public final class Target_sun_misc_Unsafe {

    private static final int SAFETY_FIELD_OFFSET = 123456789;

    private static Unsafe U;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Substitution(hasReceiver = true)
    public static int arrayBaseOffset(@SuppressWarnings("unused") Object self, @Host(Class.class) StaticObjectClass clazz) {
        assert clazz.getMirror().isArray();
        if (clazz.getMirror().getComponentType().isPrimitive()) {
            Class<?> hostPrimitive = clazz.getMirror().getComponentType().getJavaKind().toJavaClass();
            return U.arrayBaseOffset(Array.newInstance(hostPrimitive, 0).getClass());
        } else {
            // Just a reference type.
            return U.arrayBaseOffset(Object[].class);
        }
    }

    @Substitution(hasReceiver = true)
    public static int arrayIndexScale(@SuppressWarnings("unused") Object self, @Host(Class.class) StaticObjectClass clazz) {
        assert clazz.getMirror().isArray();
        if (clazz.getMirror().getComponentType().isPrimitive()) {
            Class<?> hostPrimitive = clazz.getMirror().getComponentType().getJavaKind().toJavaClass();
            return U.arrayIndexScale(Array.newInstance(hostPrimitive, 0).getClass());
        } else {
            // Just a reference type.
            return U.arrayIndexScale(Object[].class);
        }
    }

    @Substitution(hasReceiver = true)
    public static int addressSize(@SuppressWarnings("unused") Object self) {
        return 4;
    }

    @Substitution(hasReceiver = true)
    public static long objectFieldOffset(@SuppressWarnings("unused") Object self, @Host(Field.class) StaticObjectImpl field) {
        StaticObjectImpl curField = field;
        FieldInfo target = null;
        while (target == null) {
            target = (FieldInfo) curField.getHiddenField(Target_java_lang_Class.HIDDEN_FIELD_KEY);
            if (target == null) {
                curField = (StaticObjectImpl) meta(curField).declaredField("root").get();
            }
        }

        return SAFETY_FIELD_OFFSET + target.getSlot();
    }

    @Substitution(hasReceiver = true)
    public static final boolean compareAndSwapObject(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, Object before, Object after) {
        if (holder instanceof StaticObjectArray) {
            return U.compareAndSwapObject(((StaticObjectArray) holder).unwrap(), offset, before, after);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, offset <-> field index.
        Meta.Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        Object inTheField = f.get(holder);
        if (inTheField == before) {
            f.set(holder, after);
            return true;
        } else {
            return false;
        }
    }

    // FIXME(peterssen): This abomination must go, once the object model land.
    private static Meta.Field getInstanceFieldFromIndex(StaticObject holder, int index) {
        if (!(0 <= index && index < (1 << 16))) {
            throw EspressoError.shouldNotReachHere("the field offset is not normalized");
        }
        Meta.Klass klass = meta(holder.getKlass());

        FieldInfo field = (holder.getKlass().tryInitializeAndGetStatics() == holder)
                ? klass.rawKlass().getStaticFields()[index]
                : klass.rawKlass().getInstanceFields(true)[index];

        assert field.getSlot() == index;
        return meta(field);
    }

    @Substitution(hasReceiver = true)
    public static int getIntVolatile(@SuppressWarnings("unused") Object unsafe, @Host(Object.class) StaticObject holder, long offset) {
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        Meta.Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (int) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static long getLongVolatile(@SuppressWarnings("unused") Object unsafe, @Host(Object.class) StaticObject holder, long offset) {
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        Meta.Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (long) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static @Host(Class.class) StaticObject defineClass(@SuppressWarnings("unused") Object self, @Host(String.class) StaticObject name,
                                                              @Host(byte[].class) StaticObject guestBuf, int offset, int len, @Host(ClassLoader.class) StaticObject loader,
                                                              @SuppressWarnings("unused") @Host(ProtectionDomain.class) StaticObject pd) {
        // TODO(peterssen): Protection domain is ignored.
        byte[] buf = ((StaticObjectArray) guestBuf).unwrap();
        byte[] bytes = Arrays.copyOfRange(buf, offset, len);
        return EspressoLanguage.getCurrentContext().getRegistries().defineKlass(Meta.toHostString(name), bytes, loader).mirror();

    }

    @Substitution(hasReceiver = true)
    public static boolean compareAndSwapInt(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, int before, int after) {
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        Meta.Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        int inTheField = (int) f.get(holder);
        if (inTheField == before) {
            f.set(holder, after);
            return true;
        } else {
            return false;
        }
    }

    @Substitution(hasReceiver = true)
    public static boolean compareAndSwapLong(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, long before, long after) {
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        Meta.Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        long inTheField = (long) f.get(holder);
        if (inTheField == before) {
            f.set(holder, after);
            return true;
        } else {
            return false;
        }
    }

    @Substitution
    public static void registerNatives() {
        /* nop */
    }

    @Substitution(hasReceiver = true)
    public static long allocateMemory(@SuppressWarnings("unused") Object self, long length) {
        return U.allocateMemory(length);
    }

    @Substitution(hasReceiver = true)
    public static void freeMemory(@SuppressWarnings("unused") Object self, long address) {
        U.freeMemory(address);
    }

    @Substitution(hasReceiver = true)
    public static void putLong(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, long x) {
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        Meta.Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        f.set(holder, x);
    }

    @Substitution(hasReceiver = true)
    public static void putLong(@SuppressWarnings("unused") Object self, long offset, long x) {
        U.putLong(offset, x);
    }

    @Substitution(hasReceiver = true)
    public static byte getByte(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        Meta.Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (byte) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static int getInt(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        Meta.Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (int) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static byte getByte(@SuppressWarnings("unused") Object self, long offset) {
        return U.getByte(offset);
    }

    @Substitution(hasReceiver = true)
    public static int getInt(@SuppressWarnings("unused") Object self, long offset) {
        return U.getInt(offset);
    }

    @Substitution(hasReceiver = true)
    public static Object getObjectVolatile(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getObjectVolatile(((StaticObjectArray) holder).unwrap(), offset);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        Meta.Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static void putObjectVolatile(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, Object value) {
        if (holder instanceof StaticObjectArray) {
            U.putObjectVolatile(((StaticObjectArray) holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        Meta.Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void ensureClassInitialized(@SuppressWarnings("unused") Object self, @Host(Class.class) StaticObject clazz) {
        meta(((StaticObjectClass) clazz).getMirror()).safeInitialize();
    }

    @Substitution(hasReceiver = true)
    public static void copyMemory(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject srcBase, long srcOffset, @Host(Object.class) StaticObject destBase, long destOffset,
                                  long bytes) {
        if (bytes == 0) {
            return;
        }
        U.copyMemory(MetaUtil.unwrap(srcBase), srcOffset, MetaUtil.unwrap(destBase), destOffset, bytes);
    }

    @Substitution(hasReceiver = true)
    public static void putByte(@SuppressWarnings("unused") Object self, long offset, byte value) {
        U.putByte(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putByte(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject object, long offset, byte value) {
        U.putByte(MetaUtil.unwrap(object), offset, value);
    }

    @Substitution(hasReceiver = true)
    public static Object allocateInstance(@SuppressWarnings("unused") Object self, @Host(Class.class) StaticObject clazz) { // throws
        // InstantiationException;
        assert !((StaticObjectClass) clazz).getMirror().isAbstract();
        return EspressoLanguage.getCurrentContext().getInterpreterToVM().newObject(((StaticObjectClass) clazz).getMirror());
    }

    @Substitution(hasReceiver = true)
    public static int pageSize(@SuppressWarnings("unused") Object self) {
        return U.pageSize();
    }

    @Substitution(hasReceiver = true)
    public static void setMemory(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject o, long offset, long bytes, byte value) {
        U.setMemory(StaticObject.isNull(o) ? null : o, offset, bytes, value);
    }

    @Substitution(hasReceiver = true)
    public static long staticFieldOffset(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Field.class) StaticObject field) {
        StaticObject curField = field;
        FieldInfo target = null;
        while (target == null) {
            target = (FieldInfo) ((StaticObjectImpl) curField).getHiddenField(Target_java_lang_Class.HIDDEN_FIELD_KEY);
            if (target == null) {
                curField = (StaticObject) meta(curField).declaredField("root").get();
            }
        }
        Meta.Field f = meta(target);
        return f.getSlot() + SAFETY_FIELD_OFFSET;
    }

    @Substitution(hasReceiver = true)
    public static Object staticFieldBase(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Field.class) StaticObject field) {
        StaticObject curField = field;
        FieldInfo target = null;
        while (target == null) {
            target = (FieldInfo) ((StaticObjectImpl) curField).getHiddenField(Target_java_lang_Class.HIDDEN_FIELD_KEY);
            if (target == null) {
                curField = (StaticObject) meta(curField).declaredField("root").get();
            }
        }
        Meta.Field f = meta(target);
        return f.getDeclaringClass().rawKlass().tryInitializeAndGetStatics();
    }
}
