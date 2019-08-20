/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.util.Objects;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.hub.AnnotationTypeSupport;
import com.oracle.svm.hosted.FeatureImpl.AfterRegistrationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

@AutomaticFeature
public class AnnotationTypeFeature implements Feature {

    private EconomicSet<Object> repeatableAnnotationClasses = EconomicSet.create();
    private EconomicSet<AnnotatedElement> visitedElements = EconomicSet.create();

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(AnnotationTypeSupport.class, new AnnotationTypeSupport());
        ((AfterRegistrationAccessImpl) access).getImageClassLoader().allAnnotations().stream()
                        .map(a -> a.getAnnotation(Repeatable.class))
                        .filter(Objects::nonNull)
                        .map(Repeatable::value)
                        .forEach(repeatableAnnotationClasses::add);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        DuringAnalysisAccessImpl accessImpl = (DuringAnalysisAccessImpl) access;
        AnalysisUniverse universe = accessImpl.getUniverse();

        /*
         * JDK implementation of repeatable annotations always instantiates an array of a requested
         * annotation. We need to mark arrays of all reachable annotations as in heap.
         */
        universe.getTypes().stream()
                        .filter(AnalysisType::isAnnotation)
                        .filter(AnalysisType::isInTypeCheck)
                        .map(type -> universe.lookup(type.getWrapped()).getArrayClass())
                        .filter(annotationArray -> !annotationArray.isInstantiated())
                        .forEach(annotationArray -> {
                            accessImpl.registerAsInHeap(annotationArray);
                            access.requireAnalysisIteration();
                        });

        Stream<AnnotatedElement> allElements = Stream.concat(Stream.concat(universe.getFields().stream(), universe.getMethods().stream()), universe.getTypes().stream());
        Stream<AnnotatedElement> newElements = allElements.filter(visitedElements::add);
        newElements.forEach(this::reportAnnotation);
    }

    private void reportAnnotation(AnnotatedElement element) {
        for (Annotation annotation : element.getDeclaredAnnotations()) {
            if (repeatableAnnotationClasses.contains(annotation.annotationType())) {
                ImageSingletons.lookup(AnnotationTypeSupport.class).createInstance(annotation.annotationType());
            }
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        repeatableAnnotationClasses.clear();
        visitedElements.clear();
    }
}
