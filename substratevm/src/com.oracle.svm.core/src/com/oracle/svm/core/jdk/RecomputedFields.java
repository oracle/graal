/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

//Checkstyle: stop

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.ArrayBaseOffset;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.ArrayIndexShift;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.AtomicFieldUpdaterOffset;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.FieldOffset;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.FromAlias;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Reset;

import java.io.FileDescriptor;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.UnsafeAccess;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/*
 * This file contains JDK fields that need to be intercepted because their value in the hosted environment is not
 * suitable for the Substrate VM. The list is derived from the intercepted fields of the Maxine VM.
 */

@TargetClass(java.util.EnumMap.class)
final class Target_java_util_EnumMap {
    @Alias @RecomputeFieldValue(kind = Reset) //
    private Set<Map.Entry<?, ?>> entrySet;
}

@TargetClass(sun.util.calendar.ZoneInfoFile.class)
final class Target_sun_util_calendar_ZoneInfoFile {
    @Alias @RecomputeFieldValue(kind = FromAlias) //
    private static Map<String, String> aliases = new java.util.HashMap<>();
}

@TargetClass(java.util.Random.class)
final class Target_java_util_Random {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "seed") //
    private static long seedOffset;
}

@TargetClass(java.util.concurrent.ConcurrentSkipListSet.class)
final class Target_java_util_concurrent_ConcurrentSkipListSet {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "m") //
    private static long mapOffset;
}

@TargetClass(java.util.concurrent.ConcurrentSkipListMap.class)
final class Target_java_util_concurrent_ConcurrentSkipListMap {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "head") //
    private static long headOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = Thread.class, name = "threadLocalRandomSecondarySeed") //
    private static long SECONDARY;
}

@TargetClass(value = java.util.concurrent.ConcurrentSkipListMap.class, innerClass = "Node") //
final class Target_java_util_concurrent_ConcurrentSkipListMap_Node {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "value") //
    private static long valueOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "next") //
    private static long nextOffset;
}

@TargetClass(value = java.util.concurrent.ConcurrentSkipListMap.class, innerClass = "Index")
final class Target_java_util_concurrent_ConcurrentSkipListMap_Index {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "right") //
    private static long rightOffset;
}

@TargetClass(value = java.util.concurrent.ConcurrentLinkedQueue.class, innerClass = "Node")
final class Target_java_util_concurrent_ConcurrentLinkedQueue_Node {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "item") //
    private static long itemOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "next") //
    private static long nextOffset;
}

@TargetClass(java.util.concurrent.ConcurrentLinkedQueue.class)
final class Target_java_util_concurrent_ConcurrentLinkedQueue {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "head") //
    private static long headOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "tail") //
    private static long tailOffset;
}

@TargetClass(java.util.concurrent.CopyOnWriteArrayList.class)
final class Target_java_util_concurrent_CopyOnWriteArrayList {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "lock") //
    private static long lockOffset;
}

@TargetClass(value = java.util.concurrent.ConcurrentLinkedDeque.class, innerClass = "Node")
final class Target_java_util_concurrent_ConcurrentLinkedDeque_Node {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "prev") //
    private static long prevOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "item") //
    private static long itemOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "next") //
    private static long nextOffset;
}

@TargetClass(java.util.concurrent.ConcurrentLinkedDeque.class)
final class Target_java_util_concurrent_ConcurrentLinkedDeque {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "head") //
    private static long headOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "tail") //
    private static long tailOffset;
}

@TargetClass(className = "java.nio.DirectByteBuffer")
@SuppressWarnings("unused")
final class Target_java_nio_DirectByteBuffer {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = byte[].class) //
    private static long arrayBaseOffset;

    @Alias
    protected Target_java_nio_DirectByteBuffer(int cap, long addr, FileDescriptor fd, Runnable unmapper) {
    }
}

@TargetClass(className = "java.nio.DirectByteBufferR")
@SuppressWarnings("unused")
final class Target_java_nio_DirectByteBufferR {
    @Alias
    protected Target_java_nio_DirectByteBufferR(int cap, long addr, FileDescriptor fd, Runnable unmapper) {
    }
}

@TargetClass(className = "java.nio.DirectCharBufferS")
final class Target_java_nio_DirectCharBufferS {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = char[].class) //
    private static long arrayBaseOffset;
}

@TargetClass(className = "java.nio.DirectCharBufferU")
final class Target_java_nio_DirectCharBufferU {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = char[].class) //
    private static long arrayBaseOffset;
}

@TargetClass(className = "java.nio.DirectDoubleBufferS")
final class Target_java_nio_DirectDoubleBufferS {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = double[].class) //
    private static long arrayBaseOffset;
}

@TargetClass(className = "java.nio.DirectDoubleBufferU")
final class Target_java_nio_DirectDoubleBufferU {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = double[].class) //
    private static long arrayBaseOffset;
}

@TargetClass(className = "java.nio.DirectFloatBufferS")
final class Target_java_nio_DirectFloatBufferS {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = float[].class) //
    private static long arrayBaseOffset;
}

@TargetClass(className = "java.nio.DirectFloatBufferU")
final class Target_java_nio_DirectFloatBufferU {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = float[].class) //
    private static long arrayBaseOffset;
}

@TargetClass(className = "java.nio.DirectIntBufferS")
final class Target_java_nio_DirectIntBufferS {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = int[].class) //
    private static long arrayBaseOffset;
}

@TargetClass(className = "java.nio.DirectIntBufferU")
final class Target_java_nio_DirectIntBufferU {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = int[].class) //
    private static long arrayBaseOffset;
}

@TargetClass(className = "java.nio.DirectLongBufferS")
final class Target_java_nio_DirectLongBufferS {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = long[].class) //
    private static long arrayBaseOffset;
}

@TargetClass(className = "java.nio.DirectLongBufferU")
final class Target_java_nio_DirectLongBufferU {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = long[].class) //
    private static long arrayBaseOffset;
}

@TargetClass(className = "java.nio.DirectShortBufferS")
final class Target_java_nio_DirectShortBufferS {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = short[].class) //
    private static long arrayBaseOffset;
}

@TargetClass(className = "java.nio.DirectShortBufferU")
final class Target_java_nio_DirectShortBufferU {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = short[].class) //
    private static long arrayBaseOffset;
}

@TargetClass(java.nio.charset.CharsetEncoder.class)
final class Target_java_nio_charset_CharsetEncoder {
    @Alias @RecomputeFieldValue(kind = Reset) //
    private WeakReference<CharsetDecoder> cachedDecoder;
}

@TargetClass(className = "java.nio.charset.CoderResult$Cache")
final class Target_java_nio_charset_CoderResult_Cache {
    @Alias @RecomputeFieldValue(kind = Reset) //
    private Map<Integer, WeakReference<CoderResult>> cache;
}

@TargetClass(java.util.concurrent.atomic.AtomicBoolean.class)
final class Target_java_util_concurrent_atomic_AtomicBoolean {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "value") //
    private static long valueOffset;
}

@TargetClass(java.util.concurrent.atomic.AtomicInteger.class)
final class Target_java_util_concurrent_atomic_AtomicInteger {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "value") //
    protected static long valueOffset;
}

@TargetClass(java.util.concurrent.atomic.AtomicLong.class)
final class Target_java_util_concurrent_atomic_AtomicLong {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "value") //
    protected static long valueOffset;
}

@TargetClass(java.util.concurrent.atomic.AtomicReference.class)
final class Target_java_util_concurrent_atomic_AtomicReference {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "value") //
    protected static long valueOffset;
}

@TargetClass(java.util.concurrent.atomic.AtomicIntegerArray.class)
final class Target_java_util_concurrent_atomic_AtomicIntegerArray {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = int[].class) //
    private static int base;
    @Alias @RecomputeFieldValue(kind = ArrayIndexShift, declClass = int[].class) //
    private static int shift;
}

@TargetClass(java.util.concurrent.atomic.AtomicLongArray.class)
final class Target_java_util_concurrent_atomic_AtomicLongArray {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = long[].class) //
    private static int base;
    @Alias @RecomputeFieldValue(kind = ArrayIndexShift, declClass = long[].class) //
    private static int shift;
}

@TargetClass(java.util.concurrent.atomic.AtomicReferenceArray.class)
final class Target_java_util_concurrent_atomic_AtomicReferenceArray {
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = Object[].class) //
    private static int base;
    @Alias @RecomputeFieldValue(kind = ArrayIndexShift, declClass = Object[].class) //
    private static int shift;
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicReferenceFieldUpdater$AtomicReferenceFieldUpdaterImpl")
final class Target_java_util_concurrent_atomic_AtomicReferenceFieldUpdater_AtomicReferenceFieldUpdaterImpl {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicIntegerFieldUpdater$AtomicIntegerFieldUpdaterImpl")
final class Target_java_util_concurrent_atomic_AtomicIntegerFieldUpdater_AtomicIntegerFieldUpdaterImpl {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicLongFieldUpdater$CASUpdater")
final class Target_java_util_concurrent_atomic_AtomicLongFieldUpdater_CASUpdater {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicLongFieldUpdater$LockedUpdater")
final class Target_java_util_concurrent_atomic_AtomicLongFieldUpdater_LockedUpdater {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;
}

/**
 * The atomic field updaters access fields using {@link sun.misc.Unsafe}. The static analysis needs
 * to know about all these fields, so we need to find the original field (the updater only stores
 * the field offset) and mark it as unsafe accessed.
 */
@AutomaticFeature
class AtomicFieldUpdaterFeature implements Feature {

    private final ConcurrentMap<Object, Boolean> processedUpdaters = new ConcurrentHashMap<>();
    private Consumer<Field> markAsUnsafeAccessed;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(this::processObject);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        markAsUnsafeAccessed = (field -> access.registerAsUnsafeWritten(field));
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        markAsUnsafeAccessed = null;
    }

    private Object processObject(Object obj) {
        if (obj instanceof AtomicReferenceFieldUpdater || obj instanceof AtomicIntegerFieldUpdater || obj instanceof AtomicLongFieldUpdater) {
            if (processedUpdaters.putIfAbsent(obj, true) == null) {
                processFieldUpdater(obj);
            }
        }
        return obj;
    }

    /*
     * This code runs multi-threaded during the static analysis. It must not be called after static
     * analysis, because that would mean that we missed an atomic field updater during static
     * analysis.
     */
    private void processFieldUpdater(Object updater) {
        VMError.guarantee(markAsUnsafeAccessed != null, "New atomic field updater found after static analysis");

        try {
            Class<?> updaterClass = updater.getClass();

            Field tclassField = updaterClass.getDeclaredField("tclass");
            Field offsetField = updaterClass.getDeclaredField("offset");
            tclassField.setAccessible(true);
            offsetField.setAccessible(true);

            Class<?> tclass = (Class<?>) tclassField.get(updater);
            long searchOffset = offsetField.getLong(updater);
            // search the declared fields for a field with a matching offset
            for (Field f : tclass.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    long fieldOffset = UnsafeAccess.UNSAFE.objectFieldOffset(f);
                    if (fieldOffset == searchOffset) {
                        markAsUnsafeAccessed.accept(f);
                        return;
                    }
                }
            }
            throw VMError.shouldNotReachHere("unknown field offset class: " + tclass + ", offset = " + searchOffset);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}

@TargetClass(java.util.concurrent.locks.AbstractQueuedSynchronizer.class)
final class Target_java_util_concurrent_locks_AbstractQueuedSynchronizer {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "state") //
    private static long stateOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "head") //
    private static long headOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "tail") //
    private static long tailOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.locks.AbstractQueuedSynchronizer$Node", name = "waitStatus") //
    private static long waitStatusOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.locks.AbstractQueuedSynchronizer$Node", name = "next") //
    private static long nextOffset;
}

@TargetClass(java.util.concurrent.locks.AbstractQueuedLongSynchronizer.class)
final class Target_java_util_concurrent_locks_AbstractQueuedLongSynchronizer {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "state") //
    private static long stateOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "head") //
    private static long headOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "tail") //
    private static long tailOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.locks.AbstractQueuedLongSynchronizer$Node", name = "waitStatus") //
    private static long waitStatusOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.locks.AbstractQueuedLongSynchronizer$Node", name = "next") //
    private static long nextOffset;
}

@TargetClass(java.util.concurrent.locks.LockSupport.class)
final class Target_java_util_concurrent_locks_LockSupport {
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = java.lang.Thread.class, name = "parkBlocker") //
    private static long parkBlockerOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = java.lang.Thread.class, name = "threadLocalRandomSeed") //
    private static long SEED;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = java.lang.Thread.class, name = "threadLocalRandomProbe") //
    private static long PROBE;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = java.lang.Thread.class, name = "threadLocalRandomSecondarySeed") //
    private static long SECONDARY;
}

@TargetClass(java.util.concurrent.locks.ReentrantReadWriteLock.class)
final class Target_java_util_concurrent_locks_ReentrantReadWriteLock {
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = Thread.class, name = "tid") //
    private static long TID_OFFSET;
}

@TargetClass(java.util.concurrent.locks.StampedLock.class)
final class Target_java_util_concurrent_locks_StampedLock {
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = StampedLock.class, name = "state") //
    private static long STATE;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = StampedLock.class, name = "whead") //
    private static long WHEAD;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = StampedLock.class, name = "wtail") //
    private static long WTAIL;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.locks.StampedLock$WNode", name = "status") //
    private static long WSTATUS;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.locks.StampedLock$WNode", name = "next") //
    private static long WNEXT;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.locks.StampedLock$WNode", name = "cowait") //
    private static long WCOWAIT;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = Thread.class, name = "parkBlocker") //
    private static long PARKBLOCKER;
}

@TargetClass(value = java.math.BigInteger.class, innerClass = "UnsafeHolder")
final class Target_java_math_BigInteger_UnsafeHolder {
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = java.math.BigInteger.class, name = "signum") //
    private static long signumOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = java.math.BigInteger.class, name = "mag") //
    private static long magOffset;
}

@TargetClass(java.util.concurrent.ConcurrentHashMap.class)
final class Target_java_util_concurrent_ConcurrentHashMap {

    @Substitute
    private static Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) {
            /*
             * We cannot do all the generic interface checks that the original implementation is
             * doing, because we do not have the necessary metadata at run time.
             */
            return x.getClass();
        }
        return null;
    }

    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "sizeCtl") //
    private static long SIZECTL;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "transferIndex") //
    private static long TRANSFERINDEX;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "baseCount") //
    private static long BASECOUNT;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "cellsBusy") //
    private static long CELLSBUSY;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.ConcurrentHashMap$CounterCell", name = "value") //
    private static long CELLVALUE;
    /*
     * TODO: This should use Node[].class, but that class is not visible, so I use Object[].class.
     */
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = Object[].class) //
    private static long ABASE;
    @Alias @RecomputeFieldValue(kind = ArrayIndexShift, declClass = Object[].class) //
    private static int ASHIFT;
}

@TargetClass(className = "java.util.concurrent.ConcurrentHashMap$TreeBin")
final class Target_java_util_concurrent_ConcurrentHashMap_TreeBin {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "lockState") //
    private static long LOCKSTATE;
}

@TargetClass(java.util.concurrent.ForkJoinPool.class)
final class Target_java_util_concurrent_ForkJoinPool {

    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "ctl") //
    private static long CTL;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "runState") //
    private static long RUNSTATE;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "stealCounter") //
    private static long STEALCOUNTER;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = Thread.class, name = "parkBlocker") //
    private static long PARKBLOCKER;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.ForkJoinPool$WorkQueue", name = "top") //
    private static long QTOP;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.ForkJoinPool$WorkQueue", name = "qlock") //
    private static long QLOCK;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.ForkJoinPool$WorkQueue", name = "scanState") //
    private static long QSCANSTATE;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.ForkJoinPool$WorkQueue", name = "parker") //
    private static long QPARKER;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.ForkJoinPool$WorkQueue", name = "currentSteal") //
    private static long QCURRENTSTEAL;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.ForkJoinPool$WorkQueue", name = "currentJoin") //
    private static long QCURRENTJOIN;
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = ForkJoinTask[].class) //
    private static int ABASE;
    @Alias @RecomputeFieldValue(kind = ArrayIndexShift, declClass = Object[].class) //
    private static int ASHIFT;

    @Alias static /* final */ int MAX_CAP;
    @Alias static /* final */ int LIFO_QUEUE;
    @Alias static /* final */ ForkJoinWorkerThreadFactory defaultForkJoinWorkerThreadFactory;

    @Alias
    @SuppressWarnings("unused")
    protected Target_java_util_concurrent_ForkJoinPool(int parallelism, ForkJoinWorkerThreadFactory factory, UncaughtExceptionHandler handler, int mode, String workerNamePrefix) {
    }

    /**
     * "commonParallelism" is only accessed via this method, so a substitution provides a convenient
     * place to ensure that it is initialized.
     */
    @Substitute
    public static int getCommonPoolParallelism() {
        CommonInjector.ensureCommonPoolIsInitialized();
        return commonParallelism;
    }

    /*
     * Recomputation of the common pool: we cannot create it during image generation, because it
     * this time we do not know the number of cores that will be available at run time. Therefore,
     * we create the common pool when it is accessed the first time.
     */
    @Alias @InjectAccessors(CommonInjector.class) //
    static /* final */ ForkJoinPool common;
    @Alias //
    static /* final */ int commonParallelism;

    /**
     * An injected field to replace ForkJoinPool.common.
     *
     * This class is also a convenient place to handle the initialization of "common" and
     * "commonParallelism", which can unfortunately be accessed independently.
     */
    protected static class CommonInjector {

        /**
         * The static field that is used in place of the static field ForkJoinPool.common. This
         * field set the first time it is accessed, when it transitions from null to something, but
         * does not change thereafter. There is no set method for the field.
         */
        protected static AtomicReference<Target_java_util_concurrent_ForkJoinPool> injectedCommon = new AtomicReference<>(null);

        /** The get access method for ForkJoinPool.common. */
        public static ForkJoinPool getCommon() {
            ensureCommonPoolIsInitialized();
            return Util_java_util_concurrent_ForkJoinPool.as_ForkJoinPool(injectedCommon.get());
        }

        /** Ensure that the common pool variables are initialized. */
        protected static void ensureCommonPoolIsInitialized() {
            if (injectedCommon.get() == null) {
                /* "common" and "commonParallelism" have to be set together. */
                /*
                 * This is a simplified version of ForkJoinPool.makeCommonPool(), without the
                 * dynamic class loading for factory and handler based on system properties.
                 */
                int parallelism = Runtime.getRuntime().availableProcessors() - 1;
                if (!SubstrateOptions.MultiThreaded.getValue()) {
                    /*
                     * Using "parallelism = 0" gets me a ForkJoinPool that does not try to start any
                     * threads, which is what I want if I am not multi-threaded.
                     */
                    parallelism = 0;
                }
                if (parallelism > MAX_CAP) {
                    parallelism = MAX_CAP;
                }
                final Target_java_util_concurrent_ForkJoinPool proposedPool = //
                                new Target_java_util_concurrent_ForkJoinPool(parallelism, defaultForkJoinWorkerThreadFactory, null, LIFO_QUEUE, "ForkJoinPool.commonPool-worker-");
                /* The assignment to "injectedCommon" is atomic to prevent races. */
                injectedCommon.compareAndSet(null, proposedPool);
                final ForkJoinPool actualPool = Util_java_util_concurrent_ForkJoinPool.as_ForkJoinPool(injectedCommon.get());
                /*
                 * The assignment to "commonParallelism" can race because multiple assignments are
                 * idempotent once "injectedCommon" is set. This code is a copy of the relevant part
                 * of the static initialization block in ForkJoinPool.
                 */
                commonParallelism = actualPool.getParallelism();
            }
        }
    }

    protected static class Util_java_util_concurrent_ForkJoinPool {

        static ForkJoinPool as_ForkJoinPool(Target_java_util_concurrent_ForkJoinPool tjucfjp) {
            return KnownIntrinsics.unsafeCast(tjucfjp, ForkJoinPool.class);
        }

        static Target_java_util_concurrent_ForkJoinPool as_Target_java_util_concurrent_ForkJoinPool(ForkJoinPool forkJoinPool) {
            return KnownIntrinsics.unsafeCast(forkJoinPool, Target_java_util_concurrent_ForkJoinPool.class);
        }
    }
}

@TargetClass(java.util.concurrent.ForkJoinWorkerThread.class)
final class Target_java_util_concurrent_ForkJoinWorkerThread {
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = Thread.class, name = "threadLocals") //
    private static long THREADLOCALS;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = Thread.class, name = "inheritableThreadLocals") //
    private static long INHERITABLETHREADLOCALS;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = Thread.class, name = "inheritedAccessControlContext") //
    private static long INHERITEDACCESSCONTROLCONTEXT;
}

@TargetClass(java.util.concurrent.ForkJoinTask.class)
@SuppressWarnings("static-method")
final class Target_java_util_concurrent_ForkJoinTask {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "status") //
    private static long STATUS;

    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static final Target_java_util_concurrent_ForkJoinTask_ExceptionNode[] exceptionTable;
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static final ReentrantLock exceptionTableLock;
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static final ReferenceQueue<Object> exceptionTableRefQueue;

    static {
        int exceptionMapCapacity;
        try {
            Field field = ForkJoinTask.class.getDeclaredField("EXCEPTION_MAP_CAPACITY");
            field.setAccessible(true);
            exceptionMapCapacity = field.getInt(null);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException ex) {
            throw VMError.shouldNotReachHere(ex);
        }

        exceptionTableLock = new ReentrantLock();
        exceptionTableRefQueue = new ReferenceQueue<>();
        exceptionTable = new Target_java_util_concurrent_ForkJoinTask_ExceptionNode[exceptionMapCapacity];
    }

    @Substitute
    private Throwable getThrowableException() {
        throw VMError.unimplemented();
    }
}

@TargetClass(value = java.util.concurrent.ForkJoinTask.class, innerClass = "ExceptionNode")
final class Target_java_util_concurrent_ForkJoinTask_ExceptionNode {
}

@TargetClass(className = "java.util.concurrent.SynchronousQueue$TransferStack")
final class Target_java_util_concurrent_SynchronousQueue_TransferStack {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "head") //
    private static long headOffset;
}

@TargetClass(className = "java.util.concurrent.SynchronousQueue$TransferStack$SNode")
final class Target_java_util_concurrent_SynchronousQueue_TransferStack_SNode {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "match") //
    private static long matchOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "next") //
    private static long nextOffset;
}

@TargetClass(className = "java.util.concurrent.SynchronousQueue$TransferQueue")
final class Target_java_util_concurrent_SynchronousQueue_TransferQueue {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "head") //
    private static long headOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "tail") //
    private static long tailOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "cleanMe") //
    private static long cleanMeOffset;
}

@TargetClass(className = "java.util.concurrent.SynchronousQueue$TransferQueue$QNode")
final class Target_java_util_concurrent_SynchronousQueue_TransferQueue_QNode {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "item") //
    private static long itemOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "next") //
    private static long nextOffset;
}

@TargetClass(java.util.concurrent.atomic.AtomicStampedReference.class)
final class Target_java_util_concurrent_atomic_AtomicStampedReference {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "pair") //
    private static long pairOffset;
}

@TargetClass(java.util.concurrent.atomic.AtomicMarkableReference.class)
final class Target_java_util_concurrent_atomic_AtomicMarkableReference {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "pair") //
    private static long pairOffset;
}

@TargetClass(java.lang.invoke.MethodType.class)
final class Target_java_lang_invoke_MethodType {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "ptypes") //
    private static long ptypesOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "rtype") //
    private static long rtypeOffset;
}

@TargetClass(java.util.concurrent.ThreadLocalRandom.class)
final class Target_java_util_concurrent_ThreadLocalRandom {
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = Thread.class, name = "threadLocalRandomSeed") //
    private static long SEED;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = Thread.class, name = "threadLocalRandomProbe") //
    private static long PROBE;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = Thread.class, name = "threadLocalRandomSecondarySeed") //
    private static long SECONDARY;

}

@TargetClass(java.util.concurrent.CompletableFuture.class)
final class Target_java_util_concurrent_CompletableFuture {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "result") //
    private static long RESULT;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "stack") //
    private static long STACK;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.CompletableFuture$Completion", name = "next") //
    private static long NEXT;
}

@TargetClass(java.util.concurrent.CountedCompleter.class)
final class Target_java_util_concurrent_CountedCompleter {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "pending") //
    private static long PENDING;
}

@TargetClass(java.util.concurrent.Exchanger.class)
final class Target_java_util_concurrent_Exchanger {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "bound") //
    private static long BOUND;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "slot") //
    private static long SLOT;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClassName = "java.util.concurrent.Exchanger$Node", name = "match") //
    private static long MATCH;
    @Alias @RecomputeFieldValue(kind = FieldOffset, declClass = Thread.class, name = "parkBlocker") //
    private static long BLOCKER;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = ExchangerABASEComputer.class) //
    private static /* final */ int ABASE;

}

/**
 * Recomputation of Exchanger.ABASE. We do not have a built-in recomputation because it involves
 * arithmetic. But we can still do it once during native image generation, since it only depends on
 * values that do not change at run time.
 */
@Platforms(Platform.HOSTED_ONLY.class)
class ExchangerABASEComputer implements RecomputeFieldValue.CustomFieldValueComputer {

    @Override
    public Object compute(ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        try {
            ObjectLayout layout = ImageSingletons.lookup(ObjectLayout.class);

            /*
             * ASHIFT is a hard-coded constant in the original implementation, so there is no need
             * to recompute it. It is a private field, so we need reflection to access it.
             */
            Field ashiftField = java.util.concurrent.Exchanger.class.getDeclaredField("ASHIFT");
            ashiftField.setAccessible(true);
            int ashift = ashiftField.getInt(null);

            /*
             * The original implementation uses Node[].class, but we know that all Object arrays
             * have the same kind and layout. The kind denotes the element type of the array.
             */
            JavaKind ak = JavaKind.Object;

            // ABASE absorbs padding in front of element 0
            int abase = layout.getArrayBaseOffset(ak) + (1 << ashift);
            /* Sanity check. */
            final int s = layout.getArrayIndexScale(ak);
            if ((s & (s - 1)) != 0 || s > (1 << ashift)) {
                throw VMError.shouldNotReachHere("Unsupported array scale");
            }

            return abase;
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}

@TargetClass(className = "java.util.concurrent.ForkJoinWorkerThread$InnocuousForkJoinWorkerThread")
final class Target_java_util_concurrent_ForkJoinWorkerThread_InnocuousForkJoinWorkerThread {

    /**
     * TODO: ForkJoinWorkerThread.InnocuousForkJoinWorkerThread.innocuousThreadGroup is initialized
     * by calling ForkJoinWorkerThread.InnocuousForkJoinWorkerThread.createThreadGroup() which uses
     * runtime reflection to (I think) create a ThreadGroup that is a child of the ThreadGroup of
     * the current thread. I do not think I want the ThreadGroup that was created to initialize this
     * field during native image generation. Since SubstrateVM does not implement runtime
     * reflection, I can not call createThreadGroup() to initialize this field later. If it turns
     * out that this field is used, then I will have to think about what to do here. For now, I
     * annotate the field as being deleted to catch an attempts to use the field.
     */
    @Delete //
    private static ThreadGroup innocuousThreadGroup;
}

@TargetClass(className = "java.util.concurrent.ForkJoinPool$WorkQueue")
final class Target_java_util_concurrent_ForkJoinPool_WorkQueue {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "top") //
    private static long QTOP;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "qlock") //
    private static long QLOCK;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "currentSteal") //
    private static long QCURRENTSTEAL;
    @Alias @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = ForkJoinTask[].class) //
    private static int ABASE;
    @Alias @RecomputeFieldValue(kind = ArrayIndexShift, declClass = ForkJoinTask[].class) //
    private static /* final */ int ASHIFT;
}

@TargetClass(java.util.concurrent.FutureTask.class)
final class Target_java_util_concurrent_FutureTask {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "state") //
    private static long stateOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "runner") //
    private static long runnerOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "waiters") //
    private static long waitersOffset;
}

@TargetClass(java.util.concurrent.LinkedTransferQueue.class)
final class Target_java_util_concurrent_LinkedTransferQueue {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "head") //
    private static long headOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "tail") //
    private static long tailOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "sweepVotes") //
    private static long sweepVotesOffset;
}

@TargetClass(className = "java.util.concurrent.LinkedTransferQueue$Node")
final class Target_java_util_concurrent_LinkedTransferQueue_Node {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "item") //
    private static long itemOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "next") //
    private static long nextOffset;
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "waiter") //
    private static long waiterOffset;
}

@TargetClass(java.util.concurrent.Phaser.class)
final class Target_java_util_concurrent_Phaser {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "state") //
    private static long stateOffset;
}

@TargetClass(java.util.concurrent.PriorityBlockingQueue.class)
final class Target_java_util_concurrent_PriorityBlockingQueue {
    @Alias @RecomputeFieldValue(kind = FieldOffset, name = "allocationSpinLock") //
    private static long allocationSpinLockOffset;
}

/** Dummy class to have a class with the file's name. */
public final class RecomputedFields {
}
