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
package com.oracle.graal.pointsto.infrastructure;

import static jdk.vm.ci.common.JVMCIError.unimplemented;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

public class UniverseMetaAccess implements WrappedMetaAccess {
    private final Function<Class<?>, ResolvedJavaType> computeJavaType = new Function<Class<?>, ResolvedJavaType>() {
        @Override
        public ResolvedJavaType apply(Class<?> clazz) {
            return universe.lookup(wrapped.lookupJavaType(clazz));
        }
    };
    private final Universe universe;
    private final MetaAccessProvider wrapped;

    public UniverseMetaAccess(Universe universe, MetaAccessProvider wrapped) {
        this.universe = universe;
        this.wrapped = wrapped;
    }

    @Override
    public MetaAccessProvider getWrapped() {
        return wrapped;
    }

    public Universe getUniverse() {
        return universe;
    }

    @Override
    public ResolvedJavaType lookupJavaType(JavaConstant constant) {
        if (constant.getJavaKind() != JavaKind.Object || constant.isNull()) {
            return null;
        }
        return lookupJavaType(universe.getSnippetReflection().asObject(Object.class, constant).getClass());
    }

    private final ConcurrentHashMap<Class<?>, ResolvedJavaType> typeCache = new ConcurrentHashMap<>(AnalysisUniverse.ESTIMATED_NUMBER_OF_TYPES);

    @Override
    public ResolvedJavaType lookupJavaType(Class<?> clazz) {
        return typeCache.computeIfAbsent(clazz, computeJavaType);
    }

    protected ResolvedJavaType getTypeCacheEntry(Class<?> clazz) {
        return typeCache.get(clazz);
    }

    @Override
    public ResolvedJavaMethod lookupJavaMethod(Executable reflectionMethod) {
        return universe.lookup(wrapped.lookupJavaMethod(reflectionMethod));
    }

    @Override
    public ResolvedJavaField lookupJavaField(Field reflectionField) {
        return universe.lookup(wrapped.lookupJavaField(reflectionField));
    }

    @Override
    public Signature parseMethodDescriptor(String methodDescriptor) {
        throw unimplemented();
    }

    @Override
    public JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int speculationId) {
        throw unimplemented();
    }

    @Override
    public DeoptimizationAction decodeDeoptAction(JavaConstant constant) {
        throw unimplemented();
    }

    @Override
    public DeoptimizationReason decodeDeoptReason(JavaConstant constant) {
        throw unimplemented();
    }

    @Override
    public int decodeDebugId(JavaConstant constant) {
        throw unimplemented();
    }

    @Override
    public int getArrayBaseOffset(JavaKind elementKind) {
        return wrapped.getArrayBaseOffset(elementKind);
    }

    @Override
    public int getArrayIndexScale(JavaKind elementKind) {
        return wrapped.getArrayIndexScale(elementKind);
    }

    @Override
    public long getMemorySize(JavaConstant constant) {
        throw unimplemented();
    }

    @Override
    public JavaConstant encodeSpeculation(Speculation speculation) {
        throw unimplemented();
    }

    @Override
    public Speculation decodeSpeculation(JavaConstant constant, SpeculationLog speculationLog) {
        throw unimplemented();
    }
}
