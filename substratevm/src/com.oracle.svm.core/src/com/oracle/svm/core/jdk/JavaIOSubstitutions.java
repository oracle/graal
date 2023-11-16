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
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import jdk.graal.compiler.java.LambdaUtils;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.fieldvaluetransformer.NewInstanceFieldValueTransformer;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.reflect.serialize.MissingSerializationRegistrationUtils;

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

    @Substitute
    static ObjectStreamClass lookup(Class<?> cl, boolean all) {
        if (!(all || Serializable.class.isAssignableFrom(cl))) {
            return null;
        }

        if (Serializable.class.isAssignableFrom(cl)) {
            if (!cl.isArray() && !DynamicHub.fromClass(cl).isRegisteredForSerialization()) {
                boolean isLambda = cl.getTypeName().contains(LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING);
                boolean isProxy = Proxy.isProxyClass(cl);
                if (isProxy || isLambda) {
                    var interfaceList = Arrays.stream(cl.getInterfaces())
                                    .map(Class::getTypeName)
                                    .collect(Collectors.joining(", ", "[", "]"));
                    if (isProxy) {
                        MissingSerializationRegistrationUtils.missingSerializationRegistration(cl, "proxy type implementing interfaces: " + interfaceList);
                    } else {
                        MissingSerializationRegistrationUtils.missingSerializationRegistration(cl,
                                        "lambda declared in: " + LambdaUtils.capturingClass(cl.getTypeName()),
                                        "extending interfaces: " + interfaceList);
                    }
                } else {
                    MissingSerializationRegistrationUtils.missingSerializationRegistration(cl, "type " + cl.getTypeName());
                }
            }
        }

        return Target_java_io_ObjectStreamClass_Caches.localDescs0.get(cl);
    }

}

@TargetClass(value = java.io.ObjectStreamClass.class, innerClass = "Caches")
final class Target_java_io_ObjectStreamClass_Caches {

    @TargetElement(onlyWith = JavaIOClassCachePresent.class, name = "localDescs") @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = NewInstanceFieldValueTransformer.class) static Target_java_io_ClassCache<ObjectStreamClass> localDescs0;

    @TargetElement(onlyWith = JavaIOClassCachePresent.class, name = "reflectors") @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = NewInstanceFieldValueTransformer.class) static Target_java_io_ClassCache<?> reflectors0;

    @TargetElement(onlyWith = JavaIOClassCacheAbsent.class) @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class) static ConcurrentMap<?, ObjectStreamClass> localDescs;

    @TargetElement(onlyWith = JavaIOClassCacheAbsent.class) @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class) static ConcurrentMap<?, ?> reflectors;

    @TargetElement(onlyWith = JavaIOClassCacheAbsent.class) @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ReferenceQueue.class) private static ReferenceQueue<Class<?>> localDescsQueue;

    @TargetElement(onlyWith = JavaIOClassCacheAbsent.class) @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ReferenceQueue.class) private static ReferenceQueue<Class<?>> reflectorsQueue;
}

@TargetClass(className = "java.io.ClassCache", onlyWith = JavaIOClassCachePresent.class)
final class Target_java_io_ClassCache<T> {
    @Alias
    native T get(Class<?> cl);
}

/** Dummy class to have a class with the file's name. */
public final class JavaIOSubstitutions {
}
