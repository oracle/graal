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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.descriptors.Symbol;
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
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

import sun.misc.Unsafe;

@EspressoSubstitutions
public final class Target_sun_misc_Unsafe {

    public static final int SAFETY_FIELD_OFFSET = 123456789;

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
    public static @Host(Class.class) StaticObject defineAnonymousClass(
                    @Host(Unsafe.class) StaticObject self,
                    @Host(Class.class) StaticObject hostClass,
                    @Host(typeName = "[B") StaticObject data,
                    @Host(typeName = "[Ljava/lang/Object;") StaticObject constantPoolPatches) {

        EspressoContext context = self.getKlass().getContext();
        Meta meta = context.getMeta();

        if (StaticObject.isNull(hostClass) || StaticObject.isNull(data)) {
            throw meta.throwEx(meta.IllegalArgumentException);
        }

        byte[] bytes = data.unwrap();
        StaticObject[] patches = StaticObject.isNull(constantPoolPatches) ? null : constantPoolPatches.unwrap();
        Klass hostKlass = hostClass.getMirrorKlass();
        ClassfileStream cfs = new ClassfileStream(bytes, null);
        ClassfileParser parser = new ClassfileParser(cfs, null, hostKlass, context, patches);
        ParserKlass parserKlass = parser.parseClass();
        StaticObject classLoader = hostKlass.getDefiningClassLoader();
        return defineAnonymousKlass(parserKlass, context, classLoader, parser.getThisKlassIndex(), hostKlass).mirror();
    }

    private static ObjectKlass defineAnonymousKlass(ParserKlass parserKlass, EspressoContext context, StaticObject classLoader, int thisKlassIndex, Klass hostKlass) {

        Symbol<Symbol.Type> superKlassType = parserKlass.getSuperKlass();
        ClassRegistries classRegistry = context.getRegistries();

        // TODO(garcia): Superclass must be a class, and non-final.
        ObjectKlass superKlass = superKlassType != null
                        ? (ObjectKlass) classRegistry.loadKlass(superKlassType, classLoader)
                        : null;

        assert superKlass == null || !superKlass.isInterface();

        final Symbol<Symbol.Type>[] superInterfacesTypes = parserKlass.getSuperInterfaces();

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
        klass.getConstantPool().setKlassAt(thisKlassIndex, klass);

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
        assert clazz.getMirrorKlass().isArray();
        if (clazz.getMirrorKlass().getComponentType().isPrimitive()) {
            Class<?> hostPrimitive = clazz.getMirrorKlass().getComponentType().getJavaKind().toJavaClass();
            return U.arrayBaseOffset(Array.newInstance(hostPrimitive, 0).getClass());
        } else {
            // Just a reference type.
            return U.arrayBaseOffset(Object[].class);
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
        assert clazz.getMirrorKlass().isArray();
        if (clazz.getMirrorKlass().getComponentType().isPrimitive()) {
            Class<?> hostPrimitive = clazz.getMirrorKlass().getComponentType().getJavaKind().toJavaClass();
            return U.arrayIndexScale(Array.newInstance(hostPrimitive, 0).getClass());
        } else {
            // Just a reference type.
            return U.arrayIndexScale(Object[].class);
        }
    }

    /** The value of {@code addressSize()} */
    public static final int ADDRESS_SIZE = U.addressSize();

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
    public static long objectFieldOffset(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(java.lang.reflect.Field.class) StaticObject field) {
        Field target = Field.getReflectiveFieldRoot(field);
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

    @Substitution(hasReceiver = true)
    public static @Host(Class.class) StaticObject defineClass(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(String.class) StaticObject name,
                    @Host(byte[].class) StaticObject guestBuf, int offset, int len, @Host(ClassLoader.class) StaticObject loader,
                    @SuppressWarnings("unused") @Host(ProtectionDomain.class) StaticObject pd) {
        // TODO(peterssen): Protection domain is ignored.
        byte[] buf = guestBuf.unwrap();
        byte[] bytes = Arrays.copyOfRange(buf, offset, len);
        return EspressoLanguage.getCurrentContext().getRegistries().defineKlass(self.getKlass().getTypes().fromClassGetName(Meta.toHostString(name)), bytes, loader).mirror();
    }

    // region compareAndSwap*

    // CAS ops should be atomic.
    @Substitution(hasReceiver = true)
    public static boolean compareAndSwapObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset,
                    Object before, Object after) {
        if (holder.isArray()) {
            return U.compareAndSwapObject((holder).unwrap(), offset, before, after);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, offset <-> field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.compareAndSwapField(f, before, after);
    }

    @Substitution(hasReceiver = true)
    public static boolean compareAndSwapInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, int before,
                    int after) {
        if (holder.isArray()) {
            return U.compareAndSwapInt((holder).unwrap(), offset, before, after);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.compareAndSwapIntField(f, before, after);
    }

    @Substitution(hasReceiver = true)
    public static boolean compareAndSwapLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long before,
                    long after) {
        if (holder.isArray()) {
            return U.compareAndSwapLong((holder).unwrap(), offset, before, after);
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
        return U.allocateMemory(length);
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
        U.freeMemory(address);
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
        if (holder.isArray()) {
            return U.getByte((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (byte) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static @Host(Object.class) StaticObject getObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return (StaticObject) U.getObject((holder).unwrap(), offset);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (StaticObject) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static boolean getBoolean(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getBoolean((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (boolean) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static char getChar(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getChar((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (char) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static short getShort(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getShort((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (short) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static int getInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getInt((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (int) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static float getFloat(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getFloat((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (float) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static double getDouble(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getDouble((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (double) f.get(holder);
    }

    @Substitution(hasReceiver = true)
    public static long getLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getLong((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return (long) f.get(holder);
    }

    // endregion get*(Object holder, long offset)

    // region get*Volatile(Object holder, long offset)

    @Substitution(hasReceiver = true)
    public static boolean getBooleanVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getBooleanVolatile((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getByteFieldVolatile(f) != 0;
    }

    @Substitution(hasReceiver = true)
    public static byte getByteVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getByteVolatile((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getByteFieldVolatile(f);
    }

    @Substitution(hasReceiver = true)
    public static short getShortVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getShortVolatile((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getShortFieldVolatile(f);
    }

    @Substitution(hasReceiver = true)
    public static char getCharVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getCharVolatile((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getCharFieldVolatile(f);
    }

    @Substitution(hasReceiver = true)
    public static float getFloatVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getFloatVolatile((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getFloatFieldVolatile(f);
    }

    @Substitution(hasReceiver = true)
    public static int getIntVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject unsafe, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getIntVolatile((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getIntFieldVolatile(f);
    }

    @Substitution(hasReceiver = true)
    public static long getLongVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject unsafe, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getLongVolatile((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getLongFieldVolatile(f);
    }

    @Substitution(hasReceiver = true)
    public static double getDoubleVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getDoubleVolatile((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getDoubleField(f);
    }

    @Substitution(hasReceiver = true)
    public static Object getObjectVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset) {
        if (holder.isArray()) {
            return U.getObjectVolatile((holder).unwrap(), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return holder.getFieldVolatile(f);
    }

    // endregion get*Volatile(Object holder, long offset)

    // region get*(long offset)

    @Substitution(hasReceiver = true)
    public static byte getByte(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return U.getByte(offset);
    }

    @Substitution(hasReceiver = true)
    public static char getChar(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return U.getChar(offset);
    }

    @Substitution(hasReceiver = true)
    public static short getShort(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return U.getShort(offset);
    }

    @Substitution(hasReceiver = true)
    public static int getInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return U.getInt(offset);
    }

    @Substitution(hasReceiver = true)
    public static float getFloat(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return U.getFloat(offset);
    }

    @Substitution(hasReceiver = true)
    public static long getLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return U.getLong(offset);
    }

    @Substitution(hasReceiver = true)
    public static double getDouble(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset) {
        return U.getDouble(offset);
    }

    // endregion get*(long offset)

    @Substitution(hasReceiver = true)
    public static void putObjectVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, Object value) {
        if (holder.isArray()) {
            U.putObjectVolatile((holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert !f.getKind().isSubWord();
        holder.setFieldVolatile(f, value);
    }

    @Substitution(hasReceiver = true)
    public static void putIntVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, int value) {
        if (holder.isArray()) {
            U.putIntVolatile((holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setIntFieldVolatile(f, value);
    }

    @Substitution(hasReceiver = true)
    public static void putLongVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long value) {
        if (holder.isArray()) {
            U.putLongVolatile((holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setLongFieldVolatile(f, value);
    }

    @Substitution(hasReceiver = true)
    public static void putBooleanVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, boolean value) {
        if (holder.isArray()) {
            U.putBooleanVolatile((holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setBooleanFieldVolatile(f, value);
    }

    @Substitution(hasReceiver = true)
    public static void putCharVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, char value) {
        if (holder.isArray()) {
            U.putCharVolatile((holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setCharFieldVolatile(f, value);
    }

    @Substitution(hasReceiver = true)
    public static void putShortVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, short value) {
        if (holder.isArray()) {
            U.putShortVolatile((holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setShortFieldVolatile(f, value);
    }

    @Substitution(hasReceiver = true)
    public static void putFloatVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, float value) {
        if (holder.isArray()) {
            U.putFloatVolatile((holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setFloatFieldVolatile(f, value);
    }

    @Substitution(hasReceiver = true)
    public static void putDoubleVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, double value) {
        if (holder.isArray()) {
            U.putDoubleVolatile((holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setDoubleFieldVolatile(f, value);
    }

    @Substitution(hasReceiver = true)
    public static void putByteVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, byte value) {
        if (holder.isArray()) {
            U.putByteVolatile((holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        holder.setByteFieldVolatile(f, value);
    }

    @Substitution(methodName = "shouldBeInitialized", hasReceiver = true)
    public static boolean shouldBeInit(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Class.class) StaticObject clazz) {
        Klass k = clazz.getMirrorKlass();
        return (k != null);
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
        U.copyMemory(MetaUtil.unwrapArrayOrNull(srcBase), srcOffset, MetaUtil.unwrapArrayOrNull(destBase), destOffset, bytes);
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
        U.putByte(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putChar(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, char value) {
        U.putChar(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putShort(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, short value) {
        U.putShort(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, int value) {
        U.putInt(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putFloat(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, float value) {
        U.putFloat(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putDouble(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, double value) {
        U.putDouble(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, long x) {
        U.putLong(offset, x);
    }

    // endregion put*(long offset, * value)

    // region put*(Object holder, long offset, * value)

    @Substitution(hasReceiver = true)
    public static void putObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, Object value) {
        if (holder.isArray()) {
            U.putObject((holder).unwrap(), offset, value);
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
        if (holder.isArray()) {
            U.putBoolean((holder).unwrap(), offset, value);
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
        if (holder.isArray()) {
            U.putByte((holder).unwrap(), offset, value);
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
        if (holder.isArray()) {
            U.putChar((holder).unwrap(), offset, value);
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
        if (holder.isArray()) {
            U.putShort((holder).unwrap(), offset, value);
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
        if (holder.isArray()) {
            U.putInt((holder).unwrap(), offset, value);
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
        if (holder.isArray()) {
            U.putFloat((holder).unwrap(), offset, value);
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
        if (holder.isArray()) {
            U.putDouble((holder).unwrap(), offset, value);
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
        if (holder.isArray()) {
            U.putLong((holder).unwrap(), offset, value);
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
        if (holder.isArray()) {
            U.putOrderedInt((holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putOrderedLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long value) {
        if (holder.isArray()) {
            U.putOrderedLong((holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.set(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putOrderedObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, Object value) {
        if (holder.isArray()) {
            U.putOrderedObject((holder).unwrap(), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.set(holder, value);
    }

    // endregion put*(Object holder, long offset, * value)

    /**
     * Allocate an instance but do not run any constructor. Initializes the class if it has not yet
     * been.
     */
    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static @Host(Object.class) StaticObject allocateInstance(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Class.class) StaticObject clazz) { // throws
        // InstantiationException;
        return InterpreterToVM.newObject(clazz.getMirrorKlass());
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
        U.setMemory(StaticObject.isNull(o) ? null : o, offset, bytes, value);
    }

    /**
     * Report the size in bytes of a native memory page (whatever that is). This value will always
     * be a power of two.
     */
    @Substitution(hasReceiver = true)
    public static int pageSize(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self) {
        return U.pageSize();
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
    public static long staticFieldOffset(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(java.lang.reflect.Field.class) StaticObject field) {
        return Field.getReflectiveFieldRoot(field).getSlot() + SAFETY_FIELD_OFFSET;
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
    public static Object staticFieldBase(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(java.lang.reflect.Field.class) StaticObject field) {
        Field target = Field.getReflectiveFieldRoot(field);
        return target.getDeclaringKlass().getStatics();
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
     * Block current thread, returning when a balancing <tt>unpark</tt> occurs, or a balancing
     * <tt>unpark</tt> has already occurred, or the thread is interrupted, or, if not absolute and
     * time is not zero, the given time nanoseconds have elapsed, or if absolute, the given deadline
     * in milliseconds since Epoch has passed, or spuriously (i.e., returning for no "reason").
     * Note: This operation is in the Unsafe class only because <tt>unpark</tt> is, so it would be
     * strange to place it elsewhere.
     */
    @Substitution(hasReceiver = true)
    public static void park(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, boolean isAbsolute, long time) {
        U.park(isAbsolute, time);
    }

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
    @Substitution(hasReceiver = true)
    public static void unpark(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject thread) {
        Thread hostThread = (Thread) thread.getHiddenField(self.getKlass().getMeta().HIDDEN_HOST_THREAD);
        U.unpark(hostThread);
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
        return U.getAddress(address);
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
        U.putAddress(address, x);
    }

    /**
     * Ensures lack of reordering of loads before the fence with loads or stores after the fence.
     * 
     * @since 1.8
     */
    @Substitution(hasReceiver = true)
    public static void loadFence(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self) {
        U.loadFence();
    }

    /**
     * Ensures lack of reordering of stores before the fence with loads or stores after the fence.
     * 
     * @since 1.8
     */
    @Substitution(hasReceiver = true)
    public static void storeFence(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self) {
        U.storeFence();
    }

    /**
     * Ensures lack of reordering of loads or stores before the fence with loads or stores after the
     * fence.
     * 
     * @since 1.8
     */
    @Substitution(hasReceiver = true)
    public static void fullFence(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self) {
        U.fullFence();
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
        return U.reallocateMemory(address, bytes);
    }
}
