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
import java.util.Properties;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

@TargetClass(classNameProvider = Package_jdk_internal_misc.class, className = "VM")
public final class Target_jdk_internal_misc_VM {
    /* Ensure that we do not leak the full set of properties from the image generator. */
    @TargetElement(onlyWith = JDK8OrEarlier.class)//
    @Delete //
    private static Properties savedProps;

    @TargetElement(name = "savedProps", onlyWith = JDK11OrLater.class)//
    @Delete //
    private static Map<String, String> savedProps9;

    @Substitute
    public static String getSavedProperty(String name) {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).getSavedProperties().get(name);
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
    @Alias @InjectAccessors(DirectMemoryAccessors.class) //
    private static boolean pageAlignDirectMemory;
}

final class DirectMemoryAccessors {

    /*
     * Not volatile to avoid a memory barrier when reading the values. Instead, an explicit barrier
     * is inserted when writing the values.
     */
    private static boolean initialized;

    private static long directMemory;
    private static boolean pageAlignDirectMemory;

    static long getDirectMemory() {
        if (!initialized) {
            initialize();
        }
        return directMemory;
    }

    static boolean getPageAlignDirectMemory() {
        if (!initialized) {
            initialize();
        }
        return pageAlignDirectMemory;
    }

    private static void initialize() {
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
             * heap size.
             */
            newDirectMemory = Runtime.getRuntime().maxMemory();
        }

        /*
         * The initialization is not synchronized, so multiple threads can race. Usually this will
         * lead to the same value, unless the runtime options are modified concurrently - which is
         * possible but not a case we care about.
         */
        directMemory = newDirectMemory;
        pageAlignDirectMemory = Boolean.getBoolean("sun.nio.PageAlignDirectMemory");

        /* Ensure values are published to other threads before marking fields as initialized. */
        GraalUnsafeAccess.getUnsafe().storeFence();
        initialized = true;
    }
}
