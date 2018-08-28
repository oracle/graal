/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

//Checkstyle: stop

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.ArrayBaseOffset;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.AtomicFieldUpdaterOffset;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.FromAlias;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Reset;

import java.io.FileDescriptor;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.MappedByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.graalvm.compiler.serviceprovider.GraalServices;
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
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/*
 * This file contains JDK fields that need to be intercepted because their value in the hosted environment is not
 * suitable for the Substrate VM. The list is derived from the intercepted fields of the Maxine VM.
 */

@TargetClass(sun.util.calendar.ZoneInfoFile.class)
final class Target_sun_util_calendar_ZoneInfoFile {
    @Alias @RecomputeFieldValue(kind = FromAlias) //
    private static Map<String, String> aliases = new java.util.HashMap<>();
}

/**
 * We disallow direct byte buffers ({@link MappedByteBuffer} instances) in the image heap, with one
 * exception: we allow 0-length non-file-based buffers. For example, Netty has a singleton empty
 * buffer referenced from a static field, and a lot of Netty classes reference this buffer
 * statically.
 *
 * Such buffers do actually have an address to memory that is allocated during image generation and
 * therefore no longer available at run time. But since the capacity is 0, no memory can ever be
 * accessed. We therefore allow this "dangling" address. However, we must never call free() for that
 * address, so we remove the Cleaner registered for the buffer by resetting the field
 * {@link #cleaner}.
 */
@TargetClass(className = "java.nio.DirectByteBuffer")
@SuppressWarnings("unused")
final class Target_java_nio_DirectByteBuffer {
    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    @RecomputeFieldValue(kind = ArrayBaseOffset, declClass = byte[].class) //
    static long arrayBaseOffset;

    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    Target_jdk_internal_ref_Cleaner cleaner;

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

@TargetClass(java.nio.charset.CharsetEncoder.class)
final class Target_java_nio_charset_CharsetEncoder {
    @Alias @RecomputeFieldValue(kind = Reset) //
    private WeakReference<CharsetDecoder> cachedDecoder;
}

@TargetClass(className = "java.nio.charset.CoderResult$Cache", onlyWith = JDK8OrEarlier.class)
final class Target_java_nio_charset_CoderResult_Cache {
    @Alias @RecomputeFieldValue(kind = Reset) //
    private Map<Integer, WeakReference<CoderResult>> cache;
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicReferenceFieldUpdater$AtomicReferenceFieldUpdaterImpl")
final class Target_java_util_concurrent_atomic_AtomicReferenceFieldUpdater_AtomicReferenceFieldUpdaterImpl {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;

    /** the same as tclass, used for checks */
    @Alias private final Class<?> cclass;

    /** class holding the field */
    @Alias private final Class<?> tclass;

    /** field value type */
    @Alias private final Class<?> vclass;

    // simplified version of the original constructor
    @SuppressWarnings("unused")
    @Substitute
    Target_java_util_concurrent_atomic_AtomicReferenceFieldUpdater_AtomicReferenceFieldUpdaterImpl(
                    final Class<?> tclass, final Class<?> vclass, final String fieldName, final Class<?> caller) {
        final Field field;
        final Class<?> fieldClass;
        final int modifiers;
        try {
            field = tclass.getDeclaredField(fieldName);

            modifiers = field.getModifiers();
            fieldClass = field.getType();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        if (vclass != fieldClass) {
            throw new ClassCastException();
        }
        if (vclass.isPrimitive())
            throw new IllegalArgumentException("Must be reference type");

        if (!Modifier.isVolatile(modifiers))
            throw new IllegalArgumentException("Must be volatile type");

        // access checks are disabled
        this.cclass = tclass;
        this.tclass = tclass;
        this.vclass = vclass;
        this.offset = UnsafeAccess.UNSAFE.objectFieldOffset(field);
    }
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicIntegerFieldUpdater$AtomicIntegerFieldUpdaterImpl")
final class Target_java_util_concurrent_atomic_AtomicIntegerFieldUpdater_AtomicIntegerFieldUpdaterImpl {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;

    /** the same as tclass, used for checks */
    @Alias private final Class<?> cclass;
    /** class holding the field */
    @Alias private final Class<?> tclass;

    // simplified version of the original constructor
    @SuppressWarnings("unused")
    @Substitute
    Target_java_util_concurrent_atomic_AtomicIntegerFieldUpdater_AtomicIntegerFieldUpdaterImpl(final Class<?> tclass,
                    final String fieldName, final Class<?> caller) {
        final Field field;
        final int modifiers;
        try {
            field = tclass.getDeclaredField(fieldName);
            modifiers = field.getModifiers();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        if (field.getType() != int.class)
            throw new IllegalArgumentException("Must be integer type");

        if (!Modifier.isVolatile(modifiers))
            throw new IllegalArgumentException("Must be volatile type");

        // access checks are disabled
        this.cclass = tclass;
        this.tclass = tclass;
        this.offset = UnsafeAccess.UNSAFE.objectFieldOffset(field);
    }
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicLongFieldUpdater$CASUpdater")
final class Target_java_util_concurrent_atomic_AtomicLongFieldUpdater_CASUpdater {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;

    /** the same as tclass, used for checks */
    @Alias private final Class<?> cclass;
    /** class holding the field */
    @Alias private final Class<?> tclass;

    // simplified version of the original constructor
    @SuppressWarnings("unused")
    @Substitute
    Target_java_util_concurrent_atomic_AtomicLongFieldUpdater_CASUpdater(final Class<?> tclass,
                    final String fieldName, final Class<?> caller) {
        final Field field;
        final int modifiers;
        try {
            field = tclass.getDeclaredField(fieldName);
            modifiers = field.getModifiers();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        if (field.getType() != long.class)
            throw new IllegalArgumentException("Must be long type");

        if (!Modifier.isVolatile(modifiers))
            throw new IllegalArgumentException("Must be volatile type");

        // access checks are disabled
        this.cclass = tclass;
        this.tclass = tclass;
        this.offset = UnsafeAccess.UNSAFE.objectFieldOffset(field);
    }

}

@TargetClass(className = "java.util.concurrent.atomic.AtomicLongFieldUpdater$LockedUpdater")
final class Target_java_util_concurrent_atomic_AtomicLongFieldUpdater_LockedUpdater {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;

    /** the same as tclass, used for checks */
    @Alias private final Class<?> cclass;
    /** class holding the field */
    @Alias private final Class<?> tclass;

    // simplified version of the original constructor
    @SuppressWarnings("unused")
    @Substitute
    Target_java_util_concurrent_atomic_AtomicLongFieldUpdater_LockedUpdater(final Class<?> tclass,
                    final String fieldName, final Class<?> caller) {
        Field field = null;
        int modifiers = 0;
        try {
            field = tclass.getDeclaredField(fieldName);
            modifiers = field.getModifiers();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        if (field.getType() != long.class)
            throw new IllegalArgumentException("Must be long type");

        if (!Modifier.isVolatile(modifiers))
            throw new IllegalArgumentException("Must be volatile type");

        // access checks are disabled
        this.cclass = tclass;
        this.tclass = tclass;
        this.offset = UnsafeAccess.UNSAFE.objectFieldOffset(field);
    }
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
        markAsUnsafeAccessed = (field -> access.registerAsUnsafeAccessed(field));
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

@TargetClass(java.util.concurrent.ForkJoinPool.class)
final class Target_java_util_concurrent_ForkJoinPool {

    @Alias static /* final */ int MAX_CAP;

    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    static /* final */ int LIFO_QUEUE;

    @Alias static /* final */ ForkJoinWorkerThreadFactory defaultForkJoinWorkerThreadFactory;

    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    @SuppressWarnings("unused") //
    protected Target_java_util_concurrent_ForkJoinPool(int parallelism, ForkJoinWorkerThreadFactory factory, UncaughtExceptionHandler handler, int mode, String workerNamePrefix) {
    }

    @Alias //
    @TargetElement(onlyWith = JDK9OrLater.class) //
    @SuppressWarnings("unused") //
    private Target_java_util_concurrent_ForkJoinPool(byte forCommonPoolOnly) {
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
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    static /* final */ int commonParallelism;

    @Alias //
    @TargetElement(onlyWith = JDK9OrLater.class) //
    static /* final */ int COMMON_PARALLELISM;

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
                if (GraalServices.Java8OrEarlier) {
                    initializeCommonPool_JDK8OrEarlier();
                } else {
                    initializeCommonPool_JDK9OrLater();
                }
            }
        }

        protected static void initializeCommonPool_JDK8OrEarlier() {
            /* "common" and "commonParallelism" have to be set together. */
            /*
             * This is a simplified version of ForkJoinPool.makeCommonPool(), without the dynamic
             * class loading for factory and handler based on system properties.
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
            final Target_java_util_concurrent_ForkJoinPool proposedPool = new Target_java_util_concurrent_ForkJoinPool(parallelism, defaultForkJoinWorkerThreadFactory, null, LIFO_QUEUE,
                            "ForkJoinPool.commonPool-worker-");
            /* The assignment to "injectedCommon" is atomic to prevent races. */
            injectedCommon.compareAndSet(null, proposedPool);
            final ForkJoinPool actualPool = Util_java_util_concurrent_ForkJoinPool.as_ForkJoinPool(injectedCommon.get());
            /*
             * The assignment to "commonParallelism" can race because multiple assignments are
             * idempotent once "injectedCommon" is set. This code is a copy of the relevant part of
             * the static initialization block in ForkJoinPool.
             */
            commonParallelism = actualPool.getParallelism();
        }

        protected static void initializeCommonPool_JDK9OrLater() {
            /* "common" and "commonParallelism" have to be set together. */
            /*
             * TODO: This should be a simplified version of ForkJoinPool(byte), , without the
             * dynamic class loading for factory and handler based on system properties.
             *
             * Among the problems is that the public ForkJoinPool constructor that takes a
             * `parallelism` argument now throws an `IllegalArgumentException` if passed a `0`.
             */
            throw VMError.unsupportedFeature("Target_java_util_concurrent_ForkJoinPool.CommonInjector.initializeCommonPool_JDK9OrLater()");
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

@TargetClass(java.util.concurrent.ForkJoinTask.class)
@SuppressWarnings("static-method")
final class Target_java_util_concurrent_ForkJoinTask {
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static final Target_java_util_concurrent_ForkJoinTask_ExceptionNode[] exceptionTable;
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static final ReentrantLock exceptionTableLock;
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    private static final ReferenceQueue<Object> exceptionTableRefQueue;

    static {
        exceptionTableLock = new ReentrantLock();
        exceptionTableRefQueue = new ReferenceQueue<>();
        /*
         * JDK 8 has a static final field EXCEPTION_MAP_CAPACITY with value 32, later versions just
         * use 32 hardcoded. To be JDK version independent, we duplicate the hardcoded value.
         */
        exceptionTable = new Target_java_util_concurrent_ForkJoinTask_ExceptionNode[32];
    }

    @Substitute
    private Throwable getThrowableException() {
        throw VMError.unimplemented();
    }
}

@TargetClass(value = java.util.concurrent.ForkJoinTask.class, innerClass = "ExceptionNode")
final class Target_java_util_concurrent_ForkJoinTask_ExceptionNode {
}

@TargetClass(java.util.concurrent.Exchanger.class)
final class Target_java_util_concurrent_Exchanger {

    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = ExchangerABASEComputer.class) //
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
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
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

/** Dummy class to have a class with the file's name. */
public final class RecomputedFields {
}
