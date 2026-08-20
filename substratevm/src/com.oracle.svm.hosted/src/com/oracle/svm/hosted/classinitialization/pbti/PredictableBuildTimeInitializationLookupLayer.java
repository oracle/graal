/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization.pbti;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.oracle.svm.shared.util.VMError;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedPlatformException;
import com.oracle.graal.pointsto.heap.TypedConstant;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.OriginalFieldProvider;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Lookup layer used by {@link PredictableBuildTimeInitializationFeature} to access substituted
 * fields and types without touching the analysis. The pbti feature can find new types, that
 * the analysis has not yet seen, but accessing them via the {@link com.oracle.graal.pointsto.meta.AnalysisUniverse}
 * can result in an exception being thrown, after the universe is sealed.
 */
public final class PredictableBuildTimeInitializationLookupLayer {

    private final HostVM hostVM;
    private final SubstitutionProcessor substitutions;
    private final MetaAccessProvider wrappedMetaAccess;

    private final ConcurrentMap<ResolvedJavaType, PredictableBuildTimeInitializationType> types = new ConcurrentHashMap<>();
    private final ConcurrentMap<ResolvedJavaField, PredictableBuildTimeInitializationField> fields = new ConcurrentHashMap<>();

    private final JavaKind wordKind;
    private final ResolvedJavaType wordType;
    private final PredictableBuildTimeInitializationType objectType;

    public PredictableBuildTimeInitializationLookupLayer(HostVM hostVM, MetaAccessProvider wrappedMetaAccess, SubstitutionProcessor substitutions, JavaKind wordKind) {
        this.hostVM = hostVM;
        this.substitutions = substitutions;
        this.wrappedMetaAccess = wrappedMetaAccess;

        this.wordKind = wordKind;
        this.wordType = wrappedMetaAccess.lookupJavaType(WordBase.class);
        this.objectType = lookup(wrappedMetaAccess.lookupJavaType(Object.class));
    }

    public HostVM hostVM() {
        return hostVM;
    }

    public SubstitutionProcessor getSubstitutions() {
        return substitutions;
    }

    public MetaAccessProvider getOriginalMetaAccess() {
        return wrappedMetaAccess;
    }

    public JavaKind getWordKind() {
        return wordKind;
    }

    public PredictableBuildTimeInitializationType lookup(JavaConstant constant) {
        if (constant == null || constant.getJavaKind() != JavaKind.Object || constant.isNull()) {
            return null;
        }
        ResolvedJavaType type = constant instanceof TypedConstant typedConstant ? typedConstant.getType() : wrappedMetaAccess.lookupJavaType(constant);
        return lookup(type);
    }

    public PredictableBuildTimeInitializationType lookup(JavaType type) {
        JavaType result = lookupAllowUnresolved(type);
        if (result == null) {
            return null;
        } else if (result instanceof PredictableBuildTimeInitializationType pbtiType) {
            return pbtiType;
        }
        throw VMError.shouldNotReachHere("Unresolved type found: " + type.toJavaName(true));
    }

    public PredictableBuildTimeInitializationType lookup(ResolvedJavaType type) {
        if (type == null) {
            return null;
        }
        if (type instanceof PredictableBuildTimeInitializationType pbtiType) {
            return pbtiType;
        }

        ResolvedJavaType originalType = OriginalClassProvider.getOriginalType(type);
        PredictableBuildTimeInitializationType result = types.get(originalType);
        if (result == null) {
            ResolvedJavaType substitutedType = substitutions.lookup(originalType);
            result = createType(originalType, substitutedType);
        }
        return result;
    }

    private PredictableBuildTimeInitializationType createType(ResolvedJavaType originalType, ResolvedJavaType substitutedType) {
        if (!isPlatformSupported(originalType)) {
            throw new UnsupportedPlatformException("Type is not available in this platform: " + originalType.toJavaName(true));
        }

        JavaKind storageKind;
        if (wordType.isAssignableFrom(originalType)) {
            storageKind = wordKind;
        } else {
            storageKind = substitutedType.getJavaKind();
        }

        var newType = new PredictableBuildTimeInitializationType(this, substitutedType, storageKind);
        return types.computeIfAbsent(originalType, _ -> newType);
    }

    /**
     * Check a type and its interface hierarchy for platform support. The superclass is eagerly
     * looked up by {@link PredictableBuildTimeInitializationType} and checked separately.
     */
    private boolean isPlatformSupported(ResolvedJavaType type) {
        if (!hostVM.platformSupported(type)) {
            return false;
        }

        var interfaces = type.getInterfaces();
        var typeWorklist = new ArrayDeque<>(Arrays.asList(interfaces));

        while (!typeWorklist.isEmpty()) {
            var iface = typeWorklist.poll();
            if (!hostVM.platformSupported(iface)) {
                return false;
            }

            typeWorklist.addAll(Arrays.asList(iface.getInterfaces()));
        }

        return true;
    }

    public JavaType lookupAllowUnresolved(JavaType type) {
        if (!(type instanceof ResolvedJavaType resolvedType)) {
            return type;
        }
        return lookup(resolvedType);
    }

    public List<JavaType> lookup(List<? extends JavaType> inputTypes) {
        if (inputTypes == null) {
            return null;
        }
        return inputTypes.stream().map(this::lookupAllowUnresolved).toList();
    }

    public ResolvedJavaType[] lookup(ResolvedJavaType[] inputTypes) {
        if (inputTypes == null) {
            return null;
        }
        ResolvedJavaType[] result = new ResolvedJavaType[inputTypes.length];
        for (int i = 0; i < inputTypes.length; i++) {
            result[i] = lookup(inputTypes[i]);
        }
        return result;
    }

    public PredictableBuildTimeInitializationType lookup(Class<?> clazz) {
        return lookup(wrappedMetaAccess.lookupJavaType(clazz));
    }

    public PredictableBuildTimeInitializationField lookup(ResolvedJavaField field) {
        if (field == null) {
            return null;
        }
        if (field instanceof PredictableBuildTimeInitializationField pbtiField) {
            assert pbtiField.getLookupLayer() == this : pbtiField;
            return pbtiField;
        }

        ResolvedJavaField originalField = OriginalFieldProvider.getOriginalField(field);
        ResolvedJavaField substitutedField = substitutions.lookup(originalField);
        return fields.computeIfAbsent(originalField, _ -> new PredictableBuildTimeInitializationField(this, substitutedField));
    }

    public ResolvedJavaField[] lookup(ResolvedJavaField[] inputFields) {
        if (inputFields == null) {
            return null;
        }
        ResolvedJavaField[] result = new ResolvedJavaField[inputFields.length];
        for (int i = 0; i < inputFields.length; i++) {
            result[i] = lookup(inputFields[i]);
        }
        return result;
    }

    public PredictableBuildTimeInitializationField lookup(Field field) {
        return lookup(wrappedMetaAccess.lookupJavaField(field));
    }

    public PredictableBuildTimeInitializationType objectType() {
        return objectType;
    }
}
