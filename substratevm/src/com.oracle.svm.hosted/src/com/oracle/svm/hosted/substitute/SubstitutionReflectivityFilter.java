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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.util.AnnotationUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Decides whether any class, method or field should not be reflectively accessible (for example, by
 * java.lang.reflect or via JNI) due to substitutions.
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public class SubstitutionReflectivityFilter implements InternalFeature {

    private HostVM hostVM;
    private AnalysisMetaAccess metaAccess;
    private AnalysisUniverse universe;
    private Map<ResolvedJavaType, Set<String>> forbiddenFields;

    public static SubstitutionReflectivityFilter singleton() {
        return ImageSingletons.lookup(SubstitutionReflectivityFilter.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        hostVM = access.getHostVM();
        metaAccess = access.getMetaAccess();
        universe = access.getUniverse();
        /*
         * Those fields are used in allocation snippets, and registering them for reflection breaks
         * the image build
         */
        forbiddenFields = Map.of(metaAccess.lookupJavaType(DynamicHub.class), Set.of("companion", "hubType", "NO_INTERFACE_ID"));
    }

    public boolean shouldExcludeElement(AnnotatedElement annotatedElement) {
        return switch (annotatedElement) {
            case Class<?> classObj -> shouldExclude(classObj);
            case Executable method -> shouldExclude(method);
            case Field field -> shouldExclude(field);
            default -> false;
        };
    }

    public boolean shouldExclude(Class<?> classObj) {
        if (!hostVM.platformSupported(classObj)) {
            return true;
        }
        try {
            ResolvedJavaType analysisClass = metaAccess.lookupJavaType(classObj);
            if (!hostVM.platformSupported(analysisClass)) {
                return true;
            } else if (AnnotationUtil.isAnnotationPresent(analysisClass, Delete.class)) {
                return true; // accesses would fail at runtime
            }
        } catch (UnsupportedFeatureException ignored) {
            return true; // unsupported platform or deleted: reachability breaks image build
        }
        return false;
    }

    public boolean shouldExclude(Executable method) {
        if (shouldExclude(method.getDeclaringClass())) {
            return true;
        }
        if (!hostVM.platformSupported(method)) {
            return true;
        }
        try {
            AnalysisMethod aMethod = metaAccess.lookupJavaMethod(method);
            if (!hostVM.platformSupported(aMethod)) {
                return true;
            } else if (AnnotationUtil.isAnnotationPresent(aMethod, Delete.class)) {
                return true; // accesses would fail at runtime
            } else if (AnnotationUtil.isAnnotationPresent(aMethod, Fold.class)) {
                return true; // accesses can contain hosted elements
            } else if (aMethod.isSynthetic() && AnnotationUtil.isAnnotationPresent(aMethod.getDeclaringClass(), TargetClass.class)) {
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

    public boolean shouldExclude(Field field) {
        if (shouldExclude(field.getDeclaringClass())) {
            return true;
        }
        if (!hostVM.platformSupported(field)) {
            return true;
        }
        try {
            AnalysisField aField = metaAccess.lookupJavaField(field);
            if (!hostVM.platformSupported(aField)) {
                return true;
            }
            if (AnnotationUtil.isAnnotationPresent(aField, Delete.class) || AnnotationUtil.isAnnotationPresent(aField, InjectAccessors.class)) {
                return true; // accesses would fail at runtime
            }
            if (AnnotationUtil.isAnnotationPresent(aField, UnknownObjectField.class) || AnnotationUtil.isAnnotationPresent(aField, UnknownPrimitiveField.class)) {
                return true; // reflective accesses to unknown fields break the image build
            }
            if (forbiddenFields.getOrDefault(metaAccess.lookupJavaType(field.getDeclaringClass()), Collections.emptySet()).contains(field.getName())) {
                return true;
            }
        } catch (UnsupportedFeatureException ignored) {
            return true; // unsupported platform or deleted: reachability breaks image build
        }
        return false;
    }

    public boolean shouldExclude(ResolvedJavaType type) {
        return getFilteredAnalysisType(type) == null;
    }

    public AnalysisType getFilteredAnalysisType(ResolvedJavaType type) {
        Objects.requireNonNull(type);
        try {
            AnalysisType analysisType = type instanceof AnalysisType aType ? aType : universe.lookup(type);
            if (!hostVM.platformSupported(analysisType)) {
                return null;
            } else if (AnnotationUtil.isAnnotationPresent(analysisType, Delete.class)) {
                return null; // accesses would fail at runtime
            }
            return analysisType;
        } catch (UnsupportedFeatureException ignored) {
            return null; // unsupported platform or deleted: reachability breaks image build
        }
    }

    public boolean shouldExclude(ResolvedJavaMethod method) {
        return getFilteredAnalysisMethod(method) == null;
    }

    public AnalysisMethod getFilteredAnalysisMethod(ResolvedJavaMethod method) {
        Objects.requireNonNull(method);
        if (shouldExclude(method.getDeclaringClass())) {
            return null;
        }
        try {
            AnalysisMethod analysisMethod = method instanceof AnalysisMethod aMethod ? aMethod : universe.lookup(method);
            if (!hostVM.platformSupported(analysisMethod)) {
                return null;
            } else if (AnnotationUtil.isAnnotationPresent(analysisMethod, Delete.class)) {
                return null; // accesses would fail at runtime
            } else if (AnnotationUtil.isAnnotationPresent(analysisMethod, Fold.class)) {
                return null; // accesses can contain hosted elements
            } else if (analysisMethod.isSynthetic() && AnnotationUtil.isAnnotationPresent(analysisMethod.getDeclaringClass(), TargetClass.class)) {
                /*
                 * Synthetic methods are usually methods injected by javac to provide access to
                 * private fields or methods (access$NNN). In substitution classes, the referenced
                 * members might have been deleted, so do not expose their synthetic methods for
                 * reflection. We could accurately determine affected methods by their graphs, but
                 * these methods should never be relied on anyway.
                 */
                return null;
            }
            return analysisMethod;
        } catch (UnsupportedFeatureException ignored) {
            return null; // unsupported platform or deleted: reachability breaks image build
        }
    }

    public boolean shouldExclude(ResolvedJavaField field) {
        return getFilteredAnalysisField(field) == null;
    }

    public AnalysisField getFilteredAnalysisField(ResolvedJavaField field) {
        Objects.requireNonNull(field);
        if (shouldExclude(field.getDeclaringClass())) {
            return null;
        }
        try {
            AnalysisField analysisField = field instanceof AnalysisField aField ? aField : universe.lookup(field);
            if (!hostVM.platformSupported(analysisField)) {
                return null;
            }
            if (AnnotationUtil.isAnnotationPresent(analysisField, Delete.class) || AnnotationUtil.isAnnotationPresent(analysisField, InjectAccessors.class)) {
                return null; // accesses would fail at runtime
            }
            if (AnnotationUtil.isAnnotationPresent(analysisField, UnknownObjectField.class) || AnnotationUtil.isAnnotationPresent(analysisField, UnknownPrimitiveField.class)) {
                return null; // reflective accesses to unknown fields break the image build
            }
            if (forbiddenFields.getOrDefault(analysisField.getDeclaringClass(), Collections.emptySet()).contains(analysisField.getName())) {
                return null;
            }
            return analysisField;
        } catch (UnsupportedFeatureException ignored) {
            return null; // unsupported platform or deleted: reachability breaks image build
        }
    }
}
