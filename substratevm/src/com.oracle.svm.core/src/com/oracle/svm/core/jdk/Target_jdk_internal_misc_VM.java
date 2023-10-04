/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.internal.misc.Unsafe;

@TargetClass(className = "jdk.internal.misc.VM")
public final class Target_jdk_internal_misc_VM {
    /** Ensure that we do not leak the full set of properties from the image generator. */
    @Delete //
    private static Map<String, String> savedProps;

    @Substitute
    public static String getSavedProperty(String name) {
        return SystemPropertiesSupport.singleton().getSavedProperties().get(name);
    }

    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    public static ClassLoader latestUserDefinedLoader0() {
        return StackTraceUtils.latestUserDefinedClassLoader(KnownIntrinsics.readCallerStackPointer());
    }

    /*
     * Finalizers are not supported, but we still do not want to inherit any counters from the image
     * builder.
     */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    private static int finalRefCount;
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    private static int peakFinalRefCount;

    @Alias @InjectAccessors(DirectMemoryAccessors.class) //
    private static long directMemory;
    @Alias @InjectAccessors(PageAlignDirectMemoryAccessors.class) //
    private static boolean pageAlignDirectMemory;

    @Alias //
    public static native void initLevel(int newVal);

    @Alias //
    public static native int initLevel();
}

final class DirectMemoryAccessors {

    /*
     * Full initialization is two-staged. First, we init directMemory to a static value (25MB) so
     * that initialization of PhysicalMemory has a chance to finish. At that point isInintialized
     * will be false, since we need to (potentially) set the value to the actual configured heap
     * size. That can only be done once PhysicalMemory init completed. We'd introduce a cycle
     * otherwise.
     */
    private static boolean isInitialized;
    private static final int INITIALIZING = 1;
    private static final int INITIALIZED = 2;
    private static final AtomicInteger INIT_COUNT = new AtomicInteger();
    private static final long STATIC_DIRECT_MEMORY_AMOUNT = 25 * 1024 * 1024;
    private static long directMemory;

    static long getDirectMemory() {
        if (!isInitialized) {
            initialize();
        }
        return directMemory;
    }

    private static void initialize() {
        if (INIT_COUNT.get() == INITIALIZED) {
            /*
             * Safeguard for recursive init
             */
            return;
        }
        /*
         * The JDK method VM.saveAndRemoveProperties looks at the system property
         * "sun.nio.MaxDirectMemorySize". However, that property is always set by the Java HotSpot
         * VM to the value of the option -XX:MaxDirectMemorySize, so we do not need to take that
         * system property into account.
         */
        long newDirectMemory = SubstrateOptions.MaxDirectMemorySize.getValue();
        if (newDirectMemory == 0) {
            /*
             * No value explicitly specified. The default in the JDK in this case is the maximum
             * heap size. However, we cannot rely on Runtime.maxMemory() until PhysicalMemory has
             * fully initialized. Runtime.maxMemory() has a dependency on PhysicalMemory.size()
             * which in turn depends on container support which might use NIO. To avoid this cycle,
             * we first initialize the 'directMemory' field to an arbitrary value (25MB), and only
             * use the Runtime.maxMemory() API once PhysicalMemory has fully initialized.
             */
            if (!PhysicalMemory.isInitialized()) {
                /*
                 * While initializing physical memory we might end up back here with an INIT_COUNT
                 * of 1, since we read the directMemory field during container support code
                 * execution which runs when PhysicalMemory is still initializing.
                 */
                VMError.guarantee(INIT_COUNT.get() <= INITIALIZING, "Initial run needs to have init count 0 or 1");
                newDirectMemory = STATIC_DIRECT_MEMORY_AMOUNT; // Static value during initialization
                INIT_COUNT.setRelease(INITIALIZING);
            } else {
                VMError.guarantee(INIT_COUNT.get() <= INITIALIZING, "Runtime.maxMemory() invariant");
                /*
                 * Once we know PhysicalMemory has been properly initialized we can use
                 * Runtime.maxMemory(). Note that we might end up in this branch for code explicitly
                 * using the JDK cgroups code. At that point PhysicalMemory has likely been
                 * initialized.
                 */
                INIT_COUNT.setRelease(INITIALIZED);
                newDirectMemory = Runtime.getRuntime().maxMemory();
            }
        } else {
            /*
             * For explicitly set direct memory we are done
             */
            Unsafe.getUnsafe().storeFence();
            directMemory = newDirectMemory;
            isInitialized = true;
            if (Target_jdk_internal_misc_VM.initLevel() < 1) {
                // only the first accessor needs to set this
                Target_jdk_internal_misc_VM.initLevel(1);
            }
            return;
        }
        VMError.guarantee(newDirectMemory > 0, "New direct memory should be initialized");

        Unsafe.getUnsafe().storeFence();
        directMemory = newDirectMemory;
        if (PhysicalMemory.isInitialized() && INITIALIZED == INIT_COUNT.get()) {
            /*
             * Complete initialization hand-shake once PhysicalMemory is properly initialized. Also
             * set the VM init level to 1 so as to provoke the NIO code to re-set the internal
             * MAX_MEMORY field.
             */
            isInitialized = true;
            if (Target_jdk_internal_misc_VM.initLevel() < 1) {
                // only the first accessor needs to set this
                Target_jdk_internal_misc_VM.initLevel(1);
            }
        }
    }
}

final class PageAlignDirectMemoryAccessors {

    /*
     * Not volatile to avoid a memory barrier when reading the values. Instead, an explicit barrier
     * is inserted when writing the values.
     */
    private static boolean initialized;

    private static boolean pageAlignDirectMemory;

    static boolean getPageAlignDirectMemory() {
        if (!initialized) {
            initialize();
        }
        return pageAlignDirectMemory;
    }

    private static void initialize() {
        pageAlignDirectMemory = Boolean.getBoolean("sun.nio.PageAlignDirectMemory");

        /* Ensure values are published to other threads before marking fields as initialized. */
        Unsafe.getUnsafe().storeFence();
        initialized = true;
    }
}
