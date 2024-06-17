/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.reflect.ReflectionDataBuilder;

@AutomaticallyRegisteredFeature
public class AnnotationFeature implements InternalFeature {

    private final Set<Class<? extends Annotation>> processedTypes = ConcurrentHashMap.newKeySet();

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        access.registerObjectReachableCallback(Annotation.class, this::registerDeclaredMethods);
    }

    /**
     * For annotations that are materialized at image run time, all necessary methods are registered
     * for reflection in {@link ReflectionDataBuilder#registerTypesForAnnotation}. But if an
     * annotation type is only used by an annotation that is already in the image heap, then we need
     * to also register its methods for reflection. This is done here by registering a callback
     * which notifies us for every reachable {@link Annotation} object in the heap and then checking
     * if it is an annotation that was materialized by the JDK, i.e., it is a {@link Proxy}.
     */
    @SuppressWarnings("unused")
    private void registerDeclaredMethods(DuringAnalysisAccess access, Annotation annotation, ObjectScanner.ScanReason reason) {
        if (Proxy.isProxyClass(annotation.getClass())) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (processedTypes.add(annotationType)) {
                RuntimeReflection.registerAllDeclaredMethods(annotationType);
                RuntimeReflection.register(annotationType.getDeclaredMethods());
            }
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerSubtypeReachabilityHandler(this::registerArrayClass, Annotation.class);
    }

    /*
     * The JDK implementation of repeatable annotations always instantiates an array of a requested
     * annotation. We need to mark arrays of all reachable annotations as in heap.
     */
    private void registerArrayClass(DuringAnalysisAccess access, Class<?> subclass) {
        if (subclass.isAnnotation()) {
            Class<?> arrayClass = Array.newInstance(subclass, 0).getClass();
            access.registerAsInHeap(arrayClass);
        }
    }
}
