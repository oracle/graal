/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import java.lang.ref.ReferenceQueue;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.DynamicHub;

@TargetClass(java.io.FileDescriptor.class)
final class Target_java_io_FileDescriptor {

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    private List<Closeable> otherParents;
}

@TargetClass(java.io.ObjectStreamClass.class)
final class Target_java_io_ObjectStreamClass {

    @Substitute
    private static boolean hasStaticInitializer(Class<?> cl) {
        return DynamicHub.fromClass(cl).getClassInitializationInfo().hasInitializer();
    }
}

@TargetClass(value = java.io.ObjectStreamClass.class, innerClass = "Caches")
final class Target_java_io_ObjectStreamClass_Caches {

    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class) static ConcurrentMap<?, ?> localDescs;

    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class) static ConcurrentMap<?, ?> reflectors;

    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ReferenceQueue.class) private static ReferenceQueue<Class<?>> localDescsQueue;

    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ReferenceQueue.class) private static ReferenceQueue<Class<?>> reflectorsQueue;
}

/** Dummy class to have a class with the file's name. */
public final class JavaIOSubstitutions {
}
