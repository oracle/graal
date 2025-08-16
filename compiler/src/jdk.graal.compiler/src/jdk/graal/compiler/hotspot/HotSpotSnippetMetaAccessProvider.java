/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import static jdk.graal.compiler.core.common.NativeImageSupport.inRuntimeCode;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import jdk.graal.compiler.debug.GraalError;
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

public class HotSpotSnippetMetaAccessProvider implements MetaAccessProvider {
    private final MetaAccessProvider delegate;

    public HotSpotSnippetMetaAccessProvider(MetaAccessProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public ResolvedJavaType lookupJavaType(Class<?> clazz) {
        if (inRuntimeCode()) {
            ResolvedJavaType type = HotSpotReplacementsImpl.getEncodedSnippets().lookupSnippetType(clazz);
            if (type != null) {
                return type;
            }
        }
        return delegate.lookupJavaType(clazz);
    }

    @Override
    public ResolvedJavaMethod lookupJavaMethod(Executable reflectionMethod) {
        return delegate.lookupJavaMethod(reflectionMethod);
    }

    @Override
    public ResolvedJavaField lookupJavaField(Field reflectionField) {
        return delegate.lookupJavaField(reflectionField);
    }

    @Override
    public ResolvedJavaType lookupJavaType(JavaConstant constant) {
        if (constant instanceof SnippetObjectConstant objectConstant) {
            Class<?> clazz = objectConstant.asObject(Object.class).getClass();
            if (HotSpotReplacementsImpl.isGraalClass(clazz)) {
                ResolvedJavaType type = HotSpotReplacementsImpl.getEncodedSnippets().lookupSnippetType(clazz);
                GraalError.guarantee(type != null, "Type of compiler object %s missing from encoded snippet types: %s", constant, clazz.getName());
                return type;
            }
            return delegate.lookupJavaType(clazz);
        }
        return delegate.lookupJavaType(constant);
    }

    @Override
    public long getMemorySize(JavaConstant constant) {
        return delegate.getMemorySize(constant);
    }

    @Override
    public Signature parseMethodDescriptor(String methodDescriptor) {
        return delegate.parseMethodDescriptor(methodDescriptor);
    }

    @Override
    public JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int debugId) {
        return delegate.encodeDeoptActionAndReason(action, reason, debugId);
    }

    @Override
    public JavaConstant encodeSpeculation(SpeculationLog.Speculation speculation) {
        return delegate.encodeSpeculation(speculation);
    }

    @Override
    public SpeculationLog.Speculation decodeSpeculation(JavaConstant constant, SpeculationLog speculationLog) {
        return delegate.decodeSpeculation(constant, speculationLog);
    }

    @Override
    public DeoptimizationReason decodeDeoptReason(JavaConstant constant) {
        return delegate.decodeDeoptReason(constant);
    }

    @Override
    public DeoptimizationAction decodeDeoptAction(JavaConstant constant) {
        return delegate.decodeDeoptAction(constant);
    }

    @Override
    public int decodeDebugId(JavaConstant constant) {
        return delegate.decodeDebugId(constant);
    }

    @Override
    public int getArrayBaseOffset(JavaKind elementKind) {
        return delegate.getArrayBaseOffset(elementKind);
    }

    @Override
    public int getArrayIndexScale(JavaKind elementKind) {
        return delegate.getArrayIndexScale(elementKind);
    }
}
