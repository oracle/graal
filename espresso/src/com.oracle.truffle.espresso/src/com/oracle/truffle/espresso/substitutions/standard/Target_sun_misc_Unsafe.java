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
package com.oracle.truffle.espresso.substitutions.standard;

import static com.oracle.truffle.espresso.substitutions.SubstitutionFlag.IsTrivial;
import static com.oracle.truffle.espresso.threads.ThreadState.PARKED;
import static com.oracle.truffle.espresso.threads.ThreadState.TIMED_PARKED;

import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.blocking.EspressoLock;
import com.oracle.truffle.espresso.blocking.GuestInterruptedException;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.JavaVersion;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.ffi.Buffer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.EspressoClassLoadingException;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.nodes.EspressoInlineNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.GuestAllocator;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.InlineInBytecode;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNamesProvider;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.substitutions.Throws;
import com.oracle.truffle.espresso.threads.ThreadState;
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

    private static final String JDK_INTERNAL_MISC_UNSAFE = "Ljdk/internal/misc/Unsafe;";
    private static final String SUN_MISC_UNSAFE = "Lsun/misc/Unsafe;";

    static {
        Unsafe unsafe = UnsafeAccess.get();
        ADDRESS_SIZE = unsafe.addressSize();
    }

    private Target_sun_misc_Unsafe() {
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
        StaticObject pd = meta.HIDDEN_PROTECTION_DOMAIN.getMaybeHiddenObject(hostClass);
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
     * @see GetInt
     * @see PutInt
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
     * {@link GetByte}, so the scale factor for such classes is reported as zero.
     *
     * @see #arrayBaseOffset
     * @see GetInt
     * @see PutInt
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
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class, flags = {IsTrivial})
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
     * @see GetInt
     */
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static long objectFieldOffset(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(java.lang.reflect.Field.class) StaticObject field,
                    @Inject Meta meta, @Inject EspressoLanguage language) {
        Field target = Field.getReflectiveFieldRoot(field, meta);
        if (target.isStatic()) {
            meta.throwIllegalArgumentExceptionBoundary();
        }
        return getGuestFieldOffset(target, language);
    }

    public interface GuestFieldOffsetStrategy {
        default int getGuestOffset(Field f) {
            return Math.toIntExact(slotToGuestOffset(f.getSlot(), f.isStatic()));
        }

        int guestOffsetToSlot(long guestOffset);

        boolean forceStatic(long guestOffset);

        long slotToGuestOffset(int slot, boolean isStatic);

        boolean isAllowed(JavaVersion v);

        String name();
    }

    public static final class SafetyGuestFieldOffsetStrategy implements GuestFieldOffsetStrategy {
        @Override
        public int guestOffsetToSlot(long guestOffset) {
            int offset = Math.toIntExact(guestOffset);
            if (forceStatic(offset)) {
                return offset - SAFETY_STATIC_FIELD_OFFSET;
            } else {
                return offset - SAFETY_FIELD_OFFSET;
            }
        }

        @Override
        public boolean forceStatic(long guestOffset) {
            return forceStatic(Math.toIntExact(guestOffset));
        }

        private static boolean forceStatic(int guestOffset) {
            return guestOffset < (SAFETY_FIELD_OFFSET - ALLOWED_HIDDEN_FIELDS);
        }

        @Override
        public long slotToGuestOffset(int slot, boolean isStatic) {
            return ((long) (isStatic ? SAFETY_STATIC_FIELD_OFFSET : SAFETY_FIELD_OFFSET)) + slot;
        }

        @Override
        public boolean isAllowed(JavaVersion v) {
            return true;
        }

        @Override
        public String name() {
            return "safety";
        }
    }

    public static final class CompactGuestFieldOffsetStrategy implements GuestFieldOffsetStrategy {
        @Override
        public int guestOffsetToSlot(long guestOffset) {
            return Math.toIntExact(guestOffset);
        }

        @Override
        public boolean forceStatic(long guestOffset) {
            return false;
        }

        @Override
        public long slotToGuestOffset(int slot, boolean isStatic) {
            return slot;
        }

        @Override
        public boolean isAllowed(JavaVersion v) {
            // JDK-8294278 & JDK-8297757 require being able to tell if an "offset" is a static or
            // instance field.
            return v.java18OrEarlier() || v.java21OrLater();
        }

        @Override
        public String name() {
            return "compact";
        }
    }

    public static final class GraalGuestFieldOffsetStrategy implements GuestFieldOffsetStrategy {
        @Override
        public int guestOffsetToSlot(long guestOffset) {
            return Math.toIntExact(guestOffset) >> 2;
        }

        @Override
        public boolean forceStatic(long guestOffset) {
            return false;
        }

        @Override
        public long slotToGuestOffset(int slot, boolean isStatic) {
            return ((long) slot) << 2;
        }

        @Override
        public boolean isAllowed(JavaVersion v) {
            // see CompactGuestFieldOffsetStrategy.isAllowed
            return v.java18OrEarlier() || v.java21OrLater();
        }

        @Override
        public String name() {
            return "graal";
        }
    }

    public static int getGuestFieldOffset(Field f, EspressoLanguage language) {
        return language.getGuestFieldOffsetStrategy().getGuestOffset(f);
    }

    static int guestOffsetToSlot(long guestOffset, EspressoLanguage language) {
        return language.getGuestFieldOffsetStrategy().guestOffsetToSlot(guestOffset);
    }

    static long slotToGuestOffset(int slot, boolean isStatic, EspressoLanguage language) {
        return language.getGuestFieldOffsetStrategy().slotToGuestOffset(slot, isStatic);
    }

    private static Field resolveUnsafeAccessField(StaticObject holder, long offset, Meta meta, EspressoLanguage language) {
        GuestFieldOffsetStrategy guestFieldOffsetStrategy = language.getGuestFieldOffsetStrategy();
        int slot = guestFieldOffsetStrategy.guestOffsetToSlot(offset);
        boolean forceStatic = guestFieldOffsetStrategy.forceStatic(offset);

        assert !StaticObject.isNull(holder);

        if (slot >= 1 << 16 || slot < (-ALLOWED_HIDDEN_FIELDS)) {
            // the field offset is not normalized
            return null;
        }
        Field field;
        try {
            if (forceStatic) {
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
                if (holder.isStaticStorage()) {
                    field = holder.getKlass().lookupStaticFieldTable(slot);
                } else {
                    field = holder.getKlass().lookupFieldTable(slot);
                }
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

    @TruffleBoundary
    static EspressoException throwNoField(Meta meta, StaticObject holder, long offset) {
        if (StaticObject.isNull(holder)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedOperationException, "No field at offset " + offset + " in null");
        } else if (holder.isStaticStorage()) {
            throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedOperationException, "No field at offset " + offset + " in static storage of " + holder.getKlass().getExternalName());
        } else {
            throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedOperationException, "No field at offset " + offset + " in instance of type " + holder.getKlass().getExternalName());
        }
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
     * @see GetByte
     * @see PutByte
     */
    @TruffleBoundary
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static long allocateMemory(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long length, @Inject Meta meta) {
        if (length < 0) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "requested size is negative");
        }
        @Buffer
        TruffleObject buffer = meta.getNativeAccess().allocateMemory(length);
        if (buffer == null && length > 0) {
            // malloc may return anything for 0-sized allocations.
            throw meta.throwExceptionWithMessage(meta.java_lang_OutOfMemoryError, "malloc returned NULL");
        }
        long ptr;
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
        if (newSize < 0) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "requested size is negative");
        }
        @Buffer
        TruffleObject result = meta.getNativeAccess().reallocateMemory(RawPointer.create(address), newSize);
        if (result == null) {
            throw meta.throwExceptionWithMessage(meta.java_lang_OutOfMemoryError, "realloc couldn't reallocate " + newSize + " bytes");
        }
        long newAddress;
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
     * (in effect) a <em>double-register</em> addressing mode, as discussed in {@link GetInt}. When
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
     * in a form usable by {@link GetInt}. Therefore, code which will be ported to such JVMs on
     * 64-bit platforms must preserve all bits of static field offsets.
     *
     * @see GetInt
     */
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static long staticFieldOffset(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(java.lang.reflect.Field.class) StaticObject fieldMirror,
                    @Inject Meta meta, @Inject EspressoLanguage language) {
        Field field = Field.getReflectiveFieldRoot(fieldMirror, meta);
        if (!field.isStatic()) {
            meta.throwIllegalArgumentExceptionBoundary();
        }
        return getGuestFieldOffset(field, language);
    }

    /**
     * Report the location of a given static field, in conjunction with {@link #staticFieldOffset}.
     * <p>
     * Fetch the base "Object", if any, with which static fields of the given class can be accessed
     * via methods like {@link GetInt}. This value may be null. This value may refer to an object
     * which is a "cookie", not guaranteed to be a real Object, and it should not be used in any way
     * except as argument to the get and put routines in this class.
     */
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeAppend0.class)
    public static @JavaType(Object.class) StaticObject staticFieldBase(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self,
                    @JavaType(java.lang.reflect.Field.class) StaticObject field,
                    @Inject Meta meta) {
        Field target = Field.getReflectiveFieldRoot(field, meta);
        if (!target.isStatic()) {
            meta.throwIllegalArgumentExceptionBoundary();
        }
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

    @Substitution(hasReceiver = true, flags = {IsTrivial})
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
                    @Inject SubstitutionProfiler location) {
        if (time < 0 || (isAbsolute && time == 0)) { // don't wait at all
            return;
        }

        EspressoContext context = meta.getContext();
        StaticObject thread = context.getCurrentPlatformThread();

        // Check return condition beforehand
        if (parkReturnCondition(thread, meta)) {
            return;
        }
        ThreadState state = time > 0 ? TIMED_PARKED : PARKED;
        Transition transition = Transition.transition(state, location);
        try {
            parkImpl(isAbsolute, time, thread, meta);
        } finally {
            transition.restore(location);
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
     * @param loadavg an array of double of size nelems
     * @param nelems the number of samples to be retrieved and must be 1 to 3.
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
    public static long objectFieldOffset1(@JavaType(Unsafe.class) StaticObject self, @JavaType(Class.class) StaticObject cl, @JavaType(String.class) StaticObject guestName,
                    @Inject Meta meta, @Inject EspressoLanguage language) {
        Klass k = cl.getMirrorKlass(meta);
        if (k instanceof ObjectKlass kl) {
            String hostName = meta.toHostString(guestName);
            Symbol<Name> name = meta.getNames().lookup(hostName);
            if (name != null) {
                for (Field f : kl.getFieldTable()) {
                    if (!f.isRemoved() && f.getName() == name) {
                        return getGuestFieldOffset(f, language);
                    }
                }
                for (Field f : kl.getStaticFieldTable()) {
                    if (!f.isRemoved() && f.getName() == name) {
                        return getGuestFieldOffset(f, language);
                    }
                }
            }
        }
        throw meta.throwException(meta.java_lang_InternalError);
    }

    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    static long knownObjectFieldOffset0(@SuppressWarnings("unused") StaticObject self, @JavaType(Class.class) StaticObject c, @JavaType(String.class) StaticObject guestName,
                    @Inject Meta meta, @Inject EspressoLanguage language) {
        // Error code -1 is not found, -2 is static field
        Klass k = c.getMirrorKlass(meta);
        if (!(k instanceof ObjectKlass kl)) {
            return -1;
        }
        String hostName = meta.toHostString(guestName);
        Symbol<Name> name = meta.getNames().lookup(hostName);
        if (name == null) {
            return -1;
        }
        for (Field f : kl.getFieldTable()) {
            if (!f.isRemoved() && f.getName() == name) {
                return getGuestFieldOffset(f, language);
            }
        }
        for (Field f : kl.getStaticFieldTable()) {
            if (!f.isRemoved() && f.getName() == name) {
                return -2;
            }
        }
        return -1;
    }

    // region UnsafeAccessors

    @GenerateInline
    @GenerateCached(false)
    abstract static class GetFieldFromIndexNode extends EspressoInlineNode {
        static final int LIMIT = 3;

        abstract Field execute(Node node, StaticObject holder, long slot);

        @Specialization(guards = {"slot == cachedSlot", "holder.isStaticStorage() == cachedIsStaticStorage", "holder.getKlass() == cachedKlass"}, limit = "LIMIT")
        static Field doCached(@SuppressWarnings("unused") StaticObject holder, @SuppressWarnings("unused") long slot,
                        @SuppressWarnings("unused") @Bind Node node,
                        @SuppressWarnings("unused") @Cached("slot") long cachedSlot,
                        @SuppressWarnings("unused") @Cached("holder.getKlass()") Klass cachedKlass,
                        @SuppressWarnings("unused") @Cached("holder.isStaticStorage()") boolean cachedIsStaticStorage,
                        @Cached("doGeneric(holder, slot, node)") Field cachedField) {
            return cachedField;
        }

        @Specialization(replaces = "doCached")
        static Field doGeneric(StaticObject holder, long slot,
                        @Bind Node node) {
            Meta meta = EspressoContext.get(node).getMeta();
            return resolveUnsafeAccessField(holder, slot, meta, EspressoLanguage.get(node));
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
     * @see GetByte
     */
    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    abstract static class PutByte extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, byte value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, byte value) {
            UnsafeAccess.getIfAllowed(getMeta()).putByte(address, value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    abstract static class PutChar extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, char value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, char value) {
            UnsafeAccess.getIfAllowed(getMeta()).putChar(address, value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    abstract static class PutShort extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, short value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, short value) {
            UnsafeAccess.getIfAllowed(getMeta()).putShort(address, value);
        }
    }

    /** @see GetByteWithBase */
    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    abstract static class PutInt extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, int value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, int value) {
            UnsafeAccess.getIfAllowed(getMeta()).putInt(address, value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    abstract static class PutFloat extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, float value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, float value) {
            UnsafeAccess.getIfAllowed(getMeta()).putFloat(address, value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    abstract static class PutDouble extends UnsafeAccessNode {

        abstract void execute(@JavaType(Unsafe.class) StaticObject self, long address, double value);

        @Specialization
        void doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address, double value) {
            UnsafeAccess.getIfAllowed(getMeta()).putDouble(address, value);
        }
    }

    @GenerateInline(false) // not available in substitutions
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
    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putByte")
    @InlineInBytecode
    public abstract static class PutByteWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, byte value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, byte value) {
            UnsafeAccess.getIfAllowed(getMeta()).putByte(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, byte value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, byte value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, byte value, Meta meta) {
            f.setByte(resolveUnsafeAccessHolder(f, holder, meta), value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeObjectAccessToReference.class, methodName = "putObject")
    @InlineInBytecode
    public abstract static class PutObjectWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value) {
            UnsafeAccess.getIfAllowed(getMeta()).putObject(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, StaticObject value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, StaticObject value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, StaticObject value, Meta meta) {
            f.setObject(resolveUnsafeAccessHolder(f, holder, meta), value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putBoolean")
    @InlineInBytecode
    public abstract static class PutBooleanWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, boolean value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, boolean value) {
            UnsafeAccess.getIfAllowed(getMeta()).putBoolean(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, boolean value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, boolean value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, boolean value, Meta meta) {
            f.setBoolean(resolveUnsafeAccessHolder(f, holder, meta), value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putChar")
    @InlineInBytecode
    public abstract static class PutCharWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, char value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, char value) {
            UnsafeAccess.getIfAllowed(getMeta()).putChar(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, char value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, char value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, char value, Meta meta) {
            f.setChar(resolveUnsafeAccessHolder(f, holder, meta), value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putShort")
    @InlineInBytecode
    public abstract static class PutShortWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, short value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, short value) {
            UnsafeAccess.getIfAllowed(getMeta()).putShort(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, short value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, short value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, short value, Meta meta) {
            f.setShort(resolveUnsafeAccessHolder(f, holder, meta), value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putInt")
    @InlineInBytecode
    public abstract static class PutIntWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value) {
            UnsafeAccess.getIfAllowed(getMeta()).putInt(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, int value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, int value, Meta meta) {
            f.setInt(resolveUnsafeAccessHolder(f, holder, meta), value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putFloat")
    @InlineInBytecode
    public abstract static class PutFloatWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, float value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, float value) {
            UnsafeAccess.getIfAllowed(getMeta()).putFloat(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, float value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, float value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, float value, Meta meta) {
            f.setFloat(resolveUnsafeAccessHolder(f, holder, meta), value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putDouble")
    @InlineInBytecode
    public abstract static class PutDoubleWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, double value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, double value) {
            UnsafeAccess.getIfAllowed(getMeta()).putDouble(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, double value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, double value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, double value, Meta meta) {
            f.setDouble(resolveUnsafeAccessHolder(f, holder, meta), value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putLong")
    @InlineInBytecode
    public abstract static class PutLongWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value) {
            UnsafeAccess.getIfAllowed(getMeta()).putLong(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, long value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, long value, Meta meta) {
            f.setLong(resolveUnsafeAccessHolder(f, holder, meta), value);
        }
    }

    // endregion put*(Object holder, long offset, * value)

    // region putOrdered*(Object holder, long offset, * value)

    // TODO: Volatile access is stronger than needed.

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    @InlineInBytecode
    public abstract static class PutOrderedInt extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value) {
            UnsafeAccess.getIfAllowed(getMeta()).putOrderedInt(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, int value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, int value, Meta meta) {
            f.setInt(resolveUnsafeAccessHolder(f, holder, meta), value, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    @InlineInBytecode
    public abstract static class PutOrderedLong extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value) {
            UnsafeAccess.getIfAllowed(getMeta()).putOrderedLong(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, long value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, long value, Meta meta) {
            f.setLong(resolveUnsafeAccessHolder(f, holder, meta), value, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    @InlineInBytecode
    public abstract static class PutOrderedObject extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value) {
            UnsafeAccess.getIfAllowed(getMeta()).putOrderedObject(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, StaticObject value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, StaticObject value, Meta meta) {
            f.setObject(resolveUnsafeAccessHolder(f, holder, meta), value, true);
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
    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getByte")
    @InlineInBytecode
    public abstract static class GetByteWithBase extends UnsafeAccessNode {
        abstract byte execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        byte doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getByte(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static byte doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static byte doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static byte doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Byte) {
                return f.getByte(resolveUnsafeAccessHolder(f, holder, meta));
            }
            return f.getAsByte(meta, resolveUnsafeAccessHolder(f, holder, meta), false);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeObjectAccessToReference.class, methodName = "getObject")
    @InlineInBytecode
    public abstract static class GetObjectWithBase extends UnsafeAccessNode {
        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        @JavaType(Object.class)
        StaticObject doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder,
                        long offset) {
            return (StaticObject) UnsafeAccess.getIfAllowed(getMeta()).getObject(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static @JavaType(Object.class) StaticObject doField(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGetField(holder, f, meta);
            } else {
                return doGetFieldSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static StaticObject doGetFieldSlow(StaticObject holder, Field f, Meta meta) {
            return doGetField(holder, f, meta);
        }

        private static StaticObject doGetField(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Object) {
                return f.getObject(resolveUnsafeAccessHolder(f, holder, meta));
            }
            return f.getAsObject(meta, resolveUnsafeAccessHolder(f, holder, meta));
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getBoolean")
    @InlineInBytecode
    public abstract static class GetBooleanWithBase extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        boolean doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getBoolean(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static boolean doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static boolean doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static boolean doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Boolean) {
                return f.getBoolean(resolveUnsafeAccessHolder(f, holder, meta));
            }
            return f.getAsBoolean(meta, resolveUnsafeAccessHolder(f, holder, meta), false);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getChar")
    @InlineInBytecode
    public abstract static class GetCharWithBase extends UnsafeAccessNode {
        abstract char execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        char doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getChar(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static char doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static char doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static char doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Char) {
                return f.getChar(resolveUnsafeAccessHolder(f, holder, meta));
            }
            return f.getAsChar(meta, resolveUnsafeAccessHolder(f, holder, meta), false);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getShort")
    @InlineInBytecode
    public abstract static class GetShortWithBase extends UnsafeAccessNode {
        abstract short execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        short doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getShort(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static short doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static short doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static short doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Short) {
                return f.getShort(resolveUnsafeAccessHolder(f, holder, meta));
            }
            return f.getAsShort(meta, resolveUnsafeAccessHolder(f, holder, meta), false);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getInt")
    @InlineInBytecode
    public abstract static class GetIntWithBase extends UnsafeAccessNode {
        abstract int execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        int doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getInt(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static int doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static int doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static int doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Int) {
                return f.getInt(resolveUnsafeAccessHolder(f, holder, meta));
            }
            return f.getAsInt(meta, resolveUnsafeAccessHolder(f, holder, meta), false);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getFloat")
    @InlineInBytecode
    public abstract static class GetFloatWithBase extends UnsafeAccessNode {
        abstract float execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        float doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getFloat(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static float doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static float doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static float doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Float) {
                return f.getFloat(resolveUnsafeAccessHolder(f, holder, meta));
            }
            return f.getAsFloat(meta, resolveUnsafeAccessHolder(f, holder, meta), false);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getDouble")
    @InlineInBytecode
    public abstract static class GetDoubleWithBase extends UnsafeAccessNode {
        abstract double execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        double doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getDouble(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static double doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static double doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static double doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Double) {
                return f.getDouble(resolveUnsafeAccessHolder(f, holder, meta));
            }
            return f.getAsDouble(meta, resolveUnsafeAccessHolder(f, holder, meta), false);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getLong")
    @InlineInBytecode
    public abstract static class GetLongWithBase extends UnsafeAccessNode {
        abstract long execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        long doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getLong(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static long doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static long doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static long doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Long) {
                return f.getLong(resolveUnsafeAccessHolder(f, holder, meta));
            }
            return f.getAsLong(meta, resolveUnsafeAccessHolder(f, holder, meta), false);
        }
    }

    // endregion get*(Object holder, long offset)

    // region get*Volatile(Object holder, long offset)

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getByteVolatile")
    @InlineInBytecode
    public abstract static class GetByteVolatileWithBase extends UnsafeAccessNode {
        abstract byte execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        byte doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getByteVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static byte doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static byte doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static byte doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Byte) {
                return f.getByte(resolveUnsafeAccessHolder(f, holder, meta), true);
            }
            return f.getAsByte(meta, resolveUnsafeAccessHolder(f, holder, meta), false, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeObjectAccessToReference.class, methodName = "getObjectVolatile")
    @InlineInBytecode
    public abstract static class GetObjectVolatileWithBase extends UnsafeAccessNode {
        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        @JavaType(Object.class)
        StaticObject doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder,
                        long offset) {
            return (StaticObject) UnsafeAccess.getIfAllowed(getMeta()).getObjectVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        @JavaType(Object.class)
        static StaticObject doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static StaticObject doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static StaticObject doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Object) {
                return f.getObject(resolveUnsafeAccessHolder(f, holder, meta), true);
            }
            return f.getAsObject(meta, resolveUnsafeAccessHolder(f, holder, meta), true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getBooleanVolatile")
    @InlineInBytecode
    public abstract static class GetBooleanVolatileWithBase extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        boolean doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getBooleanVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static boolean doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static boolean doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static boolean doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Boolean) {
                return f.getBoolean(resolveUnsafeAccessHolder(f, holder, meta), true);
            }
            return f.getAsBoolean(meta, resolveUnsafeAccessHolder(f, holder, meta), false, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getCharVolatile")
    @InlineInBytecode
    public abstract static class GetCharVolatileWithBase extends UnsafeAccessNode {
        abstract char execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        char doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getCharVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static char doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static char doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static char doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Char) {
                return f.getChar(resolveUnsafeAccessHolder(f, holder, meta), true);
            }
            return f.getAsChar(meta, resolveUnsafeAccessHolder(f, holder, meta), false, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getShortVolatile")
    @InlineInBytecode
    public abstract static class GetShortVolatileWithBase extends UnsafeAccessNode {
        abstract short execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        short doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getShortVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static short doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static short doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static short doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Short) {
                return f.getShort(resolveUnsafeAccessHolder(f, holder, meta), true);
            }
            return f.getAsShort(meta, resolveUnsafeAccessHolder(f, holder, meta), false, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getIntVolatile")
    @InlineInBytecode
    public abstract static class GetIntVolatileWithBase extends UnsafeAccessNode {
        abstract int execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        int doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getIntVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static int doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static int doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static int doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Int) {
                return f.getInt(resolveUnsafeAccessHolder(f, holder, meta), true);
            }
            return f.getAsInt(meta, resolveUnsafeAccessHolder(f, holder, meta), false, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getFloatVolatile")
    @InlineInBytecode
    public abstract static class GetFloatVolatileWithBase extends UnsafeAccessNode {
        abstract float execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        float doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getFloatVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static float doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static float doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static float doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Float) {
                return f.getFloat(resolveUnsafeAccessHolder(f, holder, meta), true);
            }
            return f.getAsFloat(meta, resolveUnsafeAccessHolder(f, holder, meta), false, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getDoubleVolatile")
    @InlineInBytecode
    public abstract static class GetDoubleVolatileWithBase extends UnsafeAccessNode {
        abstract double execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        double doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getDoubleVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static double doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static double doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static double doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Double) {
                return f.getDouble(resolveUnsafeAccessHolder(f, holder, meta), true);
            }
            return f.getAsDouble(meta, resolveUnsafeAccessHolder(f, holder, meta), false, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "getLongVolatile")
    @InlineInBytecode
    public abstract static class GetLongVolatileWithBase extends UnsafeAccessNode {
        abstract long execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset);

        @Specialization(guards = "isNullOrArray(holder)")
        long doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset) {
            return UnsafeAccess.getIfAllowed(getMeta()).getLongVolatile(unwrapNullOrArray(getLanguage(), holder), offset);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static long doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGet(holder, f, meta);
            } else {
                return doGetSlow(holder, f, meta);
            }
        }

        @TruffleBoundary
        private static long doGetSlow(StaticObject holder, Field f, Meta meta) {
            return doGet(holder, f, meta);
        }

        private static long doGet(StaticObject holder, Field f, Meta meta) {
            if (f.getKind() == JavaKind.Long) {
                return f.getLong(resolveUnsafeAccessHolder(f, holder, meta), true);
            }
            return f.getAsLong(meta, resolveUnsafeAccessHolder(f, holder, meta), false, true);
        }
    }

    // endregion get*Volatile(Object holder, long offset)

    // region get*(long offset)

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    abstract static class GetByte extends UnsafeAccessNode {

        abstract byte execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        byte doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getByte(address);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    abstract static class GetChar extends UnsafeAccessNode {

        abstract char execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        char doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getChar(address);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    abstract static class GetShort extends UnsafeAccessNode {

        abstract short execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        short doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getShort(address);
        }
    }

    /** @see GetByteWithBase */
    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    abstract static class GetInt extends UnsafeAccessNode {

        abstract int execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        int doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getInt(address);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    abstract static class GetFloat extends UnsafeAccessNode {

        abstract float execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        float doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getFloat(address);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    abstract static class GetDouble extends UnsafeAccessNode {

        abstract double execute(@JavaType(Unsafe.class) StaticObject self, long address);

        @Specialization
        double doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, long address) {
            return UnsafeAccess.getIfAllowed(getMeta()).getDouble(address);
        }
    }

    @GenerateInline(false) // not available in substitutions
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

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putByteVolatile")
    @InlineInBytecode
    public abstract static class PutByteVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, byte value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, byte value) {
            UnsafeAccess.getIfAllowed(getMeta()).putByteVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, byte value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, byte value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, byte value, Meta meta) {
            f.setByte(resolveUnsafeAccessHolder(f, holder, meta), value, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeObjectAccessToReference.class, methodName = "putObjectVolatile")
    @InlineInBytecode
    public abstract static class PutObjectVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value) {
            UnsafeAccess.getIfAllowed(getMeta()).putObjectVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, StaticObject value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, StaticObject value, Meta meta) {
            f.setObject(resolveUnsafeAccessHolder(f, holder, meta), value, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putBooleanVolatile")
    @InlineInBytecode
    public abstract static class PutBooleanVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, boolean value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, boolean value) {
            UnsafeAccess.getIfAllowed(getMeta()).putBooleanVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, boolean value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, boolean value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, boolean value, Meta meta) {
            f.setBoolean(resolveUnsafeAccessHolder(f, holder, meta), value, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putCharVolatile")
    @InlineInBytecode
    public abstract static class PutCharVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, char value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, char value) {
            UnsafeAccess.getIfAllowed(getMeta()).putCharVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, char value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, char value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, char value, Meta meta) {
            f.setChar(resolveUnsafeAccessHolder(f, holder, meta), value, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putShortVolatile")
    @InlineInBytecode
    public abstract static class PutShortVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, short value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, short value) {
            UnsafeAccess.getIfAllowed(getMeta()).putShortVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, short value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, short value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, short value, Meta meta) {
            f.setShort(resolveUnsafeAccessHolder(f, holder, meta), value, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putIntVolatile")
    @InlineInBytecode
    public abstract static class PutIntVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value) {
            UnsafeAccess.getIfAllowed(getMeta()).putIntVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, int value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, int value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, int value, Meta meta) {
            f.setInt(resolveUnsafeAccessHolder(f, holder, meta), value, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putFloatVolatile")
    @InlineInBytecode
    public abstract static class PutFloatVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, float value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, float value) {
            UnsafeAccess.getIfAllowed(getMeta()).putFloatVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, float value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, float value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, float value, Meta meta) {
            f.setFloat(resolveUnsafeAccessHolder(f, holder, meta), value, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putDoubleVolatile")
    @InlineInBytecode
    public abstract static class PutDoubleVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, double value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, double value) {
            UnsafeAccess.getIfAllowed(getMeta()).putDoubleVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, double value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, double value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, double value, Meta meta) {
            f.setDouble(resolveUnsafeAccessHolder(f, holder, meta), value, true);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, methodName = "putLongVolatile")
    @InlineInBytecode
    public abstract static class PutLongVolatileWithBase extends UnsafeAccessNode {
        abstract void execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value);

        @Specialization(guards = "isNullOrArray(holder)")
        void doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value) {
            UnsafeAccess.getIfAllowed(getMeta()).putLongVolatile(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static void doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset, long value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                doPut(f, holder, value, meta);
            } else {
                doPutSlow(f, holder, value, meta);
            }
        }

        @TruffleBoundary
        private static void doPutSlow(Field f, StaticObject holder, long value, Meta meta) {
            doPut(f, holder, value, meta);
        }

        private static void doPut(Field f, StaticObject holder, long value, Meta meta) {
            f.setLong(resolveUnsafeAccessHolder(f, holder, meta), value, true);
        }
    }

    // endregion put*Volatile(Object holder, long offset)

    // region compareAndSwap*

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    @InlineInBytecode
    public abstract static class CompareAndSwapObject extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after);

        @Specialization(guards = "isNullOrArray(holder)")
        boolean doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after) {
            return UnsafeAccess.getIfAllowed(getMeta()).compareAndSwapObject(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static boolean doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doCAS(f, holder, before, after, meta);
            } else {
                return doCASSlow(f, holder, before, after, meta);
            }
        }

        @TruffleBoundary
        private static boolean doCASSlow(Field f, StaticObject holder, StaticObject before, StaticObject after, Meta meta) {
            return doCAS(f, holder, before, after, meta);
        }

        private static boolean doCAS(Field f, StaticObject holder, StaticObject before, StaticObject after, Meta meta) {
            return f.compareAndSwapObject(resolveUnsafeAccessHolder(f, holder, meta), before, after);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    @InlineInBytecode
    public abstract static class CompareAndSwapInt extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after);

        @Specialization(guards = "isNullOrArray(holder)")
        boolean doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after) {
            return UnsafeAccess.getIfAllowed(getMeta()).compareAndSwapInt(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static boolean doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doCAS(f, holder, before, after, meta);
            } else {
                return doCASSlow(f, holder, before, after, meta);
            }
        }

        @TruffleBoundary
        private static boolean doCASSlow(Field f, StaticObject holder, int before, int after, Meta meta) {
            return doCAS(f, holder, before, after, meta);
        }

        private static boolean doCAS(Field f, StaticObject holder, int before, int after, Meta meta) {
            switch (f.getKind()) {
                case Int:
                    return f.compareAndSwapInt(resolveUnsafeAccessHolder(f, holder, meta), before, after);
                case Float:
                    return f.compareAndSwapFloat(resolveUnsafeAccessHolder(f, holder, meta), Float.intBitsToFloat(before), Float.intBitsToFloat(after));
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    @InlineInBytecode
    public abstract static class CompareAndSwapLong extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after);

        @Specialization(guards = "isNullOrArray(holder)")
        boolean doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after) {
            return UnsafeAccess.getIfAllowed(getMeta()).compareAndSwapLong(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static boolean doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doCAS(f, holder, before, after, meta);
            } else {
                return doCASSlow(f, holder, before, after, meta);
            }
        }

        @TruffleBoundary
        private static boolean doCASSlow(Field f, StaticObject holder, long before, long after, Meta meta) {
            return doCAS(f, holder, before, after, meta);
        }

        private static boolean doCAS(Field f, StaticObject holder, long before, long after, Meta meta) {
            switch (f.getKind()) {
                case Long:
                    return f.compareAndSwapLong(resolveUnsafeAccessHolder(f, holder, meta), before, after);
                case Double:
                    return f.compareAndSwapDouble(resolveUnsafeAccessHolder(f, holder, meta), Double.longBitsToDouble(before), Double.longBitsToDouble(after));
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

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeObjectAccessToReference.class)
    @InlineInBytecode
    public abstract static class CompareAndExchangeObject extends UnsafeAccessNode {
        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after);

        @Specialization(guards = "isNullOrArray(holder)")
        @JavaType(Object.class)
        StaticObject doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder,
                        long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after) {
            UnsafeAccess.checkAllowed(getMeta());
            return (StaticObject) UnsafeSupport.compareAndExchangeObject(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        @JavaType(Object.class)
        static StaticObject doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doCAE(f, holder, before, after, meta);
            } else {
                return doCAESlow(f, holder, before, after, meta);
            }
        }

        @TruffleBoundary
        private static StaticObject doCAESlow(Field f, StaticObject holder, StaticObject before, StaticObject after, Meta meta) {
            return doCAE(f, holder, before, after, meta);
        }

        private static StaticObject doCAE(Field f, StaticObject holder, StaticObject before, StaticObject after, Meta meta) {
            if (f.getKind() != JavaKind.Object) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
            }
            return f.compareAndExchangeObject(resolveUnsafeAccessHolder(f, holder, meta), before, after);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    @InlineInBytecode
    public abstract static class CompareAndExchangeInt extends UnsafeAccessNode {
        abstract int execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after);

        @Specialization(guards = "isNullOrArray(holder)")
        int doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after) {
            UnsafeAccess.checkAllowed(getMeta());
            return UnsafeSupport.compareAndExchangeInt(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static int doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doCAE(f, holder, before, after, meta);
            } else {
                return doCAESlow(f, holder, before, after, meta);
            }
        }

        @TruffleBoundary
        private static int doCAESlow(Field f, StaticObject holder, int before, int after, Meta meta) {
            return doCAE(f, holder, before, after, meta);
        }

        private static int doCAE(Field f, StaticObject holder, int before, int after, Meta meta) {
            switch (f.getKind()) {
                case Int:
                    return f.compareAndExchangeInt(resolveUnsafeAccessHolder(f, holder, meta), before, after);
                case Float:
                    return Float.floatToRawIntBits(f.compareAndExchangeFloat(resolveUnsafeAccessHolder(f, holder, meta), Float.intBitsToFloat(before), Float.intBitsToFloat(after)));
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    public abstract static class CompareAndExchangeByte extends UnsafeAccessNode {
        abstract byte execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        byte before, byte after);

        @Specialization(guards = "isNullOrArray(holder)")
        byte doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        byte before, byte after) {
            UnsafeAccess.checkAllowed(getMeta());
            return UnsafeSupport.compareAndExchangeByte(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static byte doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        byte before, byte after,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doCAE(f, holder, before, after, meta);
            } else {
                return doCAESlow(f, holder, before, after, meta);
            }
        }

        @TruffleBoundary
        private static byte doCAESlow(Field f, StaticObject holder, byte before, byte after, Meta meta) {
            return doCAE(f, holder, before, after, meta);
        }

        private static byte doCAE(Field f, StaticObject holder, byte before, byte after, Meta meta) {
            switch (f.getKind()) {
                case Boolean:
                    return f.compareAndExchangeBoolean(resolveUnsafeAccessHolder(f, holder, meta), before != 0, after != 0) ? (byte) 1 : (byte) 0;
                case Byte:
                    return f.compareAndExchangeByte(resolveUnsafeAccessHolder(f, holder, meta), before, after);
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    public abstract static class CompareAndExchangeShort extends UnsafeAccessNode {
        abstract short execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        short before, short after);

        @Specialization(guards = "isNullOrArray(holder)")
        short doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        short before, short after) {
            UnsafeAccess.checkAllowed(getMeta());
            return UnsafeSupport.compareAndExchangeShort(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static short doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        short before, short after,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doCAE(f, holder, before, after, meta);
            } else {
                return doCAESlow(f, holder, before, after, meta);
            }
        }

        @TruffleBoundary
        private static short doCAESlow(Field f, StaticObject holder, short before, short after, Meta meta) {
            return doCAE(f, holder, before, after, meta);
        }

        private static short doCAE(Field f, StaticObject holder, short before, short after, Meta meta) {
            switch (f.getKind()) {
                case Short:
                    return f.compareAndExchangeShort(resolveUnsafeAccessHolder(f, holder, meta), before, after);
                case Char:
                    return (short) f.compareAndExchangeChar(resolveUnsafeAccessHolder(f, holder, meta), (char) before, (char) after);
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, nameProvider = Unsafe11.class)
    public abstract static class CompareAndExchangeLong extends UnsafeAccessNode {
        abstract long execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after);

        @Specialization(guards = "isNullOrArray(holder)")
        long doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after) {
            UnsafeAccess.checkAllowed(getMeta());
            return UnsafeSupport.compareAndExchangeLong(unwrapNullOrArray(getLanguage(), holder), offset, before, after);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        static long doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doCAE(f, holder, before, after, meta);
            } else {
                return doCAESlow(f, holder, before, after, meta);
            }
        }

        @TruffleBoundary
        private static long doCAESlow(Field f, StaticObject holder, long before, long after, Meta meta) {
            return doCAE(f, holder, before, after, meta);
        }

        private static long doCAE(Field f, StaticObject holder, long before, long after, Meta meta) {
            switch (f.getKind()) {
                case Long:
                    return f.compareAndExchangeLong(resolveUnsafeAccessHolder(f, holder, meta), before, after);
                case Double:
                    return Double.doubleToRawLongBits(f.compareAndExchangeDouble(resolveUnsafeAccessHolder(f, holder, meta), Double.longBitsToDouble(before), Double.longBitsToDouble(after)));
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
        }
    }

    // endregion compareAndExchange*

    // region CompareAndSet*

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, nameProvider = Unsafe8.class)
    @InlineInBytecode
    public abstract static class GetAndSetObject extends UnsafeAccessNode {
        abstract @JavaType(Unsafe.class) StaticObject execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value);

        @Specialization(guards = "isNullOrArray(holder)")
        @JavaType(Unsafe.class)
        StaticObject doNullOrArray(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder,
                        long offset,
                        @JavaType(Object.class) StaticObject value) {
            return (StaticObject) UnsafeAccess.getIfAllowed(getMeta()).getAndSetObject(unwrapNullOrArray(getLanguage(), holder), offset, value);
        }

        @Specialization(guards = "!isNullOrArray(holder)")
        @JavaType(Unsafe.class)
        static StaticObject doGeneric(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject value,
                        @Bind Node node,
                        @Cached GetFieldFromIndexNode getField,
                        @Cached InlinedBranchProfile noField) {
            Field f = getField.execute(node, holder, offset);
            Meta meta = EspressoContext.get(node).getMeta();
            if (f == null) {
                noField.enter(node);
                throw throwNoField(meta, holder, offset);
            }
            if (CompilerDirectives.isPartialEvaluationConstant(f)) {
                return doGetAndSet(f, holder, value, meta);
            } else {
                return doGetAndSetSlow(f, holder, value, meta);
            }
        }

        private static StaticObject doGetAndSetSlow(Field f, StaticObject holder, StaticObject value, Meta meta) {
            return doGetAndSet(f, holder, value, meta);
        }

        private static StaticObject doGetAndSet(Field f, StaticObject holder, StaticObject value, Meta meta) {
            return f.getAndSetObject(resolveUnsafeAccessHolder(f, holder, meta), value);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true, nameProvider = SharedUnsafeObjectAccessToReference.class)
    @InlineInBytecode
    abstract static class CompareAndSetObject extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after);

        @Specialization
        static boolean doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        @JavaType(Object.class) StaticObject before, @JavaType(Object.class) StaticObject after,
                        @Cached CompareAndSwapObject cas) {
            return cas.execute(self, holder, offset, before, after);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    @InlineInBytecode
    abstract static class CompareAndSetInt extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after);

        @Specialization
        static boolean doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        int before, int after,
                        @Cached CompareAndSwapInt cas) {
            return cas.execute(self, holder, offset, before, after);
        }
    }

    @GenerateInline(false) // not available in substitutions
    @Substitution(hasReceiver = true)
    @InlineInBytecode
    abstract static class CompareAndSetLong extends UnsafeAccessNode {
        abstract boolean execute(@JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after);

        @Specialization
        static boolean doCached(@SuppressWarnings("unused") @JavaType(Unsafe.class) StaticObject self, @JavaType(Object.class) StaticObject holder, long offset,
                        long before, long after,
                        @Cached CompareAndSwapLong cas) {
            return cas.execute(self, holder, offset, before, after);
        }
    }

    // endregion CompareAndSet*

    // endregion UnsafeAccessors

    public static class SharedUnsafe extends SubstitutionNamesProvider {
        private static final String[] NAMES = {
                        SUN_MISC_UNSAFE,
                        JDK_INTERNAL_MISC_UNSAFE
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
        private static final String[] NAMES = {
                        SUN_MISC_UNSAFE,
                        JDK_INTERNAL_MISC_UNSAFE,
                        JDK_INTERNAL_MISC_UNSAFE
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
        private static final String[] NAMES = {
                        SUN_MISC_UNSAFE
        };
        public static SubstitutionNamesProvider INSTANCE = new Unsafe8();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }
    }

    public static class Unsafe11 extends SubstitutionNamesProvider {
        private static final String[] NAMES = {
                        JDK_INTERNAL_MISC_UNSAFE
        };
        public static SubstitutionNamesProvider INSTANCE = new Unsafe11();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }
    }
}
