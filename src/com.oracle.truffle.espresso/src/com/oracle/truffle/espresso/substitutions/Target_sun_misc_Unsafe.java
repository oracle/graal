/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.locks.LockSupport;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ClassRegistries;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.LinkedKlass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.ParserKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread.State;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

@EspressoSubstitutions
public final class Target_sun_misc_Unsafe {

    static final int SAFETY_FIELD_OFFSET = 123456789;

    private static final Unsafe UNSAFE = UnsafeAccess.get();

    static {
        try {
            parkBlockerOffset = UNSAFE.objectFieldOffset(Thread.class.getDeclaredField("parkBlocker"));
        } catch (NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static @Host(Class.class) StaticObject defineAnonymousClass(
                    @Host(Unsafe.class) StaticObject self,
                    @Host(Class.class) StaticObject hostClass,
                    @Host(byte[].class) StaticObject data,
                    @Host(Object[].class) StaticObject constantPoolPatches,
                    @InjectMeta Meta meta) {
        EspressoContext context = meta.getContext();

        if (StaticObject.isNull(hostClass) || StaticObject.isNull(data)) {
            throw Meta.throwException(meta.java_lang_IllegalArgumentException);
        }

        byte[] bytes = data.unwrap();
        StaticObject[] patches = StaticObject.isNull(constantPoolPatches) ? null : constantPoolPatches.unwrap();
        Klass hostKlass = hostClass.getMirrorKlass();
        ClassfileStream cfs = new ClassfileStream(bytes, null);
        ParserKlass parserKlass = ClassfileParser.parse(cfs, null, hostKlass, context, patches);
        StaticObject classLoader = hostKlass.getDefiningClassLoader();

        // Inherit host class's protection domain.
        StaticObject clazz = defineAnonymousKlass(parserKlass, context, classLoader, hostKlass).mirror();
        StaticObject pd = (StaticObject) hostClass.getHiddenField(meta.HIDDEN_PROTECTION_DOMAIN);
        if (pd == null) {
            pd = StaticObject.NULL;
        }
        clazz.setHiddenField(meta.HIDDEN_PROTECTION_DOMAIN, pd);

        return clazz;
    }

    private static ObjectKlass defineAnonymousKlass(ParserKlass parserKlass, EspressoContext context, StaticObject classLoader, Klass hostKlass) {
        Symbol<Type> superKlassType = parserKlass.getSuperKlass();
        ClassRegistries classRegistry = context.getRegistries();

        // TODO(garcia): Superclass must be a class, and non-final.
        ObjectKlass superKlass = superKlassType != null
                        ? (ObjectKlass) classRegistry.loadKlass(superKlassType, classLoader)
                        : null;

        assert superKlass == null || !superKlass.isInterface();

        final Symbol<Type>[] superInterfacesTypes = parserKlass.getSuperInterfaces();

        LinkedKlass[] linkedInterfaces = superInterfacesTypes.length == 0
                        ? LinkedKlass.EMPTY_ARRAY
                        : new LinkedKlass[superInterfacesTypes.length];

        ObjectKlass[] superInterfaces = superInterfacesTypes.length == 0
                        ? ObjectKlass.EMPTY_ARRAY
                        : new ObjectKlass[superInterfacesTypes.length];

        // TODO(garcia): Superinterfaces must be interfaces.
        for (int i = 0; i < superInterfacesTypes.length; ++i) {
            ObjectKlass interf = (ObjectKlass) classRegistry.loadKlass(superInterfacesTypes[i], classLoader);
            superInterfaces[i] = interf;
            linkedInterfaces[i] = interf.getLinkedKlass();
        }

        LinkedKlass linkedKlass = new LinkedKlass(parserKlass, superKlass == null ? null : superKlass.getLinkedKlass(), linkedInterfaces);

        ObjectKlass klass = new ObjectKlass(context, linkedKlass, superKlass, superInterfaces, classLoader, hostKlass);

        klass.getConstantPool().setKlassAt(parserKlass.getThisKlassIndex(), klass);

        return klass;
    }

    /**
     * Report the offset of the first element in the storage allocation of a given array class. If
     * {@link #arrayIndexScale} returns a non-zero value for the same class, you may use that scale
     * factor, together with this base offset, to form new offsets to access elements of arrays of
     * the given class.
     *
     * @see #getInt
     * @see #putInt
     */
    @Substitution(hasReceiver = true)
    public static int arrayBaseOffset(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Class.class) StaticObject clazz) {
        Klass klass = clazz.getMirrorKlass();
        assert klass.isArray();
        if (((ArrayKlass) klass).getComponentType().isPrimitive()) {
            Class<?> hostPrimitive = ((ArrayKlass) klass).getComponentType().getJavaKind().toJavaClass();
            return UNSAFE.arrayBaseOffset(Array.newInstance(hostPrimitive, 0).getClass());
        } else {
            // Just a reference type.
            return UNSAFE.arrayBaseOffset(Object[].class);
        }
    }

    /**
     * Report the scale factor for addressing elements in the storage allocation of a given array
     * class. However, arrays of "narrow" types will generally not work properly with accessors like
     * {@link #getByte}, so the scale factor for such classes is reported as zero.
     *
     * @see #arrayBaseOffset
     * @see #getInt
     * @see #putInt
     */
    @Substitution(hasReceiver = true)
    public static int arrayIndexScale(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Class.class) StaticObject clazz) {
        Klass klass = clazz.getMirrorKlass();
        assert klass.isArray();
        if (((ArrayKlass) klass).getComponentType().isPrimitive()) {
            Class<?> hostPrimitive = ((ArrayKlass) klass).getComponentType().getJavaKind().toJavaClass();
            return UNSAFE.arrayIndexScale(Array.newInstance(hostPrimitive, 0).getClass());
        } else {
            // Just a reference type.
            return UNSAFE.arrayIndexScale(Object[].class);
        }
    }

    /** The value of {@code addressSize()}. */
    public static final int ADDRESS_SIZE = UNSAFE.addressSize();

    /**
     * Report the size in bytes of a native pointer, as stored via {@link #putAddress}. This value
     * will be either 4 or 8. Note that the sizes of other primitive types (as stored in native
     * memory blocks) is determined fully by their information content.
     */
    @Substitution(hasReceiver = true)
    public static int addressSize(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self) {
        return ADDRESS_SIZE;
    }

    /**
     * Report the location of a given static field, in conjunction with {@link #staticFieldBase}.
     * <p>
     * Do not expect to perform any sort of arithmetic on this offset; it is just a cookie which is
     * passed to the unsafe heap memory accessors.
     *
     * <p>
     * Any given field will always have the same offset, and no two distinct fields of the same
     * class will ever have the same offset.
     *
     * <p>
     * As of 1.4.1, offsets for fields are represented as long values, although the Sun JVM does not
     * use the most significant 32 bits. It is hard to imagine a JVM technology which needs more
     * than a few bits to encode an offset within a non-array object, However, for consistency with
     * other methods in this class, this method reports its result as a long value.
     * 
     * @see #getInt
     */
    @Substitution(hasReceiver = true)
    public static long objectFieldOffset(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(java.lang.reflect.Field.class) StaticObject field,
                    @InjectMeta Meta meta) {
        Field target = Field.getReflectiveFieldRoot(field, meta);
        return SAFETY_FIELD_OFFSET + target.getSlot();
    }

    private static Field getInstanceFieldFromIndex(StaticObject holder, int slot) {
        if (!(0 <= slot && slot < (1 << 16))) {
            throw EspressoError.shouldNotReachHere("the field offset is not normalized");
        }
        if (holder.isStaticStorage()) {
            // Lookup static field in current class.
            return holder.getKlass().lookupStaticFieldTable(slot);
        } else {
            return holder.getKlass().lookupFieldTable(slot);
        }
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static @Host(Class.class) StaticObject defineClass(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(String.class) StaticObject name,
                    @Host(byte[].class) StaticObject guestBuf, int offset, int len, @Host(ClassLoader.class) StaticObject loader,
                    @Host(ProtectionDomain.class) StaticObject pd,
                    @InjectMeta Meta meta) {
        // TODO(peterssen): Protection domain is ignored.
        byte[] buf = guestBuf.unwrap();
        byte[] bytes = Arrays.copyOfRange(buf, offset, len);
        Klass klass = meta.getRegistries().defineKlass(meta.getTypes().fromClassGetName(Meta.toHostString(name)), bytes, loader);
        klass.mirror().setHiddenField(meta.HIDDEN_PROTECTION_DOMAIN, pd);
        return klass.mirror();
    }

    private static Object unwrapNullOrArray(StaticObject object) {
        assert isNullOrArray(object);
        if (StaticObject.isNull(object)) {
            return null;
        }
        return object.unwrap();
    }

    private static boolean isNullOrArray(StaticObject object) {
        return StaticObject.isNull(object) || object.isArray(); // order matters
    }

    // region compareAndSwap*

    // CAS ops should be atomic.
    @Substitution(hasReceiver = true)
    public static boolean compareAndSwapObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset,
                    Object before, Object after) {
        if (isNullOrArray(holder)) {
            return UNSAFE.compareAndSwapObject(unwrapNullOrArray(holder), offset, before, after);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, offset <-> field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.compareAndSwapField(f, before, after);
    }

    @Substitution(hasReceiver = true)
    public static boolean compareAndSwapInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, int before,
                    int after) {
        if (isNullOrArray(holder)) {
            return UNSAFE.compareAndSwapInt(unwrapNullOrArray(holder), offset, before, after);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.compareAndSwapIntField(f, before, after);
    }

    @Substitution(hasReceiver = true)
    public static boolean compareAndSwapLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long before,
                    long after) {
        if (isNullOrArray(holder)) {
            return UNSAFE.compareAndSwapLong(unwrapNullOrArray(holder), offset, before, after);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.compareAndSwapLongField(f, before, after);
    }

    // endregion compareAndSwap*

    @Substitution
    public static void registerNatives() {
        /* nop */
    }

    /**
     * Allocates a new block of native memory, of the given size in bytes. The contents of the
     * memory are uninitialized; they will generally be garbage. The resulting native pointer will
     * never be zero, and will be aligned for all value types. Dispose of this memory by calling
     * {@link #freeMemory}, or resize it with {@link #reallocateMemory}.
     *
     * @throws IllegalArgumentException if the size is negative or too large for the native size_t
     *             type
     *
     * @throws OutOfMemoryError if the allocation is refused by the system
     *
     * @see #getByte
     * @see #putByte
     */
    @Substitution(hasReceiver = true)
    public static long allocateMemory(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long length) {
        return UNSAFE.allocateMemory(length);
    }

    /**
     * Disposes of a block of native memory, as obtained from {@link #allocateMemory} or
     * {@link #reallocateMemory}. The address passed to this method may be null, in which case no
     * action is taken.
     *
     * @see #allocateMemory
     */
    @Substitution(hasReceiver = true)
    public static void freeMemory(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long address) {
        UNSAFE.freeMemory(address);
    }

    // region get*(Object holder, long offset)

    /**
     * Fetches a value from a given memory address. If the address is zero, or does not point into a
     * block obtained from {@link #allocateMemory}, the results are undefined.
     *
     * @see #allocateMemory
     */
    @Substitution(hasReceiver = true)
    public static byte getByte(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getByte(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (byte) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static @Host(Object.class) StaticObject getObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return (StaticObject) UNSAFE.getObject(unwrapNullOrArray(holder), offset);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (StaticObject) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static boolean getBoolean(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getBoolean(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (boolean) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static char getChar(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getChar(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (char) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static short getShort(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getShort(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (short) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static int getInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getInt(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (int) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static float getFloat(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getFloat(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (float) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static double getDouble(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getDouble(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (double) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static long getLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getLong(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (long) f.get(holder);
    }

    // endregion get*(Object holder, long offset)

    // region get*Volatile(Object holder, long offset)

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static boolean getBooleanVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getBooleanVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getByteFieldVolatile(f) != 0;
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static byte getByteVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getByteVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getByteFieldVolatile(f);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static short getShortVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getShortVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getShortFieldVolatile(f);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static char getCharVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getCharVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getCharFieldVolatile(f);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static float getFloatVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getFloatVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getFloatFieldVolatile(f);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static int getIntVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject unsafe, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getIntVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getIntFieldVolatile(f);
    }

    @Substitution(hasReceiver = true)
    public static long getLongVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject unsafe, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getLongVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getLongFieldVolatile(f);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static double getDoubleVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getDoubleVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getDoubleField(f);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static Object getObjectVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (isNullOrArray(holder)) {
            return UNSAFE.getObjectVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getFieldVolatile(f);
    }

    // endregion get*Volatile(Object holder, long offset)

    // region get*(long offset)

    @Substitution(hasReceiver = true)
    public static byte getByte(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return UNSAFE.getByte(offset);
    }

    @Substitution(hasReceiver = true)
    public static char getChar(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return UNSAFE.getChar(offset);
    }

    @Substitution(hasReceiver = true)
    public static short getShort(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return UNSAFE.getShort(offset);
    }

    @Substitution(hasReceiver = true)
    public static int getInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return UNSAFE.getInt(offset);
    }

    @Substitution(hasReceiver = true)
    public static float getFloat(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return UNSAFE.getFloat(offset);
    }

    @Substitution(hasReceiver = true)
    public static long getLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return UNSAFE.getLong(offset);
    }

    @Substitution(hasReceiver = true)
    public static double getDouble(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return UNSAFE.getDouble(offset);
    }

    // endregion get*(long offset)

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putObjectVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, Object value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putObjectVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert !f.getKind().isSubWord();
        holder.setFieldVolatile(f, value);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putIntVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, int value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putIntVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setIntFieldVolatile(f, value);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putLongVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putLongVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().needsTwoSlots();
        holder.setLongFieldVolatile(f, value);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putBooleanVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, boolean value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putBooleanVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setBooleanFieldVolatile(f, value);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putCharVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, char value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putCharVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setCharFieldVolatile(f, value);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putShortVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, short value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putShortVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setShortFieldVolatile(f, value);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putFloatVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, float value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putFloatVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setFloatFieldVolatile(f, value);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putDoubleVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, double value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putDoubleVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().needsTwoSlots();
        holder.setDoubleFieldVolatile(f, value);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putByteVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, byte value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putByteVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setByteFieldVolatile(f, value);
    }

    @Substitution(hasReceiver = true)
    public static boolean shouldBeInitialized(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Class.class) StaticObject clazz,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(clazz)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        Klass klass = clazz.getMirrorKlass();
        return !klass.isInitialized();
    }

    /**
     * Ensure the given class has been initialized. This is often needed in conjunction with
     * obtaining the static field base of a class.
     */
    @Substitution(hasReceiver = true)
    public static void ensureClassInitialized(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Class.class) StaticObject clazz) {
        clazz.getMirrorKlass().safeInitialize();
    }

    @Substitution(hasReceiver = true)
    public static void copyMemory(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject srcBase, long srcOffset,
                    @Host(Object.class) StaticObject destBase,
                    long destOffset,
                    long bytes) {
        if (bytes == 0) {
            return;
        }
        UNSAFE.copyMemory(MetaUtil.unwrapArrayOrNull(srcBase), srcOffset, MetaUtil.unwrapArrayOrNull(destBase), destOffset, bytes);
    }

    // region put*(long offset, * value)

    /**
     * Stores a value into a given memory address. If the address is zero, or does not point into a
     * block obtained from {@link #allocateMemory}, the results are undefined.
     *
     * @see #getByte
     */
    @Substitution(hasReceiver = true)
    public static void putByte(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, byte value) {
        UNSAFE.putByte(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putChar(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, char value) {
        UNSAFE.putChar(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putShort(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, short value) {
        UNSAFE.putShort(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, int value) {
        UNSAFE.putInt(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putFloat(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, float value) {
        UNSAFE.putFloat(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putDouble(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, double value) {
        UNSAFE.putDouble(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, long x) {
        UNSAFE.putLong(offset, x);
    }

    // endregion put*(long offset, * value)

    // region put*(Object holder, long offset, * value)

    @Substitution(hasReceiver = true)
    public static void putObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, Object value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putObject(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putBoolean(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, boolean value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putBoolean(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putByte(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, byte value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putByte(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putChar(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, char value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putChar(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putShort(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, short value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putShort(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, int value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putInt(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putFloat(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, float value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putFloat(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putDouble(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, double value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putDouble(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putLong(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putOrderedInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, int value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putOrderedInt(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        // TODO(peterssen): Volatile is stronger than needed.
        holder.setIntFieldVolatile(f, value);
    }

    @Substitution(hasReceiver = true)
    public static void putOrderedLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putOrderedLong(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        // TODO(peterssen): Volatile is stronger than needed.
        holder.setLongFieldVolatile(f, value);
    }

    @Substitution(hasReceiver = true)
    public static void putOrderedObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, Object value) {
        if (isNullOrArray(holder)) {
            UNSAFE.putOrderedObject(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        // TODO(peterssen): Volatile is stronger than needed.
        holder.setFieldVolatile(f, value);
    }

    // endregion put*(Object holder, long offset, * value)

    /**
     * Allocate an instance but do not run any constructor. Initializes the class if it has not yet
     * been.
     */
    @TruffleBoundary
    @Throws(InstantiationException.class)
    @Substitution(hasReceiver = true)
    public static @Host(Object.class) StaticObject allocateInstance(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Class.class) StaticObject clazz) {
        return InterpreterToVM.newObject(clazz.getMirrorKlass(), false);
    }

    /**
     * Sets all bytes in a given block of memory to a fixed value (usually zero).
     *
     * <p>
     * This method determines a block's base address by means of two parameters, and so it provides
     * (in effect) a <em>double-register</em> addressing mode, as discussed in {@link #getInt}. When
     * the object reference is null, the offset supplies an absolute base address.
     *
     * <p>
     * The stores are in coherent (atomic) units of a size determined by the address and length
     * parameters. If the effective address and length are all even modulo 8, the stores take place
     * in 'long' units. If the effective address and length are (resp.) even modulo 4 or 2, the
     * stores take place in units of 'int' or 'short'.
     *
     * @since 1.7
     */
    @Substitution(hasReceiver = true)
    public static void setMemory(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject o, long offset, long bytes, byte value) {
        UNSAFE.setMemory(StaticObject.isNull(o) ? null : o, offset, bytes, value);
    }

    /**
     * Report the size in bytes of a native memory page (whatever that is). This value will always
     * be a power of two.
     */
    @Substitution(hasReceiver = true)
    public static int pageSize(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self) {
        return UNSAFE.pageSize();
    }

    /**
     * Report the location of a given field in the storage allocation of its class. Do not expect to
     * perform any sort of arithmetic on this offset; it is just a cookie which is passed to the
     * unsafe heap memory accessors.
     *
     * <p>
     * Any given field will always have the same offset and base, and no two distinct fields of the
     * same class will ever have the same offset and base.
     *
     * <p>
     * As of 1.4.1, offsets for fields are represented as long values, although the Sun JVM does not
     * use the most significant 32 bits. However, JVM implementations which store static fields at
     * absolute addresses can use long offsets and null base pointers to express the field locations
     * in a form usable by {@link #getInt}. Therefore, code which will be ported to such JVMs on
     * 64-bit platforms must preserve all bits of static field offsets.
     * 
     * @see #getInt
     */
    @Substitution(hasReceiver = true)
    public static long staticFieldOffset(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(java.lang.reflect.Field.class) StaticObject field,
                    @InjectMeta Meta meta) {
        return Field.getReflectiveFieldRoot(field, meta).getSlot() + SAFETY_FIELD_OFFSET;
    }

    /**
     * Report the location of a given static field, in conjunction with {@link #staticFieldOffset}.
     * <p>
     * Fetch the base "Object", if any, with which static fields of the given class can be accessed
     * via methods like {@link #getInt}. This value may be null. This value may refer to an object
     * which is a "cookie", not guaranteed to be a real Object, and it should not be used in any way
     * except as argument to the get and put routines in this class.
     */
    @Substitution(hasReceiver = true)
    public static Object staticFieldBase(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(java.lang.reflect.Field.class) StaticObject field,
                    @InjectMeta Meta meta) {
        Field target = Field.getReflectiveFieldRoot(field, meta);
        return target.getDeclaringKlass().getStatics();
    }

    @TruffleBoundary
    @SuppressWarnings("deprecation")
    @Substitution(hasReceiver = true)
    public static void monitorEnter(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject object,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(object)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        object.getLock().lock();
    }

    @TruffleBoundary
    @SuppressWarnings("deprecation")
    @Substitution(hasReceiver = true)
    public static void monitorExit(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject object,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(object)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        object.getLock().unlock();
    }

    @Substitution(hasReceiver = true)
    public static void throwException(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Throwable.class) StaticObject ee) {
        throw Meta.throwException(ee);
    }

    /**
     * Block current thread, returning when a balancing <tt>unpark</tt> occurs, or a balancing
     * <tt>unpark</tt> has already occurred, or the thread is interrupted, or, if not absolute and
     * time is not zero, the given time nanoseconds have elapsed, or if absolute, the given deadline
     * in milliseconds since Epoch has passed, or spuriously (i.e., returning for no "reason").
     * Note: This operation is in the Unsafe class only because <tt>unpark</tt> is, so it would be
     * strange to place it elsewhere.
     */
    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static void park(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, boolean isAbsolute, long time,
                    @InjectMeta Meta meta) {
        if (time < 0 || (isAbsolute && time == 0)) { // don't wait at all
            return;
        }

        EspressoContext context = meta.getContext();
        StaticObject thread = context.getCurrentThread();

        if (Target_java_lang_Thread.checkInterrupt(thread)) {
            return;
        }

        Target_java_lang_Thread.fromRunnable(thread, meta, time > 0 ? State.TIMED_WAITING : State.WAITING);
        Thread hostThread = Thread.currentThread();
        Object blocker = LockSupport.getBlocker(hostThread);
        Field parkBlocker = meta.java_lang_Thread.lookupDeclaredField(Symbol.Name.parkBlocker, Type.java_lang_Object);
        StaticObject guestBlocker = thread.getField(parkBlocker);
        // LockSupport.park(/* guest blocker */);
        if (!StaticObject.isNull(guestBlocker)) {
            UNSAFE.putObject(hostThread, parkBlockerOffset, guestBlocker);
        }
        UNSAFE.park(isAbsolute, time);
        Target_java_lang_Thread.toRunnable(thread, meta, State.RUNNABLE);
        UNSAFE.putObject(hostThread, parkBlockerOffset, blocker);
    }

    private static final long parkBlockerOffset;

    /**
     * Unblock the given thread blocked on <tt>park</tt>, or, if it is not blocked, cause the
     * subsequent call to <tt>park</tt> not to block. Note: this operation is "unsafe" solely
     * because the caller must somehow ensure that the thread has not been destroyed. Nothing
     * special is usually required to ensure this when called from Java (in which there will
     * ordinarily be a live reference to the thread) but this is not nearly-automatically so when
     * calling from native code.
     * 
     * @param thread the thread to unpark.
     *
     */
    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static void unpark(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject thread,
                    @InjectMeta Meta meta) {
        Thread hostThread = (Thread) thread.getHiddenField(meta.HIDDEN_HOST_THREAD);
        UNSAFE.unpark(hostThread);
    }

    /**
     * Fetches a native pointer from a given memory address. If the address is zero, or does not
     * point into a block obtained from {@link #allocateMemory}, the results are undefined.
     *
     * <p>
     * If the native pointer is less than 64 bits wide, it is extended as an unsigned number to a
     * Java long. The pointer may be indexed by any given byte offset, simply by adding that offset
     * (as a simple integer) to the long representing the pointer. The number of bytes actually read
     * from the target address maybe determined by consulting {@link #addressSize}.
     *
     * @see #allocateMemory
     */
    @Substitution(hasReceiver = true)
    public static long getAddress(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long address) {
        return UNSAFE.getAddress(address);
    }

    /**
     * Stores a native pointer into a given memory address. If the address is zero, or does not
     * point into a block obtained from {@link #allocateMemory}, the results are undefined.
     *
     * <p>
     * The number of bytes actually written at the target address maybe determined by consulting
     * {@link #addressSize}.
     *
     * @see #getAddress
     */
    @Substitution(hasReceiver = true)
    public static void putAddress(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long address, long x) {
        UNSAFE.putAddress(address, x);
    }

    /**
     * Ensures lack of reordering of loads before the fence with loads or stores after the fence.
     * 
     * @since 1.8
     */
    @Substitution(hasReceiver = true)
    public static void loadFence(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self) {
        UNSAFE.loadFence();
    }

    /**
     * Ensures lack of reordering of stores before the fence with loads or stores after the fence.
     * 
     * @since 1.8
     */
    @Substitution(hasReceiver = true)
    public static void storeFence(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self) {
        UNSAFE.storeFence();
    }

    /**
     * Ensures lack of reordering of loads or stores before the fence with loads or stores after the
     * fence.
     * 
     * @since 1.8
     */
    @Substitution(hasReceiver = true)
    public static void fullFence(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self) {
        UNSAFE.fullFence();
    }

    /**
     * Resizes a new block of native memory, to the given size in bytes. The contents of the new
     * block past the size of the old block are uninitialized; they will generally be garbage. The
     * resulting native pointer will be zero if and only if the requested size is zero. The
     * resulting native pointer will be aligned for all value types. Dispose of this memory by
     * calling {@link #freeMemory}, or resize it with {@link #reallocateMemory}. The address passed
     * to this method may be null, in which case an allocation will be performed.
     *
     * @throws IllegalArgumentException if the size is negative or too large for the native size_t
     *             type
     *
     * @throws OutOfMemoryError if the allocation is refused by the system
     *
     * @see #allocateMemory
     */
    @Substitution(hasReceiver = true)
    public static long reallocateMemory(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long address, long bytes) {
        return UNSAFE.reallocateMemory(address, bytes);
    }

    @Substitution(hasReceiver = true)
    public static @Host(Object.class) StaticObject getAndSetObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset,
                    @Host(Object.class) StaticObject value) {
        if (isNullOrArray(holder)) {
            return (StaticObject) UNSAFE.getAndSetObject(unwrapNullOrArray(holder), offset, value);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAndSetObject(holder, value);
    }

    @TruffleBoundary
    @SuppressWarnings("deprecation")
    @Substitution(hasReceiver = true)
    public static boolean tryMonitorEnter(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject object,
                    @InjectMeta Meta meta) {
        if (StaticObject.isNull(object)) {
            throw meta.throwNullPointerException();
        }
        return object.getLock().tryLock();
    }
}
