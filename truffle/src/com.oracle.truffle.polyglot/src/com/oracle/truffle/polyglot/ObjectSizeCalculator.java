/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.io.TruffleProcessBuilder;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.provider.TruffleLanguageProvider;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import sun.misc.Unsafe;

final class ObjectSizeCalculator {
    private enum ForcedStop {
        NONE,
        STOPATBYTES,
        CANCELLATION
    }

    static final sun.misc.Unsafe UNSAFE = getUnsafe();

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    private static volatile int staticObjectAlignment = -1;

    private static int getObjectAlignment() {
        int localObjectAlignment = staticObjectAlignment;
        if (localObjectAlignment < 0) {
            localObjectAlignment = EngineAccessor.RUNTIME.getObjectAlignment();
            assert localObjectAlignment > -1;
            staticObjectAlignment = localObjectAlignment;
        }
        return localObjectAlignment;
    }

    private static ForcedStop enqueueOrStop(CalculationState calculationState, Object obj) {
        ClassInfo classInfo = canProceed(calculationState.api, calculationState.classInfos, obj);
        if (classInfo != StopClassInfo.INSTANCE && calculationState.alreadyVisited.add(obj)) {
            classInfo.increaseByBaseSize(calculationState, obj);
            if (calculationState.dataSize > calculationState.stopAtBytes) {
                return ForcedStop.STOPATBYTES;
            } else if (calculationState.cancelled.get()) {
                return ForcedStop.CANCELLATION;
            }
            enqueue(calculationState.pending, obj);
        }
        return ForcedStop.NONE;
    }

    private static final class ArrayMemoryLayout {
        private static volatile Map<Class<?>, ArrayMemoryLayout> arrayMemoryLayouts = null;

        final int baseOffset;
        final int indexScale;

        private static Map<Class<?>, ArrayMemoryLayout> getArrayMemoryLayouts() {
            Map<Class<?>, ArrayMemoryLayout> localArrayMemoryLayouts = arrayMemoryLayouts;
            if (localArrayMemoryLayouts == null) {
                localArrayMemoryLayouts = new IdentityHashMap<>();
                localArrayMemoryLayouts.put(boolean.class, new ArrayMemoryLayout(boolean.class));
                localArrayMemoryLayouts.put(byte.class, new ArrayMemoryLayout(byte.class));
                localArrayMemoryLayouts.put(short.class, new ArrayMemoryLayout(short.class));
                localArrayMemoryLayouts.put(char.class, new ArrayMemoryLayout(char.class));
                localArrayMemoryLayouts.put(int.class, new ArrayMemoryLayout(int.class));
                localArrayMemoryLayouts.put(float.class, new ArrayMemoryLayout(float.class));
                localArrayMemoryLayouts.put(long.class, new ArrayMemoryLayout(long.class));
                localArrayMemoryLayouts.put(double.class, new ArrayMemoryLayout(double.class));
                localArrayMemoryLayouts.put(Object.class, new ArrayMemoryLayout(Object.class));
                arrayMemoryLayouts = localArrayMemoryLayouts;
            }
            return localArrayMemoryLayouts;
        }

        ArrayMemoryLayout(Class<?> componentType) {
            baseOffset = EngineAccessor.RUNTIME.getArrayBaseOffset(componentType);
            indexScale = EngineAccessor.RUNTIME.getArrayIndexScale(componentType);
        }
    }

    private static final class CalculationState {
        private final APIAccess api;
        private final Map<Class<?>, ClassInfo> classInfos;
        private final QuickIdentitySet<Object> alreadyVisited;
        private final Deque<Object> pending = new ArrayDeque<>(16 * 1024);
        private final long stopAtBytes;
        private final AtomicBoolean cancelled;

        private long dataSize;

        CalculationState(APIAccess api, Map<Class<?>, ClassInfo> classInfos, QuickIdentitySet<Object> alreadyVisited, long stopAtBytes, AtomicBoolean cancelled) {
            this.api = api;
            this.classInfos = classInfos;
            this.alreadyVisited = alreadyVisited;
            this.stopAtBytes = stopAtBytes;
            this.cancelled = cancelled;
        }
    }

    private boolean cachedClassInfosInUse;
    private Map<Class<?>, ClassInfo> cachedClassInfos;

    private int alreadyVisitedInitialCapacity = 16 * 1024;

    /**
     * Given an object, returns the allocated size, in bytes, of the object and all other objects
     * reachable from it within {@link ObjectSizeCalculator#isContextHeapBoundary(APIAccess, Object)
     * context heap boundary}.
     *
     * @param obj the object; cannot be null.
     * @param stopAtBytes when calculated size exceeds stopAtBytes, calculation stops and returns
     *            the current size.
     * @param cancelled when cancelled returns true, calculation stops and throws
     *            {@link java.util.concurrent.CancellationException}.
     * @return returns the allocated size of the object and all other objects it retains calculated
     *         up to the point at which the calculation exhausted objects to explore or was stopped
     *         by stopAtBytes.
     * @throws java.util.concurrent.CancellationException in case the calculation is cancelled. The
     *             message of the exception specifies the calculated size up to the cancellation.
     */
    @CompilerDirectives.TruffleBoundary
    long calculateObjectSize(APIAccess api, final Object obj, long stopAtBytes, AtomicBoolean cancelled) {
        if (Truffle.getRuntime() instanceof DefaultTruffleRuntime) {
            throw new UnsupportedOperationException("Polyglot context heap size calculation is not supported on this platform.");
        }
        /*
         * Breadth-first traversal instead of naive depth-first with recursive implementation, so we
         * don't blow the stack traversing long linked lists.
         */
        CalculationState calculationState;
        boolean usingCachedClassInfos = false;
        synchronized (this) {
            /*
             * Sharing the classInfos for all calculations would require a data structure like
             * ConcurrentHashMap, which would have a negative impact on performance, but no caching
             * at all would also be bad for performance, and so to be able to use a non-concurrent
             * data structure for the cache, we cache classInfos for the first calculation and
             * re-use it in each subsequent calculation that is not executed in parallel with the
             * previous calculation that used the cachedClassInfos.
             */
            Map<Class<?>, ClassInfo> classInfosToUse;
            if (!cachedClassInfosInUse) {
                if (cachedClassInfos == null) {
                    cachedClassInfos = new IdentityHashMap<>();
                }
                classInfosToUse = cachedClassInfos;
                cachedClassInfosInUse = true;
                usingCachedClassInfos = true;
            } else {
                classInfosToUse = new IdentityHashMap<>();
            }
            calculationState = new CalculationState(api, classInfosToUse, new QuickIdentitySet<>(alreadyVisitedInitialCapacity), stopAtBytes, cancelled);
        }
        try {
            if (cancelled.get()) {
                throw cancel(calculationState.dataSize);
            }
            ForcedStop stop = enqueueOrStop(calculationState, obj);
            for (Object o = calculationState.pending.pollFirst();;) {
                if (o != null) {
                    stop = visit(calculationState, o);
                }
                if (calculationState.pending.isEmpty() || stop == ForcedStop.STOPATBYTES) {
                    return calculationState.dataSize;
                } else if (stop == ForcedStop.CANCELLATION) {
                    throw cancel(calculationState.dataSize);
                }
                o = calculationState.pending.pollFirst();
            }
        } finally {
            synchronized (this) {
                if (usingCachedClassInfos) {
                    cachedClassInfosInUse = false;
                }
                if (calculationState.alreadyVisited.getCapacity() > alreadyVisitedInitialCapacity) {
                    alreadyVisitedInitialCapacity = calculationState.alreadyVisited.getCapacity();
                }
            }
        }
    }

    private static CancellationException cancel(long dataSize) {
        throw new CancellationException(String.format("cancelled at %d bytes", dataSize));
    }

    private static ClassInfo getClassInfo(Map<Class<?>, ClassInfo> classInfos, Class<?> clazz) {
        return classInfos.computeIfAbsent(clazz, new Function<Class<?>, ClassInfo>() {
            @Override
            public ClassInfo apply(Class<?> aClass) {
                return clazz.isArray() ? new ArrayClassInfo(aClass) : new ObjectClassInfo(aClass);
            }
        });
    }

    private static ForcedStop visit(CalculationState calculationState, Object obj) {
        Class<?> clazz = obj.getClass();
        if (clazz == ArrayElementsVisitor.class) {
            return ((ArrayElementsVisitor) obj).visit(calculationState);
        } else {
            return calculationState.classInfos.get(clazz).visit(calculationState, obj);
        }
    }

    private static void increaseByArraySize(CalculationState calculationState, ArrayMemoryLayout layout, long length) {
        increaseSize(calculationState, roundToObjectAlignment(layout.baseOffset + length * layout.indexScale, getObjectAlignment()));
    }

    @SuppressWarnings("deprecation")
    private static boolean shouldBeReachable(APIAccess api, Object obj, boolean allowContext) {
        if (obj instanceof PolyglotImpl.VMObject) {
            // only these two vm objects are allowed
            return allowContext && (obj instanceof PolyglotLanguageContext || obj instanceof PolyglotContextImpl);
        }
        if (obj instanceof PolyglotContextConfig ||
                        obj instanceof TruffleLanguageProvider ||
                        obj instanceof ExecutionEventListener ||
                        obj instanceof ClassValue ||
                        obj instanceof PolyglotWrapper ||
                        api.isValue(obj) ||
                        api.isContext(obj) ||
                        api.isEngine(obj) ||
                        api.isLanguage(obj) ||
                        api.isInstrument(obj) ||
                        api.isSource(obj) ||
                        api.isSourceSection(obj)) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private static boolean isContextHeapBoundary(APIAccess api, Object obj) {
        if (obj == null) {
            return true;
        }

        assert shouldBeReachable(api, obj, true) : obj.getClass().getName() + " should not be reachable";

        return (obj instanceof Thread) ||
                        EngineAccessor.HOST.isHostBoundaryValue(obj) ||

                        (obj instanceof Class) ||
                        (obj instanceof ClassLoader) ||
                        (obj instanceof OptionValues) ||

                        (obj instanceof TruffleLanguage.ContextReference) ||
                        (obj instanceof TruffleLanguage.LanguageReference) ||
                        (obj instanceof Source) ||
                        (obj instanceof SourceSection) ||
                        (obj instanceof TruffleFile) ||
                        (obj instanceof TruffleLogger) ||
                        (obj instanceof InstrumentInfo) ||
                        (obj instanceof LanguageInfo) ||
                        (obj instanceof TruffleProcessBuilder) ||

                        (obj instanceof CallTarget) ||
                        (obj instanceof Node) ||
                        (obj instanceof NodeFactory) ||
                        (obj instanceof AllocationReporter) ||
                        (obj instanceof Assumption) ||
                        (obj instanceof TruffleLanguage) ||
                        (obj instanceof TruffleLanguage.Env) ||
                        (obj instanceof TruffleInstrument) ||
                        (obj instanceof TruffleInstrument.Env) ||
                        (obj instanceof TruffleContext) ||

                        (obj instanceof ContextLocal) ||
                        (obj instanceof ContextThreadLocal) ||

                        (obj instanceof EventBinding<?>) ||
                        /*
                         * For safety, copy the asserts here in case asserts are disabled.
                         */
                        !shouldBeReachable(api, obj, false);
    }

    private static ClassInfo canProceed(APIAccess api, Map<Class<?>, ClassInfo> classInfos, Object obj) {
        if (obj == null) {
            return StopClassInfo.INSTANCE;
        }
        Class<?> clazz = obj.getClass();
        ClassInfo classInfo = classInfos.get(clazz);

        if (classInfo != null) {
            return classInfo;
        } else {
            boolean eligible = !isContextHeapBoundary(api, obj);
            if (eligible) {
                classInfo = getClassInfo(classInfos, clazz);
            } else {
                classInfos.put(clazz, classInfo = StopClassInfo.INSTANCE);
            }
            return classInfo;
        }
    }

    private static final class ArrayElementsVisitor {
        private final Object[] array;
        private final QuickIdentitySet<Object> alreadyVisited;

        ArrayElementsVisitor(final Object[] array, QuickIdentitySet<Object> alreadyVisited) {
            this.array = array;
            this.alreadyVisited = alreadyVisited;
        }

        public ForcedStop visit(CalculationState calculationState) {
            for (final Object elem : array) {
                ClassInfo classInfo = canProceed(calculationState.api, calculationState.classInfos, elem);
                if (classInfo != StopClassInfo.INSTANCE && alreadyVisited.add(elem)) {
                    classInfo.increaseByBaseSize(calculationState, elem);
                    if (calculationState.dataSize > calculationState.stopAtBytes) {
                        return ForcedStop.STOPATBYTES;
                    } else if (calculationState.cancelled.get()) {
                        return ForcedStop.CANCELLATION;
                    }
                    ForcedStop stop = ObjectSizeCalculator.visit(calculationState, elem);
                    if (stop != ForcedStop.NONE) {
                        return stop;
                    }
                }
            }
            return ForcedStop.NONE;
        }
    }

    private static void enqueue(Deque<Object> pending, Object obj) {
        pending.addLast(obj);
    }

    private static void increaseSize(CalculationState calculationState, long objectSize) {
        calculationState.dataSize += objectSize;
    }

    private static long roundToObjectAlignment(long x, int objectAlignment) {
        return ((x + objectAlignment - 1) / objectAlignment) * objectAlignment;
    }

    private interface ClassInfo {
        /**
         * @return <code>STOPATBYTES</code> or <code>CANCELLATION</code> if calculation should be
         *         stopped due to stopAtBytes or cancellation, respectively, <code>NONE</code>
         *         otherwise.
         */
        ForcedStop visit(CalculationState calculationState, Object obj);

        /*
         * Base size is added when the object is enqueued so that the queue doesn't grow too much
         * without accounting for the size of the objects in it. This could be a problem when huge
         * arrays of objects or objects with huge number of object fields are processed. If we
         * didn't add anything for the objects being enqueued, we lose the opportunity to detect the
         * memoryLimit being exceeded as soon as possible.
         */
        void increaseByBaseSize(CalculationState calculationState, Object obj);
    }

    private static final class StopClassInfo implements ClassInfo {
        static final StopClassInfo INSTANCE = new StopClassInfo();

        StopClassInfo() {
        }

        @Override
        public ForcedStop visit(CalculationState calculationState, Object obj) {
            return ForcedStop.NONE;
        }

        @Override
        public void increaseByBaseSize(CalculationState calculationState, Object obj) {

        }
    }

    private static final class ArrayClassInfo implements ClassInfo {
        private final ArrayMemoryLayout arrayMemoryLayout;
        private final boolean isPrimitive;

        ArrayClassInfo(Class<?> clazz) {
            Class<?> componentType = clazz.getComponentType();
            if (componentType.isPrimitive()) {
                this.arrayMemoryLayout = ArrayMemoryLayout.getArrayMemoryLayouts().get(componentType);
                isPrimitive = true;
            } else {
                this.arrayMemoryLayout = ArrayMemoryLayout.getArrayMemoryLayouts().get(Object.class);
                isPrimitive = false;
            }
        }

        @Override
        public void increaseByBaseSize(CalculationState calculationState, Object obj) {
            int length = Array.getLength(obj);
            increaseByArraySize(calculationState, arrayMemoryLayout, length);
        }

        @Override
        public ForcedStop visit(CalculationState calculationState, Object obj) {
            if (!isPrimitive) {
                int length = Array.getLength(obj);
                /*
                 * If we didn't use an ArrayElementsVisitor, we would be enqueueing every element of
                 * the array here instead. For large arrays, it would tremendously enlarge the
                 * queue. In essence, we're compressing it into a small command object instead. This
                 * is different than immediately visiting the elements, as their visiting is
                 * scheduled for the end of the current queue.
                 */
                switch (length) {
                    case 0: {
                        break;
                    }
                    case 1: {
                        Object o = Array.get(obj, 0);
                        return enqueueOrStop(calculationState, o);
                    }
                    default: {
                        enqueue(calculationState.pending, new ArrayElementsVisitor((Object[]) obj, calculationState.alreadyVisited));
                    }
                }
            }
            return ForcedStop.NONE;
        }
    }

    private static final class ObjectClassInfo implements ClassInfo {
        // Padded fields + header size
        private final long objectSize;
        private final int[] fieldOffsets;
        private final boolean isReference;
        private final Class<?> clazz;

        ObjectClassInfo(Class<?> clazz) {
            this.fieldOffsets = EngineAccessor.RUNTIME.getFieldOffsets(clazz, false, true);
            this.objectSize = EngineAccessor.RUNTIME.getBaseInstanceSize(clazz);
            this.isReference = Reference.class.isAssignableFrom(clazz);
            this.clazz = clazz;
        }

        @Override
        public void increaseByBaseSize(CalculationState calculationState, Object obj) {
            increaseSize(calculationState, objectSize);
        }

        @Override
        public ForcedStop visit(CalculationState calculationState, Object obj) {
            assert clazz == obj.getClass();
            if (isReference) {
                Object nextObj = null;
                try {
                    nextObj = ((Reference<?>) obj).get();
                } catch (Exception t) {
                    /*
                     * The lookup might throw an exception .e.g UnsupportedOperationException for
                     * phantom references.
                     */
                }
                ForcedStop stop = enqueueOrStop(calculationState, nextObj);
                if (stop != ForcedStop.NONE) {
                    return stop;
                }
            }
            for (int fieldOffset : fieldOffsets) {
                Object nextObj = UNSAFE.getObject(obj, fieldOffset);
                ForcedStop stop = enqueueOrStop(calculationState, nextObj);
                if (stop != ForcedStop.NONE) {
                    return stop;
                }
            }
            return ForcedStop.NONE;
        }

    }

    /**
     * Identity set that supports only the {@link #add(Object) add} method. It is backed by a single
     * object array that is resized only when the number of its elements is more than a half of the
     * capacity of the set. No other allocation on the heap are performed by this set.
     */
    private static final class QuickIdentitySet<T> implements Set<T> {

        private Object[] data;
        private int size;
        private int capacity;
        private int growLimit;

        QuickIdentitySet(int initialCapacity) {
            if (initialCapacity < 1) {
                throw new IllegalArgumentException();
            }
            this.capacity = initialCapacity;
            this.data = new Object[this.capacity];
            updateGrowLimit();
        }

        private void updateGrowLimit() {
            growLimit = capacity / 2;
        }

        @Override
        public int size() {
            return size;
        }

        public int getCapacity() {
            return capacity;
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public boolean contains(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<T> iterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object[] toArray() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T1> T1[] toArray(T1[] a) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean add(T t) {
            if (t == null) {
                throw new IllegalArgumentException();
            }
            int i = System.identityHashCode(t) % capacity;
            if (i < 0) {
                i += capacity;
            }
            while (data[i] != null && data[i] != t) {
                i++;
                if (i == capacity) {
                    i = 0;
                }
            }
            if (data[i] == null) {
                data[i] = t;
                size++;
                if (size > growLimit) {
                    grow();
                }
                return true;
            } else {
                return false;
            }
        }

        private void addFast(Object t) {
            int i = System.identityHashCode(t) % capacity;
            if (i < 0) {
                i += capacity;
            }
            while (data[i] != null) {
                i++;
                if (i == capacity) {
                    i = 0;
                }
            }
            data[i] = t;
        }

        private void grow() {
            capacity = Math.multiplyExact(2, capacity);
            Object[] oldData = data;
            data = new Object[capacity];
            for (Object obj : oldData) {
                if (obj != null) {
                    addFast(obj);
                }
            }
            updateGrowLimit();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            if (size > 0) {
                Arrays.fill(data, null);
            }
            size = 0;
        }
    }
}
