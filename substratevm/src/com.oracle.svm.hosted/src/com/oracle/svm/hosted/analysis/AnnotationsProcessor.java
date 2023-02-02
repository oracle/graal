/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.hub.AnnotationsEncoding;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;

public class AnnotationsProcessor {

    public static Object encodeAnnotations(AnalysisMetaAccess metaAccess, Annotation[] allAnnotations, Annotation[] declaredAnnotations, Object oldEncoding) {
        Object newEncoding;
        if (allAnnotations.length == 0 && declaredAnnotations.length == 0) {
            newEncoding = null;
        } else {
            Set<Annotation> all = new HashSet<>();
            Collections.addAll(all, allAnnotations);
            Collections.addAll(all, declaredAnnotations);
            final Set<Annotation> usedAnnotations = all.stream()
                            .filter(a -> !SubstitutionReflectivityFilter.shouldExclude(a.annotationType(), metaAccess, metaAccess.getUniverse()))
                            .filter(a -> {
                                try {
                                    AnalysisType annotationClass = metaAccess.lookupJavaType(a.getClass());
                                    return isAnnotationUsed(annotationClass);
                                } catch (AnalysisError.TypeNotFoundError e) {
                                    /*
                                     * Silently ignore the annotation if its type was not discovered
                                     * by the static analysis.
                                     */
                                    return false;
                                }
                            }).collect(Collectors.toSet());
            Set<Annotation> usedDeclared = filterUsedAnnotation(usedAnnotations, declaredAnnotations);
            newEncoding = usedAnnotations.size() == 0
                            ? null
                            : AnnotationsEncoding.encodeAnnotations(usedAnnotations, usedDeclared);
        }

        /*
         * Return newEncoding only if the value is different from oldEncoding. Without this guard,
         * for tests that do runtime compilation, the field appears as being continuously updated
         * during BigBang.checkObjectGraph.
         */
        if (oldEncoding != null && oldEncoding.equals(newEncoding)) {
            return oldEncoding;
        } else {
            return newEncoding;
        }
    }

    /**
     * We only want annotations in the native image heap that are "used" at run time. In our case,
     * "used" means that the annotation interface is used at least in a type check. This leaves one
     * case where Substrate VM behaves differently than a normal Java VM: When you just query the
     * number of annotations on a class, then we might return a lower number.
     */
    private static boolean isAnnotationUsed(AnalysisType annotationType) {
        if (annotationType.isReachable()) {
            return true;
        }
        assert annotationType.getInterfaces().length == 1 : annotationType;

        AnalysisType annotationInterfaceType = annotationType.getInterfaces()[0];
        return annotationInterfaceType.isReachable();
    }

    private static Set<Annotation> filterUsedAnnotation(Set<Annotation> used, Annotation[] rest) {
        if (rest == null) {
            return null;
        }

        Set<Annotation> restUsed = new HashSet<>();
        for (Annotation a : rest) {
            if (used.contains(a)) {
                restUsed.add(a);
            }
        }
        return restUsed;
    }

}
