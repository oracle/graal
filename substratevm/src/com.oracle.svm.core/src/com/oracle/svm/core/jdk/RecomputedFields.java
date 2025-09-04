/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.AtomicFieldUpdaterOffset;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Reset;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.CharsetDecoder;
import java.util.concurrent.ForkJoinPool;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.BasedOnJDKFile;

import jdk.internal.misc.Unsafe;

/*
 * This file contains JDK fields that need to be intercepted because their value in the hosted environment is not
 * suitable for the Substrate VM. The list is derived from the intercepted fields of the Maxine VM.
 */

@TargetClass(java.nio.charset.CharsetEncoder.class)
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+23/src/java.base/share/classes/java/nio/charset/Charset-X-Coder.java.template")
final class Target_java_nio_charset_CharsetEncoder {
    @Alias @RecomputeFieldValue(kind = Reset) //
    private WeakReference<CharsetDecoder> cachedDecoder;
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicReferenceFieldUpdater$AtomicReferenceFieldUpdaterImpl")
final class Target_java_util_concurrent_atomic_AtomicReferenceFieldUpdater_AtomicReferenceFieldUpdaterImpl {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;

    /** the same as tclass, used for checks */
    @Alias private Class<?> cclass;

    /** class holding the field */
    @Alias private Class<?> tclass;

    /** field value type */
    @Alias private Class<?> vclass;

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

        if (Modifier.isStatic(modifiers))
            throw new IllegalArgumentException("Must not be a static field");

        // access checks are disabled
        this.cclass = tclass;
        this.tclass = tclass;
        this.vclass = vclass;
        this.offset = Unsafe.getUnsafe().objectFieldOffset(field);
    }
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicIntegerFieldUpdater$AtomicIntegerFieldUpdaterImpl")
final class Target_java_util_concurrent_atomic_AtomicIntegerFieldUpdater_AtomicIntegerFieldUpdaterImpl {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;

    /** the same as tclass, used for checks */
    @Alias private Class<?> cclass;
    /** class holding the field */
    @Alias private Class<?> tclass;

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

        if (Modifier.isStatic(modifiers))
            throw new IllegalArgumentException("Must not be a static field");

        // access checks are disabled
        this.cclass = tclass;
        this.tclass = tclass;
        this.offset = Unsafe.getUnsafe().objectFieldOffset(field);
    }
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicLongFieldUpdater$CASUpdater")
final class Target_java_util_concurrent_atomic_AtomicLongFieldUpdater_CASUpdater {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;

    /** the same as tclass, used for checks */
    @Alias private Class<?> cclass;
    /** class holding the field */
    @Alias private Class<?> tclass;

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

        if (Modifier.isStatic(modifiers))
            throw new IllegalArgumentException("Must not be a static field");

        // access checks are disabled
        this.cclass = tclass;
        this.tclass = tclass;
        this.offset = Unsafe.getUnsafe().objectFieldOffset(field);
    }

}

@AutomaticallyRegisteredFeature
@Platforms(InternalPlatform.NATIVE_ONLY.class)
class InnocuousForkJoinWorkerThreadFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).initializeAtRunTime(access.findClassByName("java.util.concurrent.ForkJoinWorkerThread$InnocuousForkJoinWorkerThread"),
                        "innocuousThreadGroup must be initialized at run time");
    }
}

@TargetClass(java.util.concurrent.ForkJoinPool.class)
@SuppressWarnings("unused") //
final class Target_java_util_concurrent_ForkJoinPool {

    /*
     * Recomputation of the common pool: we cannot create it during image generation, because at
     * this time we do not know the number of cores that will be available at run time. Therefore,
     * we create the common pool when it is accessed the first time.
     *
     * Note that re-running the class initializer of ForkJoinPool does not work because the class
     * initializer does several other things that we do not support at run time.
     */
    @Alias @InjectAccessors(ForkJoinPoolCommonAccessor.class) //
    static ForkJoinPool common;

    @Substitute
    public static int getCommonPoolParallelism() {
        /*
         * The field for common parallelism is only accessed via this method, so a substitution
         * provides a convenient place to ensure that the common pool is initialized.
         */
        return ForkJoinPoolCommonAccessor.get().getParallelism();
    }

    @Alias //
    Target_java_util_concurrent_ForkJoinPool(byte forCommonPoolOnly) {
    }

    @Alias //
    public static native ForkJoinPool asyncCommonPool();
}

/**
 * An injected field to replace ForkJoinPool.common.
 *
 * This class is also a convenient place to handle the initialization of "common" and
 * "commonParallelism", which can unfortunately be accessed independently.
 */
class ForkJoinPoolCommonAccessor {

    /**
     * The static field that is used in place of the static field ForkJoinPool.common. This field
     * set the first time it is accessed, when it transitions from null to something, but does not
     * change thereafter.
     */
    private static volatile ForkJoinPool injectedCommon;

    static volatile int commonParallelism;

    /** The get access method for ForkJoinPool.common. */
    static ForkJoinPool get() {
        ForkJoinPool result = injectedCommon;
        if (result == null) {
            result = initializeCommonPool();
        }
        return result;
    }

    private static synchronized ForkJoinPool initializeCommonPool() {
        ForkJoinPool result = injectedCommon;
        if (result == null) {
            result = SubstrateUtil.cast(new Target_java_util_concurrent_ForkJoinPool((byte) 0), ForkJoinPool.class);
            injectedCommon = result;
        }
        return result;
    }
}

/** Dummy class to have a class with the file's name. */
public final class RecomputedFields {
}
