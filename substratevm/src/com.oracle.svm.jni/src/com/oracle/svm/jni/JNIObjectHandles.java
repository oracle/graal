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
package com.oracle.svm.jni;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.handles.ObjectHandlesImpl;
import com.oracle.svm.core.handles.ObjectHandlesTestingBackdoor;
import com.oracle.svm.core.handles.ThreadLocalHandles;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIObjectRefType;

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
 * in which case they don't prevent garbage collection of their target object.</li>
 * <li>Image heap handles refer to objects in the image heap. This handle type is specific to our
 * implementation. Handles can be either local, global or weak-global according to the JNI
 * specification, so they need some special treatment. See: {@link JNIImageHeapHandles}.</li>
 * </ul>
 */
public final class JNIObjectHandles {
    public static <T extends SignedWord> T nullHandle() {
        return ThreadLocalHandles.nullHandle();
    }

    static final SignedWord GLOBAL_HANDLES_MIN = WordFactory.signed(Long.MIN_VALUE);
    static final SignedWord GLOBAL_HANDLES_MAX = nullHandle().subtract(1);
    private static final ObjectHandlesImpl globalHandles = new ObjectHandlesImpl(GLOBAL_HANDLES_MIN, GLOBAL_HANDLES_MAX, nullHandle());

    /**
     * Minimum available local handles according to specification: "Before it enters a native
     * method, the VM automatically ensures that at least 16 local references can be created".
     */
    static final int NATIVE_CALL_MIN_LOCAL_HANDLE_CAPACITY = 16;

    @SuppressWarnings("rawtypes") private static final FastThreadLocalObject<ThreadLocalHandles> handles //
                    = FastThreadLocalFactory.createObject(ThreadLocalHandles.class);

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
    private static ThreadLocalHandles<JNIObjectHandle> getLocals() {
        if (handles.get() == null) {
            handles.set(new ThreadLocalHandles<JNIObjectHandle>(NATIVE_CALL_MIN_LOCAL_HANDLE_CAPACITY));
        }
        return handles.get();
    }

    public static <T> T getObject(JNIObjectHandle handle) {
        if (handle.equal(nullHandle())) {
            return null;
        }
        if (ThreadLocalHandles.isInRange(handle)) {
            return getLocals().getObject(handle);
        }
        if (useImageHeapHandles() && JNIImageHeapHandles.isInRange(handle)) {
            return JNIImageHeapHandles.getObject(handle);
        }
        if (globalHandles.isInRange(handle)) {
            return globalHandles.get(handle);
        }
        throw new RuntimeException("Invalid object handle");
    }

    public static JNIObjectRefType getHandleType(JNIObjectHandle handle) {
        if (ThreadLocalHandles.isInRange(handle)) {
            return JNIObjectRefType.Local;
        }
        if (useImageHeapHandles() && JNIImageHeapHandles.isInRange(handle)) {
            return JNIImageHeapHandles.getHandleType(handle);
        }
        if (globalHandles.isInRange(handle)) {
            if (globalHandles.isWeak(handle)) {
                return JNIObjectRefType.WeakGlobal;
            }
            return JNIObjectRefType.Global;
        }
        return JNIObjectRefType.Invalid; // intentionally includes the null handle
    }

    public static JNIObjectHandle createLocal(Object obj) {
        if (useImageHeapHandles() && JNIImageHeapHandles.isInImageHeap(obj)) {
            return JNIImageHeapHandles.asLocal(obj);
        }
        return getLocals().create(obj);
    }

    public static JNIObjectHandle newLocalRef(JNIObjectHandle ref) {
        if (useImageHeapHandles() && JNIImageHeapHandles.isInRange(ref)) {
            return JNIImageHeapHandles.toLocal(ref);
        }
        return getLocals().create(getObject(ref));
    }

    public static void deleteLocalRef(JNIObjectHandle localRef) {
        if (!useImageHeapHandles() || !JNIImageHeapHandles.isInRange(localRef)) {
            getLocals().delete(localRef);
        }
    }

    public static int pushLocalFrame(int capacity) {
        return getLocals().pushFrame(capacity);
    }

    public static void popLocalFrame() {
        getLocals().popFrame();
    }

    public static void popLocalFramesIncluding(int frame) {
        getLocals().popFramesIncluding(frame);
    }

    public static void ensureLocalCapacity(int capacity) {
        getLocals().ensureCapacity(capacity);
    }

    public static JNIObjectHandle newGlobalRef(JNIObjectHandle handle) {
        JNIObjectHandle result = nullHandle();
        if (useImageHeapHandles() && JNIImageHeapHandles.isInRange(handle)) {
            result = JNIImageHeapHandles.toGlobal(handle);
        } else {
            Object obj = getObject(handle);
            if (obj != null) {
                result = (JNIObjectHandle) globalHandles.create(obj);
            }
        }
        return result;
    }

    public static void deleteGlobalRef(JNIObjectHandle globalRef) {
        if (!useImageHeapHandles() || !JNIImageHeapHandles.isInRange(globalRef)) {
            globalHandles.destroy(globalRef);
        }
    }

    public static JNIObjectHandle newWeakGlobalRef(JNIObjectHandle handle) {
        JNIObjectHandle result = nullHandle();
        if (useImageHeapHandles() && JNIImageHeapHandles.isInRange(handle)) {
            result = JNIImageHeapHandles.toWeakGlobal(handle);
        } else {
            Object obj = getObject(handle);
            if (obj != null) {
                result = (JNIObjectHandle) globalHandles.createWeak(obj);
            }
        }
        return result;
    }

    public static void deleteWeakGlobalRef(JNIObjectHandle weakRef) {
        if (!useImageHeapHandles() || !JNIImageHeapHandles.isInRange(weakRef)) {
            globalHandles.destroyWeak(weakRef);
        }
    }

    static int getLocalHandleCount() {
        return getLocals().getHandleCount();
    }

    static long getGlobalHandleCount() {
        return ObjectHandlesTestingBackdoor.getCurrentCount(globalHandles);
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
        assert ENTIRE_RANGE_MAX.lessThan(JNIObjectHandles.GLOBAL_HANDLES_MIN) || ENTIRE_RANGE_MIN.greaterThan(JNIObjectHandles.GLOBAL_HANDLES_MAX);
    }

    static boolean isInImageHeap(Object target) {
        return Heap.getHeap().isInImageHeap(target);
    }

    static boolean isInRange(JNIObjectHandle handle) {
        SignedWord handleValue = (SignedWord) handle;
        return handleValue.greaterOrEqual(ENTIRE_RANGE_MIN) && handleValue.lessOrEqual(ENTIRE_RANGE_MAX);
    }

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
        assert isInRange(handle);
        return (JNIObjectHandle) ((SignedWord) handle).and(OBJ_OFFSET_BITS_MASK).add(rangeMin);
    }

    static <T> T getObject(JNIObjectHandle handle) {
        assert isInRange(handle);
        UnsignedWord base = (UnsignedWord) Isolates.getHeapBase(CurrentIsolate.getIsolate());
        Pointer offset = ((Pointer) handle).and(OBJ_OFFSET_BITS_MASK).add(base);
        @SuppressWarnings("unchecked")
        T obj = (T) KnownIntrinsics.convertUnknownValue(offset.toObjectNonNull(), Object.class);
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
