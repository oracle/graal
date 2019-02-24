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

import java.lang.reflect.Array;
import java.security.ProtectionDomain;
import java.util.Arrays;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

import sun.misc.Unsafe;

@EspressoSubstitutions
public final class Target_sun_misc_Unsafe {

    private static final int SAFETY_FIELD_OFFSET = 123456789;

    private static Unsafe U;

    static {
        try {
            java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Substitution(hasReceiver = true)
    public static synchronized int arrayBaseOffset(@SuppressWarnings("unused") Object self, @Host(Class.class) StaticObjectClass clazz) {
        assert clazz.getMirrorKlass().isArray();
        if (clazz.getMirrorKlass().getComponentType().isPrimitive()) {
            Class<?> hostPrimitive = clazz.getMirrorKlass().getComponentType().getJavaKind().toJavaClass();
            return U.arrayBaseOffset(Array.newInstance(hostPrimitive, 0).getClass());
        } else {
            // Just a reference type.
            return U.arrayBaseOffset(Object[].class);
        }
    }

    @Substitution(hasReceiver = true)
    public static synchronized int arrayIndexScale(@SuppressWarnings("unused") Object self, @Host(Class.class) StaticObjectClass clazz) {
        assert clazz.getMirrorKlass().isArray();
        if (clazz.getMirrorKlass().getComponentType().isPrimitive()) {
            Class<?> hostPrimitive = clazz.getMirrorKlass().getComponentType().getJavaKind().toJavaClass();
            return U.arrayIndexScale(Array.newInstance(hostPrimitive, 0).getClass());
        } else {
            // Just a reference type.
            return U.arrayIndexScale(Object[].class);
        }
    }

    @Substitution(hasReceiver = true)
    public static synchronized int addressSize(@SuppressWarnings("unused") Object self) {
        return 4;
    }

    @Substitution(hasReceiver = true)
    public static synchronized long objectFieldOffset(@SuppressWarnings("unused") Object self, @Host(java.lang.reflect.Field.class) StaticObjectImpl field) {
        Field target = getReflectiveFieldRoot(field);
        return SAFETY_FIELD_OFFSET + target.getSlot();
    }

    @Substitution(hasReceiver = true)
    public static synchronized final boolean compareAndSwapObject(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, Object before, Object after) {
        if (holder instanceof StaticObjectArray) {
            return U.compareAndSwapObject(((StaticObjectArray) holder).unwrap(), offset, before, after);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, offset <-> field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        Object inTheField = f.get(holder);
        if (inTheField == before) {
            f.set(holder, after);
            return true;
        } else {
            return false;
        }
    }

    // FIXME(peterssen): This abomination must go, once the object model land.
    private static Field getInstanceFieldFromIndex(StaticObject holder, int slot) {
        if (!(0 <= slot && slot < (1 << 16))) {
            throw EspressoError.shouldNotReachHere("the field offset is not normalized");
        }
        if (holder.isStaticStorage()) {
            // Lookup static field in current class.
            for (Field f : holder.getKlass().getDeclaredFields()) {
                if (f.isStatic() && f.getSlot() == slot) {
                    return f;
                }
            }
        } else {
            // Lookup nstance field in current class and superclasses.
            for (Klass k = holder.getKlass(); k != null; k = k.getSuperKlass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (!f.isStatic() && f.getSlot() == slot) {
                        return f;
                    }
                }
            }
        }

        throw EspressoError.shouldNotReachHere("Field with slot " + slot + " not found");
    }

    @Substitution(hasReceiver = true)
    public static synchronized int getIntVolatile(@SuppressWarnings("unused") Object unsafe, @Host(Object.class) StaticObject holder, long offset) {
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (int) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized long getLongVolatile(@SuppressWarnings("unused") Object unsafe, @Host(Object.class) StaticObject holder, long offset) {
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (long) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized @Host(Class.class) StaticObject defineClass(@SuppressWarnings("unused") StaticObject self, @Host(String.class) StaticObject name,
                    @Host(byte[].class) StaticObject guestBuf, int offset, int len, @Host(ClassLoader.class) StaticObject loader,
                    @SuppressWarnings("unused") @Host(ProtectionDomain.class) StaticObject pd) {
        // TODO(peterssen): Protection domain is ignored.
        byte[] buf = ((StaticObjectArray) guestBuf).unwrap();
        byte[] bytes = Arrays.copyOfRange(buf, offset, len);
        return EspressoLanguage.getCurrentContext().getRegistries().defineKlass(self.getKlass().getTypes().fromClassGetName(Meta.toHostString(name)), bytes, loader).mirror();
    }

    @Substitution(hasReceiver = true)
    public static synchronized boolean compareAndSwapInt(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, int before, int after) {
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        int inTheField = (int) f.get(holder);
        if (inTheField == before) {
            f.set(holder, after);
            return true;
        } else {
            return false;
        }
    }

    @Substitution(hasReceiver = true)
    public static synchronized boolean compareAndSwapLong(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, long before, long after) {
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        long inTheField = (long) f.get(holder);
        if (inTheField == before) {
            f.set(holder, after);
            return true;
        } else {
            return false;
        }
    }

    @Substitution
    public static synchronized void registerNatives() {
        /* nop */
    }

    @Substitution(hasReceiver = true)
    public static synchronized long allocateMemory(@SuppressWarnings("unused") Object self, long length) {
        return U.allocateMemory(length);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void freeMemory(@SuppressWarnings("unused") Object self, long address) {
        U.freeMemory(address);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putLong(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, long x) {
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        f.set(holder, x);
    }



    @Substitution(hasReceiver = true)
    public static synchronized byte getByte(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getByte(((StaticObjectArray) holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (byte) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized byte getByteVolatile(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getByteVolatile(((StaticObjectArray) holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (byte) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized short getShortVolatile(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getShortVolatile(((StaticObjectArray) holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (short) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized char getCharVolatile(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getCharVolatile(((StaticObjectArray) holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (char) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized float getFloatVolatile(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getFloatVolatile(((StaticObjectArray) holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (float) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized double getDoubleVolatile(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getDoubleVolatile(((StaticObjectArray) holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (double) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized boolean getBooleanVolatile(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getBooleanVolatile(((StaticObjectArray) holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (boolean) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized int getInt(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getInt(((StaticObjectArray) holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (int) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized boolean getBoolean(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getBoolean(((StaticObjectArray) holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (boolean) f.get(holder);
    }

    // region get*(long offset)

    @Substitution(hasReceiver = true)
    public static synchronized byte getByte(@SuppressWarnings("unused") Object self, long offset) {
        return U.getByte(offset);
    }

    @Substitution(hasReceiver = true)
    public static synchronized char getChar(@SuppressWarnings("unused") Object self, long offset) {
        return U.getChar(offset);
    }

    @Substitution(hasReceiver = true)
    public static synchronized short getShort(@SuppressWarnings("unused") Object self, long offset) {
        return U.getShort(offset);
    }

    @Substitution(hasReceiver = true)
    public static synchronized int getInt(@SuppressWarnings("unused") Object self, long offset) {
        return U.getInt(offset);
    }

    @Substitution(hasReceiver = true)
    public static synchronized float getFloat(@SuppressWarnings("unused") Object self, long offset) {
        return U.getFloat(offset);
    }

    @Substitution(hasReceiver = true)
    public static synchronized long getLong(@SuppressWarnings("unused") Object self, long offset) {
        return U.getLong(offset);
    }

    @Substitution(hasReceiver = true)
    public static synchronized double getDouble(@SuppressWarnings("unused") Object self, long offset) {
        return U.getDouble(offset);
    }

    // endregion get*(long offset)

    @Substitution(hasReceiver = true)
    public static synchronized long getLong(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getLong(((StaticObjectArray) holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return (long) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized Object getObjectVolatile(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getObjectVolatile(((StaticObjectArray) holder).unwrap(), offset);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized Object getObject(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return U.getObjectVolatile(((StaticObjectArray) holder).unwrap(), offset);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        return f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putObjectVolatile(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, Object value) {
        if (holder instanceof StaticObjectArray) {
            U.putObjectVolatile(((StaticObjectArray) holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putObject(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, Object value) {
        if (holder instanceof StaticObjectArray) {
            U.putObject(((StaticObjectArray) holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void ensureClassInitialized(@SuppressWarnings("unused") Object self, @Host(Class.class) StaticObject clazz) {
        ((StaticObjectClass) clazz).getMirrorKlass().safeInitialize();
    }

    @Substitution(hasReceiver = true)
    public static synchronized void copyMemory(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject srcBase, long srcOffset, @Host(Object.class) StaticObject destBase, long destOffset,
                    long bytes) {
        if (bytes == 0) {
            return;
        }
        U.copyMemory(MetaUtil.unwrapArrayOrNull(srcBase), srcOffset, MetaUtil.unwrapArrayOrNull(destBase), destOffset, bytes);
    }

    // region put*(long offset, * value)

    @Substitution(hasReceiver = true)
    public static synchronized void putByte(@SuppressWarnings("unused") Object self, long offset, byte value) {
        U.putByte(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putChar(@SuppressWarnings("unused") Object self, long offset, char value) {
        U.putChar(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putShort(@SuppressWarnings("unused") Object self, long offset, short value) {
        U.putShort(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putInt(@SuppressWarnings("unused") Object self, long offset, int value) {
        U.putInt(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putFloat(@SuppressWarnings("unused") Object self, long offset, float value) {
        U.putFloat(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putDouble(@SuppressWarnings("unused") Object self, long offset, double value) {
        U.putDouble(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putLong(@SuppressWarnings("unused") Object self, long offset, long x) {
        U.putLong(offset, x);
    }

    // endregion put*(long offset, * value)

    @Substitution(hasReceiver = true)
    public static synchronized void putByte(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, byte value) {
        if (holder instanceof StaticObjectArray) {
            U.putByte(((StaticObjectArray) holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putShort(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, short value) {
        if (holder instanceof StaticObjectArray) {
            U.putShort(((StaticObjectArray) holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putInt(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, int value) {
        if (holder instanceof StaticObjectArray) {
            U.putInt(((StaticObjectArray) holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putFloat(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, float value) {
        if (holder instanceof StaticObjectArray) {
            U.putFloat(((StaticObjectArray) holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putBoolean(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, boolean value) {
        if (holder instanceof StaticObjectArray) {
            U.putBoolean(((StaticObjectArray) holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putDouble(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, double value) {
        if (holder instanceof StaticObjectArray) {
            U.putDouble(((StaticObjectArray) holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static synchronized void putChar(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject holder, long offset, char value) {
        if (holder instanceof StaticObjectArray) {
            U.putChar(((StaticObjectArray) holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static Object allocateInstance(@SuppressWarnings("unused") Object self, @Host(Class.class) StaticObject clazz) { // throws
        // InstantiationException;
        assert !((StaticObjectClass) clazz).getMirrorKlass().isAbstract();
        return InterpreterToVM.newObject(((StaticObjectClass) clazz).getMirrorKlass());
    }

    @Substitution(hasReceiver = true)
    public static int pageSize(@SuppressWarnings("unused") Object self) {
        return U.pageSize();
    }

    @Substitution(hasReceiver = true)
    public static synchronized void setMemory(@SuppressWarnings("unused") Object self, @Host(Object.class) StaticObject o, long offset, long bytes, byte value) {
        U.setMemory(StaticObject.isNull(o) ? null : o, offset, bytes, value);
    }

    private static Field getReflectiveFieldRoot(@Host(java.lang.reflect.Field.class) StaticObject seed) {
        Meta meta = seed.getKlass().getMeta();
        StaticObject curField = seed;
        Field target = null;
        while (target == null) {
            target = (Field) ((StaticObjectImpl) curField).getHiddenField(Target_java_lang_Class.HIDDEN_FIELD_KEY);
            if (target == null) {
                curField = (StaticObject) meta.Field_root.get(curField);
            }
        }
        return target;
    }

    @Substitution(hasReceiver = true)
    public static long staticFieldOffset(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(java.lang.reflect.Field.class) StaticObject field) {
        return getReflectiveFieldRoot(field).getSlot() + SAFETY_FIELD_OFFSET;
    }

    @Substitution(hasReceiver = true)
    public static synchronized Object staticFieldBase(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(java.lang.reflect.Field.class) StaticObject field) {
        Field target = getReflectiveFieldRoot(field);
        return target.getDeclaringKlass().tryInitializeAndGetStatics();
    }

    @SuppressWarnings("deprecation")
    @Substitution(hasReceiver = true)
    public static void monitorEnter(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject object) {
        U.monitorEnter(object);
    }

    @SuppressWarnings("deprecation")
    @Substitution(hasReceiver = true)
    public static void monitorExit(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject object) {
        U.monitorExit(object);
    }

    @Substitution(hasReceiver = true)
    public static void throwException(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Throwable.class) StaticObject ee) {
        throw new EspressoException(ee);
    }


    /**
     * Block current thread, returning when a balancing
     * <tt>unpark</tt> occurs, or a balancing <tt>unpark</tt> has
     * already occurred, or the thread is interrupted, or, if not
     * absolute and time is not zero, the given time nanoseconds have
     * elapsed, or if absolute, the given deadline in milliseconds
     * since Epoch has passed, or spuriously (i.e., returning for no
     * "reason"). Note: This operation is in the Unsafe class only
     * because <tt>unpark</tt> is, so it would be strange to place it
     * elsewhere.
     */
    @Substitution(hasReceiver = true)
    public static void park(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, boolean b, long l) {
        U.park(b, l);
    }

    /**
     * Unblock the given thread blocked on <tt>park</tt>, or, if it is
     * not blocked, cause the subsequent call to <tt>park</tt> not to
     * block.  Note: this operation is "unsafe" solely because the
     * caller must somehow ensure that the thread has not been
     * destroyed. Nothing special is usually required to ensure this
     * when called from Java (in which there will ordinarily be a live
     * reference to the thread) but this is not nearly-automatically
     * so when calling from native code.
     * @param thread the thread to unpark.
     *
     */
    @Substitution(hasReceiver = true)
    public static void unpark(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject thread) {
        Thread hostThread = (Thread)((StaticObjectImpl) thread).getHiddenField(Target_java_lang_Thread.HIDDEN_HOST_THREAD);
        U.unpark(hostThread);
    }

    /**
     * Fetches a native pointer from a given memory address.  If the address is
     * zero, or does not point into a block obtained from {@link
     * #allocateMemory}, the results are undefined.
     *
     * <p> If the native pointer is less than 64 bits wide, it is extended as
     * an unsigned number to a Java long.  The pointer may be indexed by any
     * given byte offset, simply by adding that offset (as a simple integer) to
     * the long representing the pointer.  The number of bytes actually read
     * from the target address maybe determined by consulting {@link
     * #addressSize}.
     *
     * @see #allocateMemory
     */
    @Substitution(hasReceiver = true)
    public static long getAddress(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long address) {
        return U.getAddress(address);
    }

    /**
     * Stores a native pointer into a given memory address.  If the address is
     * zero, or does not point into a block obtained from {@link
     * #allocateMemory}, the results are undefined.
     *
     * <p> The number of bytes actually written at the target address maybe
     * determined by consulting {@link #addressSize}.
     *
     * @see #getAddress
     */
    @Substitution(hasReceiver = true)
    public static void putAddress(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long address, long x) {
        U.putAddress(address, x);
    }
}
