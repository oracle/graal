/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.lang.ref.ReferenceQueue;
import java.util.function.Function;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK11OrLater;

@Platforms(Platform.HOSTED_ONLY.class)
class Package_jdk_internal_ref implements Function<TargetClass, String> {
    @Override
    public String apply(TargetClass annotation) {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            return "sun.misc." + annotation.className();
        } else {
            return "jdk.internal.ref." + annotation.className();
        }
    }
}

@TargetClass(classNameProvider = Package_jdk_internal_ref.class, className = "Cleaner")
public final class Target_jdk_internal_ref_Cleaner {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    static Target_jdk_internal_ref_Cleaner first;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)//
    static ReferenceQueue<Object> dummyQueue = new ReferenceQueue<>();

    @Alias
    native void clean();
}

/**
 * On JDK11+, the cleaner infrastructure is quite different from JDK8:
 * <ul>
 * <li>java.lang.ref.Cleaner: starts a new thread to process its reference queue.</li>
 * <li>jdk.internal.ref.CleanerFactory: provides a common cleaner with a shared cleaner thread. In
 * native-image, we do not necessarily spawn a separate thread for processing references, but may
 * drain the queue after garbage collection.</li>
 * <li>jdk.internal.ref.Cleaner: this only seems to be used by DirectByteBuffer but at least the
 * handling is the same as on JDK 8.
 * </ul>
 */
@TargetClass(className = "jdk.internal.ref.CleanerFactory", onlyWith = JDK11OrLater.class)
final class Target_jdk_internal_ref_CleanerFactory {
    @Alias
    public static native Target_java_lang_ref_Cleaner cleaner();
}

@TargetClass(className = "java.lang.ref.Cleaner", onlyWith = JDK11OrLater.class)
final class Target_java_lang_ref_Cleaner {
    @Alias//
    public Target_jdk_internal_ref_CleanerImpl impl;
}

@TargetClass(className = "java.lang.ref.Cleaner$Cleanable", onlyWith = JDK11OrLater.class)
final class Target_java_lang_ref_Cleaner_Cleanable {
    @Alias
    native void clean();
}

@TargetClass(className = "jdk.internal.ref.CleanerImpl", onlyWith = JDK11OrLater.class)
final class Target_jdk_internal_ref_CleanerImpl {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.ref.CleanerImpl$PhantomCleanableRef")//
    Target_jdk_internal_ref_PhantomCleanable phantomCleanableList;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.ref.CleanerImpl$WeakCleanableRef")//
    Target_jdk_internal_ref_WeakCleanable weakCleanableList;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.ref.CleanerImpl$SoftCleanableRef")//
    Target_jdk_internal_ref_SoftCleanable softCleanableList;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "java.lang.ref.ReferenceQueue")//
    public ReferenceQueue<Object> queue;
}

@TargetClass(className = "jdk.internal.ref.PhantomCleanable", onlyWith = JDK11OrLater.class)
final class Target_jdk_internal_ref_PhantomCleanable {
}

@TargetClass(className = "jdk.internal.ref.WeakCleanable", onlyWith = JDK11OrLater.class)
final class Target_jdk_internal_ref_WeakCleanable {
}

@TargetClass(className = "jdk.internal.ref.SoftCleanable", onlyWith = JDK11OrLater.class)
final class Target_jdk_internal_ref_SoftCleanable {
}
