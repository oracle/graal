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
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.blocking.EspressoLock;
import com.oracle.truffle.espresso.blocking.GuestInterruptedException;
import com.oracle.truffle.espresso.ffi.Buffer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.EspressoClassLoadingException;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.GuestAllocator;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.threads.State;
import com.oracle.truffle.espresso.threads.Transition;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

@EspressoSubstitutions(nameProvider = Target_sun_misc_Unsafe.SharedUnsafe.class)
public final class Target_sun_misc_Unsafe {

    /** The value of {@code addressSize()}. */
    public static final int ADDRESS_SIZE;

    private static final int SAFETY_FIELD_OFFSET = 123456789;
    private static final int SAFETY_STATIC_FIELD_OFFSET = 3456789;
    private static final int ALLOWED_HIDDEN_FIELDS = 0x1000;

    private static final String TARGET_JDK_INTERNAL_MISC_UNSAFE = "Target_jdk_internal_misc_Unsafe";
    private static final String TARGET_SUN_MISC_UNSAFE = "Target_sun_misc_Unsafe";

    static {
        Unsafe unsafe = UnsafeAccess.get();
        ADDRESS_SIZE = unsafe.addressSize();
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    @SuppressWarnings("unused")
    public static @JavaType(Class.class) StaticObject defineAnonymousClass(
                    @JavaType(Unsafe.class) StaticObject self,
                    @JavaType(Class.class) StaticObject hostClass,
                    @JavaType(byte[].class) StaticObject data,
                    @JavaType(Object[].class) StaticObject constantPoolPatches,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta) {
        if (StaticObject.isNull(hostClass) || StaticObject.isNull(data)) {
            throw meta.throwNullPointerException();
        }
        if (hostClass.getMirrorKlass(meta).isArray() || hostClass.getMirrorKlass(meta).isPrimitive()) {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }

        byte[] bytes = data.unwrap(language);
        ObjectKlass hostKlass = (ObjectKlass) hostClass.getMirrorKlass(meta);
        StaticObject pd = (StaticObject) meta.HIDDEN_PROTECTION_DOMAIN.getHiddenObject(hostClass);
        StaticObject[] patches = StaticObject.isNull(constantPoolPatches) ? null : constantPoolPatches.unwrap(language);
        // Inherit host class's protection domain.
        ClassRegistry.ClassDefinitionInfo info = new ClassRegistry.ClassDefinitionInfo(pd, hostKlass, patches);

        EspressoContext context = meta.getContext();
        ObjectKlass k = null;
        try {
            k = context.getRegistries().defineKlass(null, bytes, hostKlass.getDefiningClassLoader(), info);
        } catch (EspressoClassLoadingException e) {
            throw e.asGuestException(meta);
        }

        // Initialize, because no one else will.
        k.safeInitialize();

        return k.mirror();
    }

    /**
     * Report the offset of the first element in the storage allocation of a given array class. If
     * {@link #arrayIndexScale} returns a non-zero value for the same class, you may use that scale
     * factor, together with this base offset, to form new offsets to access elements of arrays of
     * the given class.
     *
     * @see Target_sun_misc_Unsafe.GetInt
     * @see Target_sun_misc_Unsafe.PutInt
     */
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static int arrayBaseOffset(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Class.class) StaticObject clazz, @Inject Meta meta) {
        Unsafe unsafe = UnsafeAccess.getIfAllowed(meta);
        Klass klass = clazz.getMirrorKlass(meta);
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
     * {@link Target_sun_misc_Unsafe.GetByte}, so the scale factor for such classes is reported as
     * zero.
     *
     * @see #arrayBaseOffset
     * @see Target_sun_misc_Unsafe.GetInt
     * @see Target_sun_misc_Unsafe.PutInt
     */
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static int arrayIndexScale(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Class.class) StaticObject clazz, @Inject Meta meta) {
        Unsafe unsafe = UnsafeAccess.getIfAllowed(meta);
        Klass klass = clazz.getMirrorKlass(meta);
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
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class, isTrivial = true)
    public static int addressSize(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self) {
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
     * @see Target_sun_misc_Unsafe.GetInt
     */
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static long objectFieldOffset(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(java.lang.reflect.Field.class) StaticObject field,
                    @Inject Meta meta) {
        Field target = Field.getReflectiveFieldRoot(field, meta);
        return (target.isStatic() ? SAFETY_STATIC_FIELD_OFFSET : SAFETY_FIELD_OFFSET) + target.getSlot();
    }

    static int safetyOffsetToSlot(long safetyOffset) {
        int offset = Math.toIntExact(safetyOffset);
        if (offset >= (SAFETY_FIELD_OFFSET - ALLOWED_HIDDEN_FIELDS)) {
            return offset - SAFETY_FIELD_OFFSET;
        } else {
            assert offset >= (SAFETY_STATIC_FIELD_OFFSET - ALLOWED_HIDDEN_FIELDS) : "offset: " + offset;
            return offset - SAFETY_STATIC_FIELD_OFFSET;
        }
    }

    static long slotToSafetyOffset(int slot, boolean isStatic) {
        return ((long) (isStatic ? SAFETY_STATIC_FIELD_OFFSET : SAFETY_FIELD_OFFSET)) + slot;
    }

    private static Field resolveUnsafeAccessField(StaticObject holder, long offset, Meta meta) {
        int slot;
        int safetyOffset = Math.toIntExact(offset);
        boolean isStatic = false;
        if (safetyOffset >= (SAFETY_FIELD_OFFSET - ALLOWED_HIDDEN_FIELDS)) {
            slot = safetyOffset - SAFETY_FIELD_OFFSET;
        } else {
            assert safetyOffset >= (SAFETY_STATIC_FIELD_OFFSET - ALLOWED_HIDDEN_FIELDS) : "safetyOffset: " + safetyOffset;
            slot = safetyOffset - SAFETY_STATIC_FIELD_OFFSET;
            isStatic = true;
        }

        assert !StaticObject.isNull(holder);

        if (slot >= 1 << 16 || slot < (-ALLOWED_HIDDEN_FIELDS)) {
            // the field offset is not normalized
            return null;
        }
        Field field = null;
        try {
            if (isStatic) {
                if (holder.isMirrorKlass()) {
                    // This is needed to support:
                    // > int off = U.objectFieldOffset(SomeClass.class, "staticField")
                    // > U.getInt(SomeClass.class, off);
                    // HotSpot supports it, although it is a questionable usage.
                    field = holder.getMirrorKlass(meta).lookupStaticFieldTable(slot);
                } else {
                    assert holder.isStaticStorage();
                    field = holder.getKlass().lookupStaticFieldTable(slot);
                }
            } else {
                field = holder.getKlass().lookupFieldTable(slot);
            }
        } catch (IndexOutOfBoundsException ex) {
            // Invalid field offset
            return null;
        }
        assert field != null;
        return field;
    }

    private static StaticObject resolveUnsafeAccessHolder(Field f, StaticObject advertisedHolder, Meta meta) {
        if (f.isStatic() && advertisedHolder.isMirrorKlass()) {
            // This is needed to support:
            // > int off = U.objectFieldOffset(SomeClass.class, "staticField")
            // > U.getInt(SomeClass.class, off);
            // HotSpot supports it, although it is a questionable usage.
            return advertisedHolder.getMirrorKlass(meta).getStatics();
        }
        return advertisedHolder;
    }

    static EspressoException throwUnsupported(Meta meta, String message) {
        throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedOperationException, message);
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static @JavaType(Class.class) StaticObject defineClass(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(String.class) StaticObject name,
                    @JavaType(byte[].class) StaticObject guestBuf, int offset, int len, @JavaType(ClassLoader.class) StaticObject loader,
                    @JavaType(ProtectionDomain.class) StaticObject pd,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta) {
        byte[] buf = guestBuf.unwrap(language);
        byte[] bytes = Arrays.copyOfRange(buf, offset, len);
        EspressoContext context = meta.getContext();
        Klass klass;
        try {
            klass = context.getRegistries().defineKlass(meta.getTypes().fromClassGetName(meta.toHostString(name)), bytes, loader, new ClassRegistry.ClassDefinitionInfo(pd));
        } catch (EspressoClassLoadingException e) {
            throw e.asGuestException(meta);
        }
        return klass.mirror();
    }

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
     * @see Target_sun_misc_Unsafe.GetByte
     * @see Target_sun_misc_Unsafe.PutByte
     */
    @TruffleBoundary
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static long allocateMemory(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long length, @Inject Meta meta) {
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
            CompilerDirectives.transferToInterpreterAndInvalidate();
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
    public static long reallocateMemory(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, long newSize, @Inject Meta meta) {
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
    public static void freeMemory(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, @Inject Meta meta) {
        meta.getNativeAccess().freeMemory(RawPointer.create(address));
    }

    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static boolean shouldBeInitialized(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Class.class) StaticObject clazz,
                    @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(clazz)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        Klass klass = clazz.getMirrorKlass(meta);
        return !klass.isInitialized();
    }

    /**
     * Ensure the given class has been initialized. This is often needed in conjunction with
     * obtaining the static field base of a class.
     */
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static void ensureClassInitialized(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Class.class) StaticObject clazz,
                    @Inject Meta meta) {
        clazz.getMirrorKlass(meta).safeInitialize();
    }

    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static void copyMemory(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject srcBase, long srcOffset,
                    @JavaType(Object.class) StaticObject destBase, long destOffset, long bytes, @Inject EspressoLanguage language, @Inject Meta meta) {
        if (bytes == 0) {
            return;
        }
        UnsafeAccess.getIfAllowed(meta).copyMemory(MetaUtil.unwrapArrayOrNull(language, srcBase), srcOffset, MetaUtil.unwrapArrayOrNull(language, destBase), destOffset, bytes);
    }

    @Substitution(hasReceiver = true)
    public static void copySwapMemory0(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject srcBase, long srcOffset,
                    @JavaType(Object.class) StaticObject destBase, long destOffset, long bytes, long elemSize, @Inject EspressoLanguage language, @Inject Meta meta) {
        if (bytes == 0) {
            return;
        }
        UnsafeAccess.checkAllowed(meta);
        UnsafeSupport.copySwapMemory(MetaUtil.unwrapArrayOrNull(language, srcBase), srcOffset, MetaUtil.unwrapArrayOrNull(language, destBase), destOffset, bytes, elemSize);
    }

    /**
     * Allocate an instance but do not run any constructor. Initializes the class if it has not yet
     * been.
     */
    @Throws(InstantiationException.class)
    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(Object.class) StaticObject allocateInstance(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Class.class) StaticObject clazz,
                    @Inject Meta meta) {
        Klass mirrorKlass = clazz.getMirrorKlass(meta);
        GuestAllocator.AllocationChecks.checkCanAllocateNewReference(meta, mirrorKlass, false);
        return meta.getAllocator().createNew((ObjectKlass) mirrorKlass);
    }

    /**
     * Sets all bytes in a given block of memory to a fixed value (usually zero).
     *
     * <p>
     * This method determines a block's base address by means of two parameters, and so it provides
     * (in effect) a <em>double-register</em> addressing mode, as discussed in
     * {@link Target_sun_misc_Unsafe.GetInt}. When the object reference is null, the offset supplies
     * an absolute base address.
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
    public static void setMemory(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject o, long offset, long bytes, byte value,
                    @Inject Meta meta, @Inject EspressoLanguage language) {
        Object hostObject;
        if (StaticObject.isNull(o)) {
            hostObject = null;
        } else if (o.getKlass().isArray()) {
            hostObject = o.unwrap(language);
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere();
        }
        UnsafeAccess.getIfAllowed(meta).setMemory(hostObject, offset, bytes, value);
    }

    /**
     * Report the size in bytes of a native memory page (whatever that is). This value will always
     * be a power of two.
     */
    @Substitution(hasReceiver = true)
    public static int pageSize(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @Inject Meta meta) {
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
     * in a form usable by {@link Target_sun_misc_Unsafe.GetInt}. Therefore, code which will be
     * ported to such JVMs on 64-bit platforms must preserve all bits of static field offsets.
     *
     * @see Target_sun_misc_Unsafe.GetInt
     */
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static long staticFieldOffset(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(java.lang.reflect.Field.class) StaticObject field,
                    @Inject Meta meta) {
        return Field.getReflectiveFieldRoot(field, meta).getSlot() + SAFETY_STATIC_FIELD_OFFSET;
    }

    /**
     * Report the location of a given static field, in conjunction with {@link #staticFieldOffset}.
     * <p>
     * Fetch the base "Object", if any, with which static fields of the given class can be accessed
     * via methods like {@link Target_sun_misc_Unsafe.GetInt}. This value may be null. This value
     * may refer to an object which is a "cookie", not guaranteed to be a real Object, and it should
     * not be used in any way except as argument to the get and put routines in this class.
     */
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static @JavaType(Object.class) StaticObject staticFieldBase(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self,
                    @JavaType(java.lang.reflect.Field.class) StaticObject field,
                    @Inject Meta meta) {
        Field target = Field.getReflectiveFieldRoot(field, meta);
        return target.getDeclaringKlass().getStatics();
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    public static void monitorEnter(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject object,
                    @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(object)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        InterpreterToVM.monitorUnsafeEnter(object.getLock(meta.getContext()));
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    public static void monitorExit(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject object,
                    @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(object)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        InterpreterToVM.monitorUnsafeExit(object.getLock(meta.getContext()));
    }

    @Substitution(hasReceiver = true, isTrivial = true)
    public static void throwException(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Throwable.class) StaticObject ee, @Inject Meta meta) {
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
    @SuppressWarnings({"try", "unused"})
    public static void park(@JavaType(Unsafe.class) StaticObject self, boolean isAbsolute, long time,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        if (time < 0 || (isAbsolute && time == 0)) { // don't wait at all
            return;
        }

        EspressoContext context = meta.getContext();
        StaticObject thread = context.getCurrentPlatformThread();

        // Check return condition beforehand
        if (parkReturnCondition(thread, meta)) {
            return;
        }
        State state = time > 0 ? State.TIMED_WAITING : State.WAITING;
        try (Transition transition = Transition.transition(context, state)) {
            parkImpl(isAbsolute, time, thread, meta);
        }
    }

    private static void parkImpl(boolean isAbsolute, long time, StaticObject thread, Meta meta) {
        EspressoLock parkLock = getParkLock(thread, meta);
        parkLock.lock();
        try {
            while (true) {
                if (parkReturnCondition(thread, meta)) {
                    return;
                }
                boolean elapsed;
                if (isAbsolute) {
                    elapsed = !parkLock.awaitUntil(new Date(time));
                } else {
                    elapsed = !parkLock.await(time, TimeUnit.NANOSECONDS);
                }
                if (elapsed) {
                    return;
                }
                // Signals will wake us up. Loop back to check return conditions.
            }
        } catch (GuestInterruptedException e) {
            // Interruptions do not throw, nor do they clear interrupted status.
            return;
        } finally {
            parkLock.unlock();
        }
    }

    private static EspressoLock getParkLock(StaticObject thread, Meta meta) {
        return (EspressoLock) meta.HIDDEN_THREAD_PARK_LOCK.getHiddenObject(thread);
    }

    private static boolean parkReturnCondition(StaticObject thread, Meta meta) {
        return consumeUnparkSignal(thread, meta) || // balancing unpark
                        meta.getThreadAccess().isInterrupted(thread, false);
    }

    private static boolean consumeUnparkSignal(StaticObject self, Meta meta) {
        Field signals = meta.HIDDEN_THREAD_UNPARK_SIGNALS;
        return signals.getAndSetInt(self, 0) > 0;
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
    public static void unpark(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject thread,
                    @Inject Meta meta) {
        EspressoLock parkLock = getParkLock(thread, meta);
        parkLock.lock();
        try {
            meta.HIDDEN_THREAD_UNPARK_SIGNALS.setInt(thread, 1, true);
            parkLock.signal();
        } finally {
            parkLock.unlock();
        }
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
    public static long getAddress(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, @Inject Meta meta) {
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
    public static void putAddress(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, long x, @Inject Meta meta) {
        UnsafeAccess.getIfAllowed(meta).putAddress(address, x);
    }

    /**
     * Ensures lack of reordering of loads before the fence with loads or stores after the fence.
     *
     * @since 1.8
     */
    @Substitution(hasReceiver = true)
    public static void loadFence(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @Inject Meta meta) {
        UnsafeAccess.getIfAllowed(meta).loadFence();
    }

    /**
     * Ensures lack of reordering of stores before the fence with loads or stores after the fence.
     *
     * @since 1.8
     */
    @Substitution(hasReceiver = true)
    public static void storeFence(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @Inject Meta meta) {
        UnsafeAccess.getIfAllowed(meta).storeFence();
    }

    /**
     * Ensures lack of reordering of loads or stores before the fence with loads or stores after the
     * fence.
     *
     * @since 1.8
     */
    @Substitution(hasReceiver = true)
    public static void fullFence(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @Inject Meta meta) {
        UnsafeAccess.getIfAllowed(meta).fullFence();
    }

    @SuppressWarnings("deprecation")
    @Substitution(hasReceiver = true)
    public static boolean tryMonitorEnter(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject object,
                    @Inject Meta meta) {
        if (StaticObject.isNull(object)) {
            throw meta.throwNullPointerException();
        }
        return InterpreterToVM.monitorTryLock(object.getLock(meta.getContext()));
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
    public static int getLoadAverage(@JavaType(Unsafe.class) StaticObject self, @JavaType(double[].class) StaticObject loadavg, int nelems) {
        return -1; // unobtainable
    }

    // Java 11 new methods:

    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    @SuppressWarnings("unused")
    public static boolean isBigEndian0(@JavaType(Unsafe.class) StaticObject self) {
        return ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    @SuppressWarnings("unused")
    public static boolean unalignedAccess0(@JavaType(Unsafe.class) StaticObject self) {
        // Be conservative, unobtainable (in java.nio.Bits, package-private class)
        return false;
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    @SuppressWarnings("unused")
    public static long objectFieldOffset1(@JavaType(Unsafe.class) StaticObject self, @JavaType(value = Class.class) StaticObject cl, @JavaType(value = String.class) StaticObject guestName,
                    @Inject Meta meta) {
        Klass k = cl.getMirrorKlass(meta);
        String hostName = meta.toHostString(guestName);
        if (k instanceof ObjectKlass) {
            ObjectKlass kl = (ObjectKlass) k;
            for (Field f : kl.getFieldTable()) {
                if (!f.isRemoved() && f.getNameAsString().equals(hostName)) {
                    return SAFETY_FIELD_OFFSET + f.getSlot();
                }
            }
            for (Field f : kl.getStaticFieldTable()) {
                if (!f.isRemoved() && f.getNameAsString().equals(hostName)) {
                    return SAFETY_STATIC_FIELD_OFFSET + f.getSlot();
                }
            }
        }
        throw meta.throwException(meta.java_lang_InternalError);
    }

    // region UnsafeAccessors

    abstract static class GetFieldFromIndexNode extends EspressoNode {
        static final int LIMIT = 3;

        abstract Field execute(StaticObject holder, long slot);

        @Specialization(guards = {"slot == cachedSlot", "holder.isStaticStorage() == cachedIsStaticStorage", "holder.getKlass() == cachedKlass"}, limit = "LIMIT")
        protected Field doCached(@SuppressWarnings("unused") StaticObject holder, @SuppressWarnings("unused") long slot,
                        @SuppressWarnings("unused") @Cached("slot") long cachedSlot,
                        @SuppressWarnings("unused") @Cached("holder.getKlass()") Klass cachedKlass,
                        @SuppressWarnings("unused") @Cached("holder.isStaticStorage()") boolean cachedIsStaticStorage,
                        @Cached("doGeneric(holder, slot)") Field cachedField) {
            return cachedField;
        }

        @Specialization(replaces = "doCached")
        protected Field doGeneric(StaticObject holder, long slot) {
            return resolveUnsafeAccessField(holder, slot, getMeta());
        }

        public static GetFieldFromIndexNode create() {
            return Target_sun_misc_UnsafeFactory.GetFieldFromIndexNodeGen.create();
        }
    }

    abstract static class UnsafeAccessNode extends SubstitutionNode {
        protected static boolean isNullOrArray(StaticObject object) {
            return StaticObject.isNull(object) || object.isArray(); // order matters
        }

        protected static Object unwrapNullOrArray(EspressoLanguage language, StaticObject object) {
            assert isNullOrArray(object);
            if (StaticObject.isNull(object)) {
                return null;
            }
            return object.unwrap(language);
        }
    }

    // region put*(long offset, * value)

    /**
     * Stores a value into a given memory address. If the address is zero, or does not point into a
     * block obtained from {@link #allocateMemory}, the results are undefined.
     *
     * @see Target_sun_misc_Unsafe.GetByte
     */
    @Substitution(hasReceiver = true)
    abstract static class PutByte extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, byte value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, byte value) {
            UnsafeAccess.getIfAllowed(getMeta()).putByte(address, value);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class PutChar extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, char value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, char value) {
            UnsafeAccess.getIfAllowed(getMeta()).putChar(address, value);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class PutShort extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, short value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, short value) {
            UnsafeAccess.getIfAllowed(getMeta()).putShort(address, value);
        }
    }

    /** @see Target_sun_misc_Unsafe.GetByteWithBase */
    @Substitution(hasReceiver = true)
    abstract static class PutInt extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, int value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, int value) {
            UnsafeAccess.getIfAllowed(getMeta()).putInt(address, value);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class PutFloat extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, float value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, float value) {
            UnsafeAccess.getIfAllowed(getMeta()).putFloat(address, value);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class PutDouble extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, double value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, double value) {
            UnsafeAccess.getIfAllowed(getMeta()).putDouble(address, value);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class PutLong extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, long value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, long value) {
            UnsafeAccess.getIfAllowed(getMeta()).putLong(address, value);
        }
    }

    // endregion put*(long offset, * value)

    // region put*(Object holder, long offset, * value)

    /**
     * Fetches a value from a given memory address. If the address is zero, or does not point into a
     * block obtained from {@link #allocateMemory}, the results are undefined.
     *
     * @see #allocateMemory
     */
    @Substitution(hasReceiver = true, methodName = "putByte")
    @InlineInBytecode
    public abstract static class PutByteWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, byte value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, byte value) {
            UnsafeAccess.getIfAllowed(getMeta()).putByte(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, byte value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setByte(resolveUnsafeAccessHolder(f, holder, getMeta()), value);
        }
    }

    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeObjectAccessToReference.class, methodName = "putObject")
    @InlineInBytecode
    public abstract static class PutObjectWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value) {
            UnsafeAccess.getIfAllowed(getMeta()).putObject(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value, @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setObject(resolveUnsafeAccessHolder(f, holder, getMeta()), value);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putBoolean")
    @InlineInBytecode
    public abstract static class PutBooleanWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, boolean value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, boolean value) {
            UnsafeAccess.getIfAllowed(getMeta()).putBoolean(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, boolean value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setBoolean(resolveUnsafeAccessHolder(f, holder, getMeta()), value);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putChar")
    @InlineInBytecode
    public abstract static class PutCharWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, char value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, char value) {
            UnsafeAccess.getIfAllowed(getMeta()).putChar(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, char value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setChar(resolveUnsafeAccessHolder(f, holder, getMeta()), value);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putShort")
    @InlineInBytecode
    public abstract static class PutShortWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, short value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, short value) {
            UnsafeAccess.getIfAllowed(getMeta()).putShort(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, short value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setShort(resolveUnsafeAccessHolder(f, holder, getMeta()), value);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putInt")
    @InlineInBytecode
    public abstract static class PutIntWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value) {
            UnsafeAccess.getIfAllowed(getMeta()).putInt(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setInt(resolveUnsafeAccessHolder(f, holder, getMeta()), value);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putFloat")
    @InlineInBytecode
    public abstract static class PutFloatWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, float value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, float value) {
            UnsafeAccess.getIfAllowed(getMeta()).putFloat(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, float value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setFloat(resolveUnsafeAccessHolder(f, holder, getMeta()), value);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putDouble")
    @InlineInBytecode
    public abstract static class PutDoubleWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, double value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, double value) {
            UnsafeAccess.getIfAllowed(getMeta()).putDouble(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, double value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setDouble(resolveUnsafeAccessHolder(f, holder, getMeta()), value);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putLong")
    @InlineInBytecode
    public abstract static class PutLongWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value) {
            UnsafeAccess.getIfAllowed(getMeta()).putLong(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setLong(resolveUnsafeAccessHolder(f, holder, getMeta()), value);
        }
    }

    // endregion put*(Object holder, long offset, * value)

    // region putOrdered*(Object holder, long offset, * value)

    // TODO: Volatile access is stronger than needed.

    @Substitution(hasReceiver = true)
    @InlineInBytecode
    public abstract static class PutOrderedInt extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value) {
            UnsafeAccess.getIfAllowed(getMeta()).putOrderedInt(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setInt(resolveUnsafeAccessHolder(f, holder, getMeta()), value, true);
        }
    }

    @Substitution(hasReceiver = true)
    @InlineInBytecode
    public abstract static class PutOrderedLong extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value) {
            UnsafeAccess.getIfAllowed(getMeta()).putOrderedLong(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setLong(resolveUnsafeAccessHolder(f, holder, getMeta()), value, true);
        }
    }

    @Substitution(hasReceiver = true)
    @InlineInBytecode
    public abstract static class PutOrderedObject extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value) {
            UnsafeAccess.getIfAllowed(getMeta()).putOrderedObject(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setObject(resolveUnsafeAccessHolder(f, holder, getMeta()), value, true);
        }
    }

    // endregion put*(Object holder, long offset, * value)

    // region get*(Object holder, long offset)

    /**
     * Fetches a value from a given memory address. If the address is zero, or does not point into a
     * block obtained from {@link #allocateMemory}, the results are undefined.
     *
     * @see #allocateMemory
     */
    @Substitution(hasReceiver = true, methodName = "getByte")
    @InlineInBytecode
    public abstract static class GetByteWithBase extends UnsafeAccessNode {
        abstract byte execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected byte doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getByte(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected byte doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Byte) {
                return f.getByte(resolveUnsafeAccessHolder(f, holder, getMeta()));
            }
            return f.getAsByte(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false);
        }
    }

    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeObjectAccessToReference.class, methodName = "getObject")
    @InlineInBytecode
    public abstract static class GetObjectWithBase extends UnsafeAccessNode {
        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected @JavaType(Object.class) StaticObject doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder,
                        long offset) {
            return (StaticObject) UnsafeAccess.getIfAllowed(getMeta()).getObject(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected @JavaType(Object.class) StaticObject doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Object) {
                return f.getObject(resolveUnsafeAccessHolder(f, holder, getMeta()));
            }
            return f.getAsObject(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()));
        }
    }

    @Substitution(hasReceiver = true, methodName = "getBoolean")
    @InlineInBytecode
    public abstract static class GetBooleanWithBase extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected boolean doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getBoolean(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected boolean doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Boolean) {
                return f.getBoolean(resolveUnsafeAccessHolder(f, holder, getMeta()));
            }
            return f.getAsBoolean(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getChar")
    @InlineInBytecode
    public abstract static class GetCharWithBase extends UnsafeAccessNode {
        abstract char execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected char doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getChar(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected char doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Char) {
                return f.getChar(resolveUnsafeAccessHolder(f, holder, getMeta()));
            }
            return f.getAsChar(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getShort")
    @InlineInBytecode
    public abstract static class GetShortWithBase extends UnsafeAccessNode {
        abstract short execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected short doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getShort(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected short doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Short) {
                return f.getShort(resolveUnsafeAccessHolder(f, holder, getMeta()));
            }
            return f.getAsShort(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getInt")
    @InlineInBytecode
    public abstract static class GetIntWithBase extends UnsafeAccessNode {
        abstract int execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected int doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getInt(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected int doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Int) {
                return f.getInt(resolveUnsafeAccessHolder(f, holder, getMeta()));
            }
            return f.getAsInt(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getFloat")
    @InlineInBytecode
    public abstract static class GetFloatWithBase extends UnsafeAccessNode {
        abstract float execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected float doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getFloat(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected float doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Float) {
                return f.getFloat(resolveUnsafeAccessHolder(f, holder, getMeta()));
            }
            return f.getAsFloat(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getDouble")
    @InlineInBytecode
    public abstract static class GetDoubleWithBase extends UnsafeAccessNode {
        abstract double execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected double doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getDouble(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected double doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Double) {
                return f.getDouble(resolveUnsafeAccessHolder(f, holder, getMeta()));
            }
            return f.getAsDouble(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getLong")
    @InlineInBytecode
    public abstract static class GetLongWithBase extends UnsafeAccessNode {
        abstract long execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected long doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getLong(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected long doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Long) {
                return f.getLong(resolveUnsafeAccessHolder(f, holder, getMeta()));
            }
            return f.getAsLong(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false);
        }
    }

    // endregion get*(Object holder, long offset)

    // region get*Volatile(Object holder, long offset)

    @Substitution(hasReceiver = true, methodName = "getByteVolatile")
    @InlineInBytecode
    public abstract static class GetByteVolatileWithBase extends UnsafeAccessNode {
        abstract byte execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected byte doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getByteVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected byte doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Byte) {
                return f.getByte(resolveUnsafeAccessHolder(f, holder, getMeta()), true);
            }
            return f.getAsByte(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false, true);
        }
    }

    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeObjectAccessToReference.class, methodName = "getObjectVolatile")
    @InlineInBytecode
    public abstract static class GetObjectVolatileWithBase extends UnsafeAccessNode {
        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected @JavaType(Object.class) StaticObject doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder,
                        long offset) {
            return (StaticObject) UnsafeAccess.getIfAllowed(getMeta()).getObjectVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected @JavaType(Object.class) StaticObject doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Object) {
                return f.getObject(resolveUnsafeAccessHolder(f, holder, getMeta()), true);
            }
            return f.getAsObject(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getBooleanVolatile")
    @InlineInBytecode
    public abstract static class GetBooleanVolatileWithBase extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected boolean doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getBooleanVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected boolean doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Boolean) {
                return f.getBoolean(resolveUnsafeAccessHolder(f, holder, getMeta()), true);
            }
            return f.getAsBoolean(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getCharVolatile")
    @InlineInBytecode
    public abstract static class GetCharVolatileWithBase extends UnsafeAccessNode {
        abstract char execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected char doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getCharVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected char doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Char) {
                return f.getChar(resolveUnsafeAccessHolder(f, holder, getMeta()), true);
            }
            return f.getAsChar(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getShortVolatile")
    @InlineInBytecode
    public abstract static class GetShortVolatileWithBase extends UnsafeAccessNode {
        abstract short execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected short doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getShortVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected short doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Short) {
                return f.getShort(resolveUnsafeAccessHolder(f, holder, getMeta()), true);
            }
            return f.getAsShort(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getIntVolatile")
    @InlineInBytecode
    public abstract static class GetIntVolatileWithBase extends UnsafeAccessNode {
        abstract int execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected int doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getIntVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected int doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Int) {
                return f.getInt(resolveUnsafeAccessHolder(f, holder, getMeta()), true);
            }
            return f.getAsInt(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getFloatVolatile")
    @InlineInBytecode
    public abstract static class GetFloatVolatileWithBase extends UnsafeAccessNode {
        abstract float execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected float doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getFloatVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected float doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Float) {
                return f.getFloat(resolveUnsafeAccessHolder(f, holder, getMeta()), true);
            }
            return f.getAsFloat(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getDoubleVolatile")
    @InlineInBytecode
    public abstract static class GetDoubleVolatileWithBase extends UnsafeAccessNode {
        abstract double execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected double doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getDoubleVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected double doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Double) {
                return f.getDouble(resolveUnsafeAccessHolder(f, holder, getMeta()), true);
            }
            return f.getAsDouble(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "getLongVolatile")
    @InlineInBytecode
    public abstract static class GetLongVolatileWithBase extends UnsafeAccessNode {
        abstract long execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        protected long doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getLongVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected long doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() == JavaKind.Long) {
                return f.getLong(resolveUnsafeAccessHolder(f, holder, getMeta()), true);
            }
            return f.getAsLong(getMeta(), resolveUnsafeAccessHolder(f, holder, getMeta()), false, true);
        }
    }

    // endregion get*Volatile(Object holder, long offset)

    // region get*(long offset)

    @Substitution(hasReceiver = true)
    abstract static class GetByte extends UnsafeAccessNode {

        abstract byte execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        byte doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getByte(address);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class GetChar extends UnsafeAccessNode {

        abstract char execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        char doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getChar(address);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class GetShort extends UnsafeAccessNode {

        abstract short execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        short doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getShort(address);
        }
    }

    /** @see Target_sun_misc_Unsafe.GetByteWithBase */
    @Substitution(hasReceiver = true)
    abstract static class GetInt extends UnsafeAccessNode {

        abstract int execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        int doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getInt(address);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class GetFloat extends UnsafeAccessNode {

        abstract float execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        float doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getFloat(address);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class GetDouble extends UnsafeAccessNode {

        abstract double execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        double doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getDouble(address);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class GetLong extends UnsafeAccessNode {

        abstract long execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        long doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getLong(address);
        }
    }

    // endregion get*(long offset)

    // region put*Volatile(Object holder, long offset)

    @Substitution(hasReceiver = true, methodName = "putByteVolatile")
    @InlineInBytecode
    public abstract static class PutByteVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, byte value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, byte value) {
            UnsafeAccess.getIfAllowed(getMeta()).putByteVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, byte value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setByte(resolveUnsafeAccessHolder(f, holder, getMeta()), value, true);
        }
    }

    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeObjectAccessToReference.class, methodName = "putObjectVolatile")
    @InlineInBytecode
    public abstract static class PutObjectVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value) {
            UnsafeAccess.getIfAllowed(getMeta()).putObjectVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value, @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setObject(resolveUnsafeAccessHolder(f, holder, getMeta()), value, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putBooleanVolatile")
    @InlineInBytecode
    public abstract static class PutBooleanVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, boolean value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, boolean value) {
            UnsafeAccess.getIfAllowed(getMeta()).putBooleanVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, boolean value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setBoolean(resolveUnsafeAccessHolder(f, holder, getMeta()), value, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putCharVolatile")
    @InlineInBytecode
    public abstract static class PutCharVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, char value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, char value) {
            UnsafeAccess.getIfAllowed(getMeta()).putCharVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, char value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setChar(resolveUnsafeAccessHolder(f, holder, getMeta()), value, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putShortVolatile")
    @InlineInBytecode
    public abstract static class PutShortVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, short value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, short value) {
            UnsafeAccess.getIfAllowed(getMeta()).putShortVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, short value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setShort(resolveUnsafeAccessHolder(f, holder, getMeta()), value, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putIntVolatile")
    @InlineInBytecode
    public abstract static class PutIntVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value) {
            UnsafeAccess.getIfAllowed(getMeta()).putIntVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setInt(resolveUnsafeAccessHolder(f, holder, getMeta()), value, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putFloatVolatile")
    @InlineInBytecode
    public abstract static class PutFloatVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, float value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, float value) {
            UnsafeAccess.getIfAllowed(getMeta()).putFloatVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, float value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setFloat(resolveUnsafeAccessHolder(f, holder, getMeta()), value, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putDoubleVolatile")
    @InlineInBytecode
    public abstract static class PutDoubleVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, double value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, double value) {
            UnsafeAccess.getIfAllowed(getMeta()).putDoubleVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, double value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setDouble(resolveUnsafeAccessHolder(f, holder, getMeta()), value, true);
        }
    }

    @Substitution(hasReceiver = true, methodName = "putLongVolatile")
    @InlineInBytecode
    public abstract static class PutLongVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value) {
            UnsafeAccess.getIfAllowed(getMeta()).putLongVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            f.setLong(resolveUnsafeAccessHolder(f, holder, getMeta()), value, true);
        }
    }

    // endregion put*Volatile(Object holder, long offset)

    // region compareAndSwap*

    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    @InlineInBytecode
    public abstract static class CompareAndSwapObject extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after);

        @Specialization(guards = "isNullOrArray(holder)")
        protected boolean doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after) {
            return UnsafeAccess.getIfAllowed(getMeta()).compareAndSwapObject(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected boolean doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            return f.compareAndSwapObject(resolveUnsafeAccessHolder(f, holder, getMeta()), before, after);
        }
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    @InlineInBytecode
    public abstract static class CompareAndSwapInt extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after);

        @Specialization(guards = "isNullOrArray(holder)")
        protected boolean doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after) {
            return UnsafeAccess.getIfAllowed(getMeta()).compareAndSwapInt(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected boolean doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            switch (f.getKind()) {
                case Int:
                    return f.compareAndSwapInt(resolveUnsafeAccessHolder(f, holder, getMeta()), before, after);
                case Float:
                    return f.compareAndSwapFloat(resolveUnsafeAccessHolder(f, holder, getMeta()), Float.intBitsToFloat(before), Float.intBitsToFloat(after));
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
        }
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    @InlineInBytecode
    public abstract static class CompareAndSwapLong extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after);

        @Specialization(guards = "isNullOrArray(holder)")
        protected boolean doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after) {
            return UnsafeAccess.getIfAllowed(getMeta()).compareAndSwapLong(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected boolean doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            switch (f.getKind()) {
                case Long:
                    return f.compareAndSwapLong(resolveUnsafeAccessHolder(f, holder, getMeta()), before, after);
                case Double:
                    return f.compareAndSwapDouble(resolveUnsafeAccessHolder(f, holder, getMeta()), Double.longBitsToDouble(before), Double.longBitsToDouble(after));
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
        }
    }

    // endregion compareAndSwap*

    // region compareAndExchange*

    /*
     * The following three methods are there to enable atomic operations on sub-word fields, which
     * would be impossible due to the safety checks in the static object model.
     *
     * All sub-word CAE operations route to `compareAndExchangeInt` in Java code, which, if left
     * as-is would access, for example, byte fields as ints, which is forbidden by the object model.
     *
     * As a workaround, create a substitution for sub-words CAE operations (to which CAS are routed
     * in Java code), and check the field kind to call the corresponding static property method.
     */

    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeObjectAccessToReference.class)
    @InlineInBytecode
    public abstract static class CompareAndExchangeObject extends UnsafeAccessNode {
        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after);

        @Specialization(guards = "isNullOrArray(holder)")
        protected @JavaType(Object.class) StaticObject doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder,
                        long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after) {
            UnsafeAccess.checkAllowed(getMeta());
            return (StaticObject) UnsafeSupport.compareAndExchangeObject(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected @JavaType(Object.class) StaticObject doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            if (f.getKind() != JavaKind.Object) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
            }
            return f.compareAndExchangeObject(resolveUnsafeAccessHolder(f, holder, getMeta()), before, after);
        }
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    @InlineInBytecode
    public abstract static class CompareAndExchangeInt extends UnsafeAccessNode {
        abstract int execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after);

        @Specialization(guards = "isNullOrArray(holder)")
        protected int doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after) {
            UnsafeAccess.checkAllowed(getMeta());
            return UnsafeSupport.compareAndExchangeInt(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected int doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            switch (f.getKind()) {
                case Int:
                    return f.compareAndExchangeInt(resolveUnsafeAccessHolder(f, holder, getMeta()), before, after);
                case Float:
                    return Float.floatToRawIntBits(f.compareAndExchangeFloat(resolveUnsafeAccessHolder(f, holder, getMeta()), Float.intBitsToFloat(before), Float.intBitsToFloat(after)));
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
        }
    }

    @Substitution(hasReceiver = true)
    public abstract static class CompareAndExchangeByte extends UnsafeAccessNode {
        abstract byte execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        byte before, byte after);

        @Specialization(guards = "isNullOrArray(holder)")
        protected byte doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        byte before, byte after) {
            UnsafeAccess.checkAllowed(getMeta());
            return UnsafeSupport.compareAndExchangeByte(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected byte doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        byte before, byte after,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            switch (f.getKind()) {
                case Boolean:
                    return f.compareAndExchangeBoolean(resolveUnsafeAccessHolder(f, holder, getMeta()), before != 0, after != 0) ? (byte) 1 : (byte) 0;
                case Byte:
                    return f.compareAndExchangeByte(resolveUnsafeAccessHolder(f, holder, getMeta()), before, after);
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
        }
    }

    @Substitution(hasReceiver = true)
    public abstract static class CompareAndExchangeShort extends UnsafeAccessNode {
        abstract short execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        short before, short after);

        @Specialization(guards = "isNullOrArray(holder)")
        protected short doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        short before, short after) {
            UnsafeAccess.checkAllowed(getMeta());
            return UnsafeSupport.compareAndExchangeShort(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected short doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        short before, short after,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            switch (f.getKind()) {
                case Short:
                    return f.compareAndExchangeShort(resolveUnsafeAccessHolder(f, holder, getMeta()), before, after);
                case Char:
                    return (short) f.compareAndExchangeChar(resolveUnsafeAccessHolder(f, holder, getMeta()), (char) before, (char) after);
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
        }
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    public abstract static class CompareAndExchangeLong extends UnsafeAccessNode {
        abstract long execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after);

        @Specialization(guards = "isNullOrArray(holder)")
        protected long doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after) {
            UnsafeAccess.checkAllowed(getMeta());
            return UnsafeSupport.compareAndExchangeLong(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected long doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after,
                        @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            switch (f.getKind()) {
                case Long:
                    return f.compareAndExchangeLong(resolveUnsafeAccessHolder(f, holder, getMeta()), before, after);
                case Double:
                    return Double.doubleToRawLongBits(f.compareAndExchangeDouble(resolveUnsafeAccessHolder(f, holder, getMeta()), Double.longBitsToDouble(before), Double.longBitsToDouble(after)));
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
        }
    }

    // endregion compareAndExchange*

    // region CompareAndSet*

    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    @InlineInBytecode
    public abstract static class GetAndSetObject extends UnsafeAccessNode {
        abstract @JavaType(Unsafe.class) StaticObject execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value);

        @Specialization(guards = "isNullOrArray(holder)")
        protected @JavaType(Unsafe.class) StaticObject doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder,
                        long offset,
                        @JavaType(Object.class) StaticObject value) {
            return (StaticObject) UnsafeAccess.getIfAllowed(getMeta()).getAndSetObject(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        protected @JavaType(Unsafe.class) StaticObject doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value, @Cached GetFieldFromIndexNode getField) {
            Field f = getField.execute(holder, offset);
            if (f == null) {
                throwUnsupported(getMeta(), "Raw unaligned unsafe access.");
            }
            return f.getAndSetObject(resolveUnsafeAccessHolder(f, holder, getMeta()), value);
        }
    }

    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeObjectAccessToReference.class)
    @InlineInBytecode
    abstract static class CompareAndSetObject extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after);

        @Specialization
        protected boolean doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after,
                        @Cached CompareAndSwapObject cas) {
            return cas.execute(self, holder, offset, before, after);
        }
    }

    @Substitution(hasReceiver = true)
    @InlineInBytecode
    abstract static class CompareAndSetInt extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after);

        @Specialization
        protected boolean doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after,
                        @Cached CompareAndSwapInt cas) {
            return cas.execute(self, holder, offset, before, after);
        }
    }

    @Substitution(hasReceiver = true)
    @InlineInBytecode
    abstract static class CompareAndSetLong extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after);

        @Specialization
        protected boolean doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after,
                        @Cached CompareAndSwapLong cas) {
            return cas.execute(self, holder, offset, before, after);
        }
    }

    // endregion CompareAndSet*

    // endregion UnsafeAccessors

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

    public static class SharedUnsafeObjectAccessToReference extends SubstitutionNamesProvider {
        private static String[] NAMES = new String[]{
                        TARGET_SUN_MISC_UNSAFE,
                        TARGET_JDK_INTERNAL_MISC_UNSAFE,
                        TARGET_JDK_INTERNAL_MISC_UNSAFE
        };
        public static SubstitutionNamesProvider INSTANCE = new SharedUnsafeObjectAccessToReference();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }

        @Override
        public String[] getMethodNames(String name) {
            String[] names = new String[3];
            names[0] = name;
            names[1] = name;
            names[2] = name.replace("Object", "Reference");
            return names;
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
