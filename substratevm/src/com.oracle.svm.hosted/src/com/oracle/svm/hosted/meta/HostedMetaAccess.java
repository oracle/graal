/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import static com.oracle.svm.core.config.ConfigurationValues.getObjectLayout;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.deopt.Deoptimizer;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HostedMetaAccess extends UniverseMetaAccess {

    public HostedMetaAccess(HostedUniverse hUniverse, AnalysisMetaAccess aMetaAccess) {
        super(hUniverse, aMetaAccess);
    }

    @Override
    public HostedType lookupJavaType(Class<?> clazz) {
        return (HostedType) super.lookupJavaType(clazz);
    }

    public Optional<HostedType> optionalLookupJavaType(Class<?> clazz) {
        HostedType result = (HostedType) getTypeCacheEntry(clazz);
        if (result != null) {
            return Optional.of(result);
        }
        Optional<? extends ResolvedJavaType> analysisType = ((AnalysisMetaAccess) getWrapped()).optionalLookupJavaType(clazz);
        if (!analysisType.isPresent()) {
            return Optional.empty();
        }
        result = ((HostedUniverse) getUniverse()).optionalLookup(analysisType.get());
        return Optional.ofNullable(result);
    }

    public List<? extends ResolvedJavaType> optionalLookupJavaTypes(List<Class<?>> types) {
        return types.stream()
                        .map(this::optionalLookupJavaType)
                        .flatMap(optType -> optType.isPresent() ? Stream.of(optType.get()) : Stream.empty())
                        .collect(Collectors.toList());
    }

    @Override
    public HostedMethod lookupJavaMethod(Executable reflectionMethod) {
        return (HostedMethod) super.lookupJavaMethod(reflectionMethod);
    }

    public HostedMethod optionalLookupJavaMethod(Executable reflectionMethod) {
        return ((HostedUniverse) getUniverse()).optionalLookup(getWrapped().lookupJavaMethod(reflectionMethod));
    }

    @Override
    public HostedField lookupJavaField(Field reflectionField) {
        return (HostedField) super.lookupJavaField(reflectionField);
    }

    public HostedField optionalLookupJavaField(Field reflectionField) {
        return ((HostedUniverse) getUniverse()).optionalLookup(getWrapped().lookupJavaField(reflectionField));
    }

    @Override
    public JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int speculationId) {
        return Deoptimizer.encodeDeoptActionAndReason(action, reason, speculationId);
    }

    @Override
    public DeoptimizationAction decodeDeoptAction(JavaConstant constant) {
        return Deoptimizer.decodeDeoptAction(constant);
    }

    @Override
    public DeoptimizationReason decodeDeoptReason(JavaConstant constant) {
        return Deoptimizer.decodeDeoptReason(constant);
    }

    @Override
    public int decodeDebugId(JavaConstant constant) {
        return Deoptimizer.decodeDebugId(constant);
    }

    @Override
    public int getArrayBaseOffset(JavaKind elementKind) {
        return getObjectLayout().getArrayBaseOffset(elementKind);
    }

    @Override
    public int getArrayIndexScale(JavaKind elementKind) {
        return getObjectLayout().getArrayIndexScale(elementKind);
    }
}
