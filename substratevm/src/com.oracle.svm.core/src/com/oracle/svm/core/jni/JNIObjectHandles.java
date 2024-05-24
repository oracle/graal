/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.jni;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.handles.ObjectHandlesImpl;
import com.oracle.svm.core.handles.ThreadLocalHandles;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.jni.headers.JNIObjectRefType;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.word.Word;

/**
 * Centralized management of {@linkplain JNIObjectHandle JNI handles for Java objects}. There are
 * different kinds of JNI handles and the main purpose of this class is to abstract their
 * differences when working with handles. In order to do that efficiently, we partition the value
 * range of the word-sized handles into subranges for the different kinds:
 * <ul>
 * <li>Local handles as defined in the JNI specification can be created and released implicitly or
 * explicitly and are valid only in the current thread.</li>
 * <li>Global handles as defined in the JNI specification must be created explicitly and are valid
 * in the entire isolate until they are explicitly released. Individual global handles can be weak,
 * in which case they don't prevent garbage collection of their target object. See:
 * {@link JNIGlobalHandles}.</li>
 * <li>Image heap handles refer to objects in the image heap. This handle type is specific to our
 * implementation. Handles can be either local, global or weak-global according to the JNI
 * specification, so they need some special treatment. See: {@link JNIImageHeapHandles}.</li>
 * </ul>
 */
public final class JNIObjectHandles {
    @Fold
    static boolean haveAssertions() {
        return RuntimeAssertionsSupport.singleton().desiredAssertionStatus(JNIObjectHandles.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends SignedWord> T nullHandle() {
        return ThreadLocalHandles.nullHandle();
    }

    /**
     * Minimum available local handles according to specification: "Before it enters a native
     * method, the VM automatically ensures that at least 16 local references can be created".
     */
    static final int NATIVE_CALL_MIN_LOCAL_HANDLE_CAPACITY = 16;

    @SuppressWarnings("rawtypes") private static final FastThreadLocalObject<ThreadLocalHandles> handles //
                    = FastThreadLocalFactory.createObject(ThreadLocalHandles.class, "JNIObjectHandles.handles");

    @Fold
    static boolean useImageHeapHandles() {
        /*
         * Image heap handles are intended for JNI code that is unaware of isolates. Without isolate
         * support, this type of handle is not needed. Also, when isolate support is turned off, the
         * heap is written into the data/rodata sections instead of having a contiguous section,
         * which would require a different approach.
         */
        return SubstrateOptions.SpawnIsolates.getValue();
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocalHandles<ObjectHandle> getOrCreateLocals() {
        ThreadLocalHandles<ObjectHandle> result = handles.get();
        if (result == null) {
            result = createLocals();
        }
        return result;
    }

    @NeverInline("slow path that is executed once per thread; do not bloat machine code by inlining the allocations")
    private static ThreadLocalHandles<ObjectHandle> createLocals() {
        ThreadLocalHandles<ObjectHandle> result = new ThreadLocalHandles<>(NATIVE_CALL_MIN_LOCAL_HANDLE_CAPACITY);
        handles.set(result);
        return result;
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static ThreadLocalHandles<ObjectHandle> getExistingLocals() {
        return handles.get();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isInLocalRange(JNIObjectHandle handle) {
        return ThreadLocalHandles.isInRange((ObjectHandle) handle);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static ObjectHandle decodeLocal(JNIObjectHandle handle) {
        return (ObjectHandle) handle;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static JNIObjectHandle encodeLocal(ObjectHandle handle) {
        return (JNIObjectHandle) handle;
    }

    /**
     * Returns the Java object that is referenced by the given handle. Note that this method may
     * execute interruptible code.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> T getObject(JNIObjectHandle handle) {
        if (handle.equal(nullHandle())) {
            return null;
        }
        if (isInLocalRange(handle)) {
            return getExistingLocals().getObject(decodeLocal(handle));
        }
        if (useImageHeapHandles() && JNIImageHeapHandles.isInRange(handle)) {
            return JNIImageHeapHandles.getObject(handle);
        }
        return getObjectSlowInterruptibly(handle);
    }

    @Uninterruptible(reason = "Not really, but our caller is to allow inlining and we must be safe at this point.", calleeMustBe = false)
    private static <T> T getObjectSlowInterruptibly(JNIObjectHandle handle) {
        return getObjectSlowInterruptibly0(handle);
    }

    private static <T> T getObjectSlowInterruptibly0(JNIObjectHandle handle) {
        if (JNIGlobalHandles.isInRange(handle)) {
            return JNIGlobalHandles.getObject(handle);
        }
        throw throwIllegalArgumentException();
    }

    @NeverInline("Exception slow path")
    private static IllegalArgumentException throwIllegalArgumentException() {
        throw new IllegalArgumentException("Invalid object handle");
    }

    public static JNIObjectRefType getHandleType(JNIObjectHandle handle) {
        if (isInLocalRange(handle)) {
            return JNIObjectRefType.Local;
        }
        if (useImageHeapHandles() && JNIImageHeapHandles.isInRange(handle)) {
            return JNIImageHeapHandles.getHandleType(handle);
        }
        if (JNIGlobalHandles.isInRange(handle)) {
            return JNIGlobalHandles.getHandleType(handle);
        }
        return JNIObjectRefType.Invalid; // intentionally includes the null handle
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static JNIObjectHandle createLocal(Object obj) {
        if (obj == null) {
            return JNIObjectHandles.nullHandle();
        }
        if (useImageHeapHandles() && JNIImageHeapHandles.isInImageHeap(obj)) {
            return JNIImageHeapHandles.asLocal(obj);
        }
        ThreadLocalHandles<ObjectHandle> locals = getExistingLocals();
        if (BranchProbabilityNode.probability(BranchProbabilityNode.VERY_FAST_PATH_PROBABILITY, locals != null)) {
            ObjectHandle handle = locals.tryCreateNonNull(obj);
            if (BranchProbabilityNode.probability(BranchProbabilityNode.FAST_PATH_PROBABILITY, handle.notEqual(nullHandle()))) {
                return encodeLocal(handle);
            }
        }
        return createLocalSlow(obj);
    }

    @Uninterruptible(reason = "Not really, but our caller is uninterruptible for inlining and we must be safe at this point.", calleeMustBe = false)
    private static JNIObjectHandle createLocalSlow(Object obj) {
        return createLocalSlow0(obj);
    }

    private static JNIObjectHandle createLocalSlow0(Object obj) {
        return encodeLocal(getOrCreateLocals().create(obj));
    }

    public static JNIObjectHandle newLocalRef(JNIObjectHandle ref) {
        if (useImageHeapHandles() && JNIImageHeapHandles.isInRange(ref)) {
            return JNIImageHeapHandles.toLocal(ref);
        }
        return encodeLocal(getOrCreateLocals().create(getObject(ref)));
    }

    public static void deleteLocalRef(JNIObjectHandle localRef) {
        if (!useImageHeapHandles() || !JNIImageHeapHandles.isInRange(localRef)) {
            getOrCreateLocals().delete(decodeLocal(localRef));
        }
    }

    public static int pushLocalFrame(int capacity) {
        return getOrCreateLocals().pushFrame(capacity);
    }

    public static void popLocalFrame() {
        getExistingLocals().popFrame();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void popLocalFramesIncluding(int frame) {
        getExistingLocals().popFramesIncluding(frame);
    }

    public static void ensureLocalCapacity(int capacity) {
        getOrCreateLocals().ensureCapacity(capacity);
    }

    public static JNIObjectHandle newGlobalRef(JNIObjectHandle handle) {
        JNIObjectHandle result = nullHandle();
        if (useImageHeapHandles() && JNIImageHeapHandles.isInRange(handle)) {
            result = JNIImageHeapHandles.toGlobal(handle);
        } else {
            Object obj = getObject(handle);
            if (obj != null) {
                result = JNIGlobalHandles.create(obj);
            }
        }
        return result;
    }

    public static void deleteGlobalRef(JNIObjectHandle globalRef) {
        if (!useImageHeapHandles() || !JNIImageHeapHandles.isInRange(globalRef)) {
            JNIGlobalHandles.destroy(globalRef);
        }
    }

    public static JNIObjectHandle newWeakGlobalRef(JNIObjectHandle handle) {
        JNIObjectHandle result = nullHandle();
        if (useImageHeapHandles() && JNIImageHeapHandles.isInRange(handle)) {
            result = JNIImageHeapHandles.toWeakGlobal(handle);
        } else {
            Object obj = getObject(handle);
            if (obj != null) {
                result = JNIGlobalHandles.createWeak(obj);
            }
        }
        return result;
    }

    public static void deleteWeakGlobalRef(JNIObjectHandle weakRef) {
        if (!useImageHeapHandles() || !JNIImageHeapHandles.isInRange(weakRef)) {
            JNIGlobalHandles.destroyWeak(weakRef);
        }
    }

    static int getLocalHandleCount() {
        ThreadLocalHandles<ObjectHandle> locals = getExistingLocals();
        return locals == null ? 0 : locals.getHandleCount();
    }

    static long computeCurrentGlobalHandleCount() {
        return JNIGlobalHandles.computeCurrentCount();
    }
}

/**
 * Manages JNI global handles, which must be explicitly created and can be accessed in all threads
 * of an isolate until they are explicitly deleted. These handles have a most significant bit of 1,
 * i.e. they are negative as signed integers. When assertions are enabled, we encode a hash of the
 * current {@link Isolate} to detect when global handles are incorrectly passed between isolates,
 * for example by native code that is unaware of isolates.
 */
final class JNIGlobalHandles {
    static final SignedWord MIN_VALUE = WordFactory.signed(Long.MIN_VALUE);
    static final SignedWord MAX_VALUE = JNIObjectHandles.nullHandle().subtract(1);
    static {
        assert JNIObjectHandles.nullHandle().equal(WordFactory.zero());
    }

    private static final int HANDLE_BITS_COUNT = 31;
    private static final SignedWord HANDLE_BITS_MASK = WordFactory.signed((1L << HANDLE_BITS_COUNT) - 1);
    private static final int VALIDATION_BITS_SHIFT = HANDLE_BITS_COUNT;
    private static final int VALIDATION_BITS_COUNT = 32;
    private static final SignedWord VALIDATION_BITS_MASK = WordFactory.signed((1L << VALIDATION_BITS_COUNT) - 1).shiftLeft(VALIDATION_BITS_SHIFT);
    private static final SignedWord MSB = WordFactory.signed(1L << 63);
    private static final ObjectHandlesImpl globalHandles = new ObjectHandlesImpl(JNIObjectHandles.nullHandle().add(1), HANDLE_BITS_MASK, JNIObjectHandles.nullHandle());

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean isInRange(JNIObjectHandle handle) {
        return MIN_VALUE.lessOrEqual((SignedWord) handle) && MAX_VALUE.greaterThan((SignedWord) handle);
    }

    private static Word isolateHash() {
        int isolateHash = Long.hashCode(CurrentIsolate.getIsolate().rawValue());
        return WordFactory.unsigned(isolateHash);
    }

    private static JNIObjectHandle encode(ObjectHandle handle) {
        SignedWord h = (Word) handle;
        if (JNIObjectHandles.haveAssertions()) {
            assert h.and(HANDLE_BITS_MASK).equal(h) : "unencoded handle must fit in range";
            Word v = isolateHash().shiftLeft(VALIDATION_BITS_SHIFT);
            assert v.and(VALIDATION_BITS_MASK).equal(v) : "validation value must fit in its range";
            h = h.or(v);
        }
        h = h.or(MSB);
        assert isInRange((JNIObjectHandle) h);
        return (JNIObjectHandle) h;
    }

    private static ObjectHandle decode(JNIObjectHandle handle) {
        assert isInRange(handle);
        assert ((Word) handle).and(VALIDATION_BITS_MASK).unsignedShiftRight(VALIDATION_BITS_SHIFT)
                        .equal(isolateHash()) : "mismatching validation value -- passed a handle from a different isolate?";
        return (ObjectHandle) HANDLE_BITS_MASK.and((Word) handle);
    }

    static <T> T getObject(JNIObjectHandle handle) {
        return globalHandles.get(decode(handle));
    }

    static JNIObjectRefType getHandleType(JNIObjectHandle handle) {
        assert isInRange(handle);
        if (globalHandles.isWeak(decode(handle))) {
            return JNIObjectRefType.WeakGlobal;
        }
        return JNIObjectRefType.Global;
    }

    static JNIObjectHandle create(Object obj) {
        return encode(globalHandles.create(obj));
    }

    static void destroy(JNIObjectHandle handle) {
        globalHandles.destroy(decode(handle));
    }

    static JNIObjectHandle createWeak(Object obj) {
        return encode(globalHandles.createWeak(obj));
    }

    static void destroyWeak(JNIObjectHandle weakRef) {
        globalHandles.destroyWeak(decode(weakRef));
    }

    public static long computeCurrentCount() {
        return globalHandles.computeCurrentCount();
    }
}

/**
 * Support for isolate-independent JNI handles for objects in the image heap. These objects never
 * move and are not garbage-collected, so the handle just contains an object's offset in the image
 * heap. This approach has the major benefit that handles are valid across isolates that are created
 * from the same image, which helps with native code that is unaware of multiple isolates.
 *
 * Although this type of handle doesn't need explicit management, we still distinguish between
 * local, global and weak-global references by means of a bit pattern in order to comply with the
 * JNI specification (in particular, for function {@code GetObjectRefType}).
 */
final class JNIImageHeapHandles {
    private static final int OBJ_OFFSET_BITS_COUNT = 32;
    private static final Word OBJ_OFFSET_BITS_MASK = WordFactory.unsigned((1L << OBJ_OFFSET_BITS_COUNT) - 1);
    private static final SignedWord LOCAL_RANGE_MIN = WordFactory.signed(0b01).shiftLeft(OBJ_OFFSET_BITS_COUNT);
    private static final SignedWord GLOBAL_RANGE_MIN = WordFactory.signed(0b10).shiftLeft(OBJ_OFFSET_BITS_COUNT);
    private static final SignedWord WEAK_GLOBAL_RANGE_MIN = WordFactory.signed(0b11).shiftLeft(OBJ_OFFSET_BITS_COUNT);
    private static final SignedWord ENTIRE_RANGE_MIN = LOCAL_RANGE_MIN;
    private static final SignedWord ENTIRE_RANGE_MAX = WEAK_GLOBAL_RANGE_MIN.add(OBJ_OFFSET_BITS_MASK);

    static { // no overlaps with the regular JNI handle ranges
        assert ENTIRE_RANGE_MAX.lessThan(ThreadLocalHandles.MIN_VALUE) || ENTIRE_RANGE_MIN.greaterThan(ThreadLocalHandles.MAX_VALUE);
        assert ENTIRE_RANGE_MAX.lessThan(JNIGlobalHandles.MIN_VALUE) || ENTIRE_RANGE_MIN.greaterThan(JNIGlobalHandles.MAX_VALUE);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean isInImageHeap(Object target) {
        return target != null && Heap.getHeap().isInImageHeap(target);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean isInRange(JNIObjectHandle handle) {
        SignedWord handleValue = (SignedWord) handle;
        return handleValue.greaterOrEqual(ENTIRE_RANGE_MIN) && handleValue.lessOrEqual(ENTIRE_RANGE_MAX);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static JNIObjectHandle asLocal(Object target) {
        assert isInImageHeap(target);
        SignedWord base = (SignedWord) Isolates.getHeapBase(CurrentIsolate.getIsolate());
        SignedWord offset = Word.objectToUntrackedPointer(target).subtract(base);
        // NOTE: we could support further bits due to the object alignment in the image heap
        assert offset.and(OBJ_OFFSET_BITS_MASK).equal(offset) : "does not fit in range";
        return (JNIObjectHandle) LOCAL_RANGE_MIN.add(offset);
    }

    static JNIObjectHandle toLocal(JNIObjectHandle handle) {
        return toRange(handle, LOCAL_RANGE_MIN);
    }

    static JNIObjectHandle toGlobal(JNIObjectHandle handle) {
        return toRange(handle, GLOBAL_RANGE_MIN);
    }

    static JNIObjectHandle toWeakGlobal(JNIObjectHandle handle) {
        return toRange(handle, WEAK_GLOBAL_RANGE_MIN);
    }

    private static JNIObjectHandle toRange(JNIObjectHandle handle, SignedWord rangeMin) {
        assert isInRange(handle) : "assert isInRange(handle) failed";
        return (JNIObjectHandle) ((SignedWord) handle).and(OBJ_OFFSET_BITS_MASK).add(rangeMin);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T> T getObject(JNIObjectHandle handle) {
        assert isInRange(handle);
        UnsignedWord base = (UnsignedWord) Isolates.getHeapBase(CurrentIsolate.getIsolate());
        Pointer offset = ((Pointer) handle).and(OBJ_OFFSET_BITS_MASK).add(base);
        @SuppressWarnings("unchecked")
        T obj = (T) offset.toObjectNonNull();
        assert isInImageHeap(obj);
        return obj;
    }

    static JNIObjectRefType getHandleType(JNIObjectHandle handle) {
        assert isInRange(handle);
        SignedWord handleValue = (SignedWord) handle;
        if (handleValue.greaterOrEqual(WEAK_GLOBAL_RANGE_MIN)) {
            return JNIObjectRefType.WeakGlobal;
        } else if (handleValue.greaterOrEqual(GLOBAL_RANGE_MIN)) {
            return JNIObjectRefType.Global;
        } else if (handleValue.greaterOrEqual(LOCAL_RANGE_MIN)) {
            return JNIObjectRefType.Local;
        }
        return JNIObjectRefType.Invalid;
    }
}
