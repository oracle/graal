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
import java.nio.ByteOrder;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.ffi.Buffer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.LinkedKlass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.ParserKlass;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread.State;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

@EspressoSubstitutions(nameProvider = Target_sun_misc_Unsafe.SharedUnsafe.class)
public final class Target_sun_misc_Unsafe {

    /** The value of {@code addressSize()}. */
    public static final int ADDRESS_SIZE;
    static final int SAFETY_FIELD_OFFSET = 123456789;
    private static final long PARK_BLOCKER_OFFSET;
    private static final String TARGET_JDK_INTERNAL_MISC_UNSAFE = "Target_jdk_internal_misc_Unsafe";
    private static final String TARGET_SUN_MISC_UNSAFE = "Target_sun_misc_Unsafe";

    static {
        Unsafe unsafe = UnsafeAccess.get();
        try {
            PARK_BLOCKER_OFFSET = unsafe.objectFieldOffset(Thread.class.getDeclaredField("parkBlocker"));
        } catch (NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        ADDRESS_SIZE = unsafe.addressSize();
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    @SuppressWarnings("unused")
    public static @Host(Class.class) StaticObject defineAnonymousClass(
                    @Host(Unsafe.class) StaticObject self,
                    @Host(Class.class) StaticObject hostClass,
                    @Host(byte[].class) StaticObject data,
                    @Host(Object[].class) StaticObject constantPoolPatches,
                    @InjectMeta Meta meta) {
        EspressoContext context = meta.getContext();

        if (StaticObject.isNull(hostClass) || StaticObject.isNull(data)) {
            throw meta.throwNullPointerException();
        }
        if (hostClass.getMirrorKlass().isArray() || hostClass.getMirrorKlass().isPrimitive()) {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }

        byte[] bytes = data.unwrap();
        StaticObject[] patches = StaticObject.isNull(constantPoolPatches) ? null : constantPoolPatches.unwrap();
        Klass hostKlass = hostClass.getMirrorKlass();
        ClassfileStream cfs = new ClassfileStream(bytes, null);
        StaticObject classLoader = hostKlass.getDefiningClassLoader();
        ParserKlass parserKlass = ClassfileParser.parse(cfs, classLoader, null, context, patches);

        // Inherit host class's protection domain.
        StaticObject clazz = defineAnonymousKlass(parserKlass, context, classLoader, hostKlass).mirror();
        StaticObject pd = (StaticObject) meta.HIDDEN_PROTECTION_DOMAIN.getHiddenObject(hostClass);
        if (pd == null) {
            pd = StaticObject.NULL;
        }
        meta.HIDDEN_PROTECTION_DOMAIN.setHiddenObject(clazz, pd);

        return clazz;
    }

    private static ObjectKlass defineAnonymousKlass(ParserKlass parserKlass, EspressoContext context, StaticObject classLoader, Klass hostKlass) {
        Symbol<Type> superKlassType = parserKlass.getSuperKlass();

        // TODO(garcia): Superclass must be a class, and non-final.
        ObjectKlass superKlass = superKlassType != null
                        ? (ObjectKlass) context.getMeta().loadKlassOrFail(superKlassType, classLoader, StaticObject.NULL)
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
            ObjectKlass interf = (ObjectKlass) context.getMeta().loadKlassOrFail(superInterfacesTypes[i], classLoader, StaticObject.NULL);
            superInterfaces[i] = interf;
            linkedInterfaces[i] = interf.getLinkedKlass();
        }

        LinkedKlass linkedSuperKlass = superKlass == null ? null : superKlass.getLinkedKlass();
        LinkedKlass linkedKlass = context.getCache().getOrCreateLinkedKlass(context.getLanguage(), parserKlass, linkedSuperKlass, linkedInterfaces);

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
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static int arrayBaseOffset(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Class.class) StaticObject clazz, @InjectMeta Meta meta) {
        Unsafe unsafe = UnsafeAccess.getIfAllowed(meta);
        Klass klass = clazz.getMirrorKlass();
        assert klass.isArray();
        if (((ArrayKlass) klass).getComponentType().isPrimitive()) {
            Class<?> hostPrimitive = ((ArrayKlass) klass).getComponentType().getJavaKind().toJavaClass();
            return unsafe.arrayBaseOffset(Array.newInstance(hostPrimitive, 0).getClass());
        } else {
            // Just a reference type.
            return unsafe.arrayBaseOffset(Object[].class);
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
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static int arrayIndexScale(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Class.class) StaticObject clazz, @InjectMeta Meta meta) {
        Unsafe unsafe = UnsafeAccess.getIfAllowed(meta);
        Klass klass = clazz.getMirrorKlass();
        assert klass.isArray();
        if (((ArrayKlass) klass).getComponentType().isPrimitive()) {
            Class<?> hostPrimitive = ((ArrayKlass) klass).getComponentType().getJavaKind().toJavaClass();
            return unsafe.arrayIndexScale(Array.newInstance(hostPrimitive, 0).getClass());
        } else {
            // Just a reference type.
            return unsafe.arrayIndexScale(Object[].class);
        }
    }

    /**
     * Report the size in bytes of a native pointer, as stored via {@link #putAddress}. This value
     * will be either 4 or 8. Note that the sizes of other primitive types (as stored in native
     * memory blocks) is determined fully by their information content.
     */
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
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
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
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
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static @Host(Class.class) StaticObject defineClass(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(String.class) StaticObject name,
                    @Host(byte[].class) StaticObject guestBuf, int offset, int len, @Host(ClassLoader.class) StaticObject loader,
                    @Host(ProtectionDomain.class) StaticObject pd,
                    @InjectMeta Meta meta) {
        // TODO(peterssen): Protection domain is ignored.
        byte[] buf = guestBuf.unwrap();
        byte[] bytes = Arrays.copyOfRange(buf, offset, len);
        Klass klass = meta.getRegistries().defineKlass(meta.getTypes().fromClassGetName(meta.toHostString(name)), bytes, loader);
        meta.HIDDEN_PROTECTION_DOMAIN.setHiddenObject(klass.mirror(), pd);
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
    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    public static boolean compareAndSwapObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset,
                    @Host(Object.class) StaticObject before, @Host(Object.class) StaticObject after, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).compareAndSwapObject(unwrapNullOrArray(holder), offset, before, after);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, offset <-> field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.compareAndSwapObject(holder, before, after);
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    public static boolean compareAndSwapInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, int before,
                    int after, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).compareAndSwapInt(unwrapNullOrArray(holder), offset, before, after);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.compareAndSwapInt(holder, before, after);
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    public static boolean compareAndSwapLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long before,
                    long after, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).compareAndSwapLong(unwrapNullOrArray(holder), offset, before, after);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.compareAndSwapLong(holder, before, after);
    }

    // endregion compareAndSwap*

    // region compareAndExchange*

    /*
     * Java 8 does not have these instrctions in Unsafe. Implement them by ourselves.
     */

    private static StaticObject doStaticObjectCompareExchange(StaticObject holder, Field f, StaticObject before, StaticObject after) {
        StaticObject result;
        do {
            result = f.getObject(holder, true);
            if (result != before) {
                return result;
            }
        } while (!f.compareAndSwapObject(holder, before, after));
        return before;
    }

    private static StaticObject doCompareExchange(Meta meta, Object holder, long offset, StaticObject before, StaticObject after) {
        Unsafe unsafe = UnsafeAccess.getIfAllowed(meta);
        Object result;
        do {
            result = unsafe.getObjectVolatile(holder, offset);
            if (result != before) {
                return (StaticObject) result;
            }
        } while (!unsafe.compareAndSwapObject(holder, offset, before, after));
        return before;
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    public static @Host(Object.class) StaticObject compareAndExchangeObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset,
                    @Host(Object.class) StaticObject before, @Host(Object.class) StaticObject after, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return doCompareExchange(meta, unwrapNullOrArray(holder), offset, before, after);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, offset <-> field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return doStaticObjectCompareExchange(holder, f, before, after);
    }

    private static int doStaticObjectCompareExchangeInt(StaticObject holder, Field f, int before, int after) {
        int result;
        do {
            result = f.getInt(holder, true);
            if (result != before) {
                return result;
            }
        } while (!f.compareAndSwapInt(holder, before, after));
        return before;
    }

    private static int doCompareExchangeInt(Meta meta, Object holder, long offset, int before, int after) {
        Unsafe unsafe = UnsafeAccess.getIfAllowed(meta);
        int result;
        do {
            result = unsafe.getIntVolatile(holder, offset);
            if (result != before) {
                return result;
            }
        } while (!unsafe.compareAndSwapInt(holder, offset, before, after));
        return before;
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    public static int compareAndExchangeInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, int before,
                    int after, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return doCompareExchangeInt(meta, unwrapNullOrArray(holder), offset, before, after);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, offset <-> field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return doStaticObjectCompareExchangeInt(holder, f, before, after);
    }

    private static long doStaticObjectCompareExchangeLong(StaticObject holder, Field f, long before, long after) {
        long result;
        do {
            result = f.getLong(holder, true);
            if (result != before) {
                return result;
            }
        } while (!f.compareAndSwapLong(holder, before, after));
        return before;
    }

    private static long doCompareExchangeLong(Meta meta, Object holder, long offset, long before, long after) {
        Unsafe unsafe = UnsafeAccess.getIfAllowed(meta);
        long result;
        do {
            result = unsafe.getLongVolatile(holder, offset);
            if (result != before) {
                return result;
            }
        } while (!unsafe.compareAndSwapLong(holder, offset, before, after));
        return before;
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    public static long compareAndExchangeLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long before,
                    long after, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return doCompareExchangeLong(meta, unwrapNullOrArray(holder), offset, before, after);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return doStaticObjectCompareExchangeLong(holder, f, before, after);
    }

    // endregion compareAndExchange*

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
    @TruffleBoundary
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static long allocateMemory(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long length, @InjectMeta Meta meta) {
        JniEnv jni = meta.getContext().getJNI();
        if (length < 0 || length > jni.sizeMax()) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "requested size doesn't fit in the size_t native type");
        }
        @Buffer
        TruffleObject buffer = meta.getNativeAccess().allocateMemory(length);
        if (buffer == null && length > 0) {
            // malloc may return anything for 0-sized allocations.
            throw meta.throwExceptionWithMessage(meta.java_lang_OutOfMemoryError, "malloc returned NULL");
        }
        long ptr = 0;
        try {
            ptr = InteropLibrary.getUncached().asPointer(buffer);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere(e);
        }
        return ptr;
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
    @TruffleBoundary
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static long reallocateMemory(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long address, long newSize, @InjectMeta Meta meta) {
        JniEnv jni = meta.getContext().getJNI();
        if (newSize < 0 || newSize > jni.sizeMax()) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "requested size doesn't fit in the size_t native type");
        }
        @Buffer
        TruffleObject result = meta.getNativeAccess().reallocateMemory(RawPointer.create(address), newSize);
        if (result == null) {
            throw meta.throwExceptionWithMessage(meta.java_lang_OutOfMemoryError, "realloc couldn't reallocate " + newSize + " bytes");
        }
        long newAddress = 0L;
        try {
            newAddress = InteropLibrary.getUncached().asPointer(result);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere(e);
        }
        return newAddress;
    }

    /**
     * Disposes of a block of native memory, as obtained from {@link #allocateMemory} or
     * {@link #reallocateMemory}. The address passed to this method may be null, in which case no
     * action is taken.
     *
     * @see #allocateMemory
     */
    @TruffleBoundary
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static void freeMemory(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long address, @InjectMeta Meta meta) {
        meta.getNativeAccess().freeMemory(RawPointer.create(address));
    }

    // region get*(Object holder, long offset)

    /**
     * Fetches a value from a given memory address. If the address is zero, or does not point into a
     * block obtained from {@link #allocateMemory}, the results are undefined.
     *
     * @see #allocateMemory
     */
    @Substitution(hasReceiver = true)
    public static byte getByte(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getByte(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsByte(meta, holder, false);
    }

    @Substitution(hasReceiver = true)
    public static @Host(Object.class) StaticObject getObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset,
                    @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return (StaticObject) UnsafeAccess.getIfAllowed(meta).getObject(unwrapNullOrArray(holder), offset);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsObject(meta, holder);
    }

    @Substitution(hasReceiver = true)
    public static boolean getBoolean(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getBoolean(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsBoolean(meta, holder, false);
    }

    @Substitution(hasReceiver = true)
    public static char getChar(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getChar(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsChar(meta, holder, false);
    }

    @Substitution(hasReceiver = true)
    public static short getShort(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getShort(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsShort(meta, holder, false);
    }

    @Substitution(hasReceiver = true)
    public static int getInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getInt(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsInt(meta, holder, false);
    }

    @Substitution(hasReceiver = true)
    public static float getFloat(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getFloat(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsFloat(meta, holder, false);
    }

    @Substitution(hasReceiver = true)
    public static double getDouble(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getDouble(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsDouble(meta, holder, false);
    }

    @Substitution(hasReceiver = true)
    public static long getLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getLong(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsLong(meta, holder, false);
    }

    // endregion get*(Object holder, long offset)

    // region get*Volatile(Object holder, long offset)

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static boolean getBooleanVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getBooleanVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsBoolean(meta, holder, false, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static byte getByteVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getByteVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsByte(meta, holder, false, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static short getShortVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getShortVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsShort(meta, holder, false, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static char getCharVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getCharVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsChar(meta, holder, false, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static float getFloatVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getFloatVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsFloat(meta, holder, false, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static int getIntVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getIntVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsInt(meta, holder, false, true);
    }

    @Substitution(hasReceiver = true)
    public static long getLongVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getLongVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsLong(meta, holder, false, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static double getDoubleVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return UnsafeAccess.getIfAllowed(meta).getDoubleVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsDouble(meta, holder, false, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static @Host(Object.class) StaticObject getObjectVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset,
                    @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return (StaticObject) UnsafeAccess.getIfAllowed(meta).getObjectVolatile(unwrapNullOrArray(holder), offset);
        }
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAsObject(meta, holder, true);
    }

    // endregion get*Volatile(Object holder, long offset)

    // region get*(long offset)

    @Substitution(hasReceiver = true)
    public static byte getByte(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, @InjectMeta Meta meta) {
        return UnsafeAccess.getIfAllowed(meta).getByte(offset);
    }

    @Substitution(hasReceiver = true)
    public static char getChar(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, @InjectMeta Meta meta) {
        return UnsafeAccess.getIfAllowed(meta).getChar(offset);
    }

    @Substitution(hasReceiver = true)
    public static short getShort(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, @InjectMeta Meta meta) {
        return UnsafeAccess.getIfAllowed(meta).getShort(offset);
    }

    @Substitution(hasReceiver = true)
    public static int getInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, @InjectMeta Meta meta) {
        return UnsafeAccess.getIfAllowed(meta).getInt(offset);
    }

    @Substitution(hasReceiver = true)
    public static float getFloat(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, @InjectMeta Meta meta) {
        return UnsafeAccess.getIfAllowed(meta).getFloat(offset);
    }

    @Substitution(hasReceiver = true)
    public static long getLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, @InjectMeta Meta meta) {
        return UnsafeAccess.getIfAllowed(meta).getLong(offset);
    }

    @Substitution(hasReceiver = true)
    public static double getDouble(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, @InjectMeta Meta meta) {
        return UnsafeAccess.getIfAllowed(meta).getDouble(offset);
    }
    // endregion get*(long offset)

    // region put*Volatile(Object holder, long offset)
    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putObjectVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset,
                    @Host(Object.class) StaticObject value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putObjectVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert !f.getKind().isSubWord();
        f.setObject(holder, value, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putIntVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, int value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putIntVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        f.setInt(holder, value, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putLongVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putLongVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().needsTwoSlots();
        f.setLong(holder, value, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putBooleanVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, boolean value,
                    @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putBooleanVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        f.setBoolean(holder, value, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putCharVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, char value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putCharVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        f.setChar(holder, value, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putShortVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, short value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putShortVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        f.setShort(holder, value, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putFloatVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, float value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putFloatVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        f.setFloat(holder, value, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putDoubleVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, double value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putDoubleVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().needsTwoSlots();
        f.setDouble(holder, value, true);
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void putByteVolatile(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, byte value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putByteVolatile(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        assert f.getKind().isSubWord();
        f.setByte(holder, value, true);
    }
    // endregion put*Volatile(Object holder, long offset)

    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
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
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static void ensureClassInitialized(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Class.class) StaticObject clazz) {
        clazz.getMirrorKlass().safeInitialize();
    }

    @Substitution(hasReceiver = true)
    public static void copyMemory(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject srcBase, long srcOffset,
                    @Host(Object.class) StaticObject destBase, long destOffset, long bytes, @InjectMeta Meta meta) {
        if (bytes == 0) {
            return;
        }
        UnsafeAccess.getIfAllowed(meta).copyMemory(MetaUtil.unwrapArrayOrNull(srcBase), srcOffset, MetaUtil.unwrapArrayOrNull(destBase), destOffset, bytes);
    }

    // region put*(long offset, * value)

    /**
     * Stores a value into a given memory address. If the address is zero, or does not point into a
     * block obtained from {@link #allocateMemory}, the results are undefined.
     *
     * @see #getByte
     */
    @Substitution(hasReceiver = true)
    public static void putByte(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, byte value, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).putByte(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putChar(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, char value, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).putChar(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putShort(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, short value, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).putShort(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, int value, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).putInt(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putFloat(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, float value, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).putFloat(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putDouble(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, double value, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).putDouble(offset, value);
    }

    @Substitution(hasReceiver = true)
    public static void putLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long offset, long x, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).putLong(offset, x);
    }

    // endregion put*(long offset, * value)

    // region put*(Object holder, long offset, * value)

    @Substitution(hasReceiver = true)
    public static void putObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, @Host(Object.class) StaticObject value,
                    @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putObject(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.setObject(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putBoolean(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, boolean value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putBoolean(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.setBoolean(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putByte(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, byte value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putByte(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.setByte(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putChar(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, char value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putChar(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.setChar(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putShort(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, short value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putShort(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.setShort(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, int value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putInt(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.setInt(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putFloat(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, float value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putFloat(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.setFloat(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putDouble(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, double value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putDouble(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.setDouble(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putLong(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        f.setLong(holder, value);
    }

    @Substitution(hasReceiver = true)
    public static void putOrderedInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, int value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putOrderedInt(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        // TODO(peterssen): Volatile is stronger than needed.
        f.setInt(holder, value, true);
    }

    @Substitution(hasReceiver = true)
    public static void putOrderedLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putOrderedLong(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        // TODO(peterssen): Volatile is stronger than needed.
        f.setLong(holder, value, true);
    }

    @Substitution(hasReceiver = true)
    public static void putOrderedObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset,
                    @Host(Object.class) StaticObject value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            UnsafeAccess.getIfAllowed(meta).putOrderedObject(unwrapNullOrArray(holder), offset, value);
            return;
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        // TODO(peterssen): Volatile is stronger than needed.
        f.setObject(holder, value, true);
    }

    // endregion put*(Object holder, long offset, * value)

    /**
     * Allocate an instance but do not run any constructor. Initializes the class if it has not yet
     * been.
     */
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
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static void setMemory(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject o, long offset, long bytes, byte value, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).setMemory(StaticObject.isNull(o) ? null : o, offset, bytes, value);
    }

    /**
     * Report the size in bytes of a native memory page (whatever that is). This value will always
     * be a power of two.
     */
    @Substitution(hasReceiver = true)
    public static int pageSize(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @InjectMeta Meta meta) {
        return UnsafeAccess.getIfAllowed(meta).pageSize();
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
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
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
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static Object staticFieldBase(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(java.lang.reflect.Field.class) StaticObject field,
                    @InjectMeta Meta meta) {
        Field target = Field.getReflectiveFieldRoot(field, meta);
        return target.getDeclaringKlass().getStatics();
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    public static void monitorEnter(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject object,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(object)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        InterpreterToVM.monitorUnsafeEnter(object.getLock());
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    public static void monitorExit(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject object,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(object)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        InterpreterToVM.monitorUnsafeExit(object.getLock());
    }

    @Substitution(hasReceiver = true)
    public static void throwException(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Throwable.class) StaticObject ee, @InjectMeta Meta meta) {
        throw meta.throwException(ee);
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
    public static void park(@Host(Unsafe.class) StaticObject self, boolean isAbsolute, long time,
                    @InjectMeta Meta meta) {
        if (time < 0 || (isAbsolute && time == 0)) { // don't wait at all
            return;
        }

        EspressoContext context = meta.getContext();
        StaticObject thread = context.getCurrentThread();

        if (Target_java_lang_Thread.checkInterrupt(thread)) {
            return;
        }

        Unsafe unsafe = UnsafeAccess.getIfAllowed(meta);
        Target_java_lang_Thread.fromRunnable(thread, meta, time > 0 ? State.TIMED_WAITING : State.WAITING);
        Thread hostThread = Thread.currentThread();
        Object blocker = LockSupport.getBlocker(hostThread);
        Field parkBlocker = meta.java_lang_Thread.lookupDeclaredField(Symbol.Name.parkBlocker, Type.java_lang_Object);
        StaticObject guestBlocker = parkBlocker.getObject(thread);
        // LockSupport.park(/* guest blocker */);
        if (!StaticObject.isNull(guestBlocker)) {
            unsafe.putObject(hostThread, PARK_BLOCKER_OFFSET, guestBlocker);
        }

        parkBoundary(self, isAbsolute, time, meta);

        Target_java_lang_Thread.toRunnable(thread, meta, State.RUNNABLE);
        unsafe.putObject(hostThread, PARK_BLOCKER_OFFSET, blocker);
    }

    @TruffleBoundary(allowInlining = true)
    public static void parkBoundary(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, boolean isAbsolute, long time, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).park(isAbsolute, time);
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
    @TruffleBoundary(allowInlining = true)
    @Substitution(hasReceiver = true)
    public static void unpark(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject thread,
                    @InjectMeta Meta meta) {
        Thread hostThread = (Thread) meta.HIDDEN_HOST_THREAD.getHiddenObject(thread);
        UnsafeAccess.getIfAllowed(meta).unpark(hostThread);
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
    public static long getAddress(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long address, @InjectMeta Meta meta) {
        return UnsafeAccess.getIfAllowed(meta).getAddress(address);
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
    public static void putAddress(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, long address, long x, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).putAddress(address, x);
    }

    /**
     * Ensures lack of reordering of loads before the fence with loads or stores after the fence.
     * 
     * @since 1.8
     */
    @Substitution(hasReceiver = true)
    public static void loadFence(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).loadFence();
    }

    /**
     * Ensures lack of reordering of stores before the fence with loads or stores after the fence.
     * 
     * @since 1.8
     */
    @Substitution(hasReceiver = true)
    public static void storeFence(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).storeFence();
    }

    /**
     * Ensures lack of reordering of loads or stores before the fence with loads or stores after the
     * fence.
     * 
     * @since 1.8
     */
    @Substitution(hasReceiver = true)
    public static void fullFence(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @InjectMeta Meta meta) {
        UnsafeAccess.getIfAllowed(meta).fullFence();
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    public static @Host(Object.class) StaticObject getAndSetObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset,
                    @Host(Object.class) StaticObject value, @InjectMeta Meta meta) {
        if (isNullOrArray(holder)) {
            return (StaticObject) UnsafeAccess.getIfAllowed(meta).getAndSetObject(unwrapNullOrArray(holder), offset, value);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        Field f = getInstanceFieldFromIndex(holder, Math.toIntExact(offset) - SAFETY_FIELD_OFFSET);
        assert f != null;
        return f.getAndSetObject(holder, value);
    }

    @SuppressWarnings("deprecation")
    @Substitution(hasReceiver = true)
    public static boolean tryMonitorEnter(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject object,
                    @InjectMeta Meta meta) {
        if (StaticObject.isNull(object)) {
            throw meta.throwNullPointerException();
        }
        return InterpreterToVM.monitorTryLock(object.getLock());
    }

    /**
     * Gets the load average in the system run queue assigned to the available processors averaged
     * over various periods of time. This method retrieves the given <tt>nelem</tt> samples and
     * assigns to the elements of the given <tt>loadavg</tt> array. The system imposes a maximum of
     * 3 samples, representing averages over the last 1, 5, and 15 minutes, respectively.
     *
     * @params loadavg an array of double of size nelems
     * @params nelems the number of samples to be retrieved and must be 1 to 3.
     *
     * @return the number of samples actually retrieved; or -1 if the load average is unobtainable.
     */
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    @SuppressWarnings("unused")
    public static int getLoadAverage(@Host(Unsafe.class) StaticObject self, @Host(double[].class) StaticObject loadavg, int nelems) {
        return -1; // unobtainable
    }

    // Java 11 new methods:

    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    @SuppressWarnings("unused")
    public static boolean isBigEndian0(@Host(Unsafe.class) StaticObject self) {
        return ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    @SuppressWarnings("unused")
    public static boolean unalignedAccess0(@Host(Unsafe.class) StaticObject self) {
        // Be conservative, unobtainable (in java.nio.Bits, package-private class)
        return false;
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    @SuppressWarnings("unused")
    public static long objectFieldOffset1(@Host(Unsafe.class) StaticObject self, @Host(value = Class.class) StaticObject cl, @Host(value = String.class) StaticObject guestName,
                    @InjectMeta Meta meta) {
        Klass k = cl.getMirrorKlass();
        String hostName = meta.toHostString(guestName);
        if (k instanceof ObjectKlass) {
            ObjectKlass kl = (ObjectKlass) k;
            for (Field f : kl.getFieldTable()) {
                if (f.getNameAsString().equals(hostName)) {
                    return SAFETY_FIELD_OFFSET + f.getSlot();
                }
            }
            for (Field f : kl.getStaticFieldTable()) {
                if (f.getNameAsString().equals(hostName)) {
                    return SAFETY_FIELD_OFFSET + f.getSlot();
                }
            }
        }
        throw meta.throwException(meta.java_lang_InternalError);
    }

    @Substitution(hasReceiver = true)
    public static boolean compareAndSetObject(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset,
                    @Host(Object.class) StaticObject before, @Host(Object.class) StaticObject after, @InjectMeta Meta meta) {
        return compareAndSwapObject(self, holder, offset, before, after, meta);
    }

    @Substitution(hasReceiver = true)
    public static boolean compareAndSetInt(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, int before,
                    int after, @InjectMeta Meta meta) {
        return compareAndSwapInt(self, holder, offset, before, after, meta);
    }

    @Substitution(hasReceiver = true)
    public static boolean compareAndSetLong(@SuppressWarnings("unused") @Host(Unsafe.class) StaticObject self, @Host(Object.class) StaticObject holder, long offset, long before,
                    long after, @InjectMeta Meta meta) {
        return compareAndSwapLong(self, holder, offset, before, after, meta);
    }

    public static class SharedUnsafe extends SubstitutionNamesProvider {
        private static String[] NAMES = new String[]{
                        TARGET_SUN_MISC_UNSAFE,
                        TARGET_JDK_INTERNAL_MISC_UNSAFE
        };
        public static SubstitutionNamesProvider INSTANCE = new SharedUnsafe();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }
    }

    public static class SharedUnsafeAppend0 extends SharedUnsafe {
        public static SubstitutionNamesProvider INSTANCE = new SharedUnsafeAppend0();

        @Override
        public String[] getMethodNames(String name) {
            return append0(this, name);
        }
    }

    public static class Unsafe8 extends SubstitutionNamesProvider {
        private static String[] NAMES = new String[]{
                        TARGET_SUN_MISC_UNSAFE
        };
        public static SubstitutionNamesProvider INSTANCE = new Unsafe8();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }
    }

    public static class Unsafe11 extends SubstitutionNamesProvider {
        private static String[] NAMES = new String[]{
                        TARGET_JDK_INTERNAL_MISC_UNSAFE
        };
        public static SubstitutionNamesProvider INSTANCE = new Unsafe11();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }
    }
}
