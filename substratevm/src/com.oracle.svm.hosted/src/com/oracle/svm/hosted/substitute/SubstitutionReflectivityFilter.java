/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.substitute;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Decides whether any class, method or field should not be reflectively accessible (for example, by
 * java.lang.reflect or via JNI) due to substitutions.
 */
public class SubstitutionReflectivityFilter {

    public static boolean shouldExclude(Class<?> classObj, AnalysisMetaAccess metaAccess, AnalysisUniverse universe) {
        try {
            ResolvedJavaType analysisClass = metaAccess.lookupJavaType(classObj);
            if (!universe.hostVM().platformSupported(analysisClass)) {
                return true;
            } else if (analysisClass.isAnnotationPresent(Delete.class)) {
                return true; // accesses would fail at runtime
            }
        } catch (UnsupportedFeatureException ignored) {
            return true; // unsupported platform or deleted: reachability breaks image build
        }
        return false;
    }

    public static boolean shouldExclude(Executable method, AnalysisMetaAccess metaAccess, AnalysisUniverse universe) {
        try {
            AnalysisMethod aMethod = metaAccess.lookupJavaMethod(method);
            if (!universe.hostVM().platformSupported(aMethod)) {
                return true;
            } else if (aMethod.isAnnotationPresent(Delete.class)) {
                return true; // accesses would fail at runtime
            } else if (aMethod.isSynthetic() && aMethod.getDeclaringClass().isAnnotationPresent(TargetClass.class)) {
                /*
                 * Synthetic methods are usually methods injected by javac to provide access to
                 * private fields or methods (access$NNN). In substitution classes, the referenced
                 * members might have been deleted, so do not expose their synthetic methods for
                 * reflection. We could accurately determine affected methods by their graphs, but
                 * these methods should never be relied on anyway.
                 */
                return true;
            }
        } catch (UnsupportedFeatureException ignored) {
            return true; // unsupported platform or deleted: reachability breaks image build
        }
        return false;
    }

    public static boolean shouldExclude(Field field, AnalysisMetaAccess metaAccess, AnalysisUniverse universe) {
        try {
            AnalysisField aField = metaAccess.lookupJavaField(field);
            if (!universe.hostVM().platformSupported(aField)) {
                return true;
            }
            if (aField.isAnnotationPresent(Delete.class) || aField.isAnnotationPresent(InjectAccessors.class)) {
                return true; // accesses would fail at runtime
            }
        } catch (UnsupportedFeatureException ignored) {
            return true; // unsupported platform or deleted: reachability breaks image build
        }
        return false;
    }
}
