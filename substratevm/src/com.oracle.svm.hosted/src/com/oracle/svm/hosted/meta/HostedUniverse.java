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

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.infrastructure.WrappedConstantPool;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.infrastructure.WrappedSignature;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.analysis.Inflation;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Stores all meta data for classes, fields, methods that will be part of the native image. It is
 * constructed after the closed-world analysis from the information provided by the analysis.
 * Nothing is added later on during compilation of methods.
 */
public class HostedUniverse implements Universe {
    protected final Inflation bb;

    protected final Map<AnalysisType, HostedType> types = new HashMap<>();
    protected final Map<AnalysisField, HostedField> fields = new HashMap<>();
    protected final Map<AnalysisMethod, HostedMethod> methods = new HashMap<>();
    protected final Map<Signature, WrappedSignature> signatures = new HashMap<>();
    protected final Map<ConstantPool, WrappedConstantPool> constantPools = new HashMap<>();

    protected EnumMap<JavaKind, HostedType> kindToType = new EnumMap<>(JavaKind.class);

    protected List<HostedType> orderedTypes;
    protected List<HostedMethod> orderedMethods;
    protected List<HostedField> orderedFields;

    /**
     * Number of allocated bits for instanceof checks.
     */
    protected int numInterfaceBits;

    public HostedUniverse(Inflation bb) {
        this.bb = bb;
    }

    public HostedType getType(JavaKind kind) {
        assert kindToType.containsKey(kind);
        return kindToType.get(kind);
    }

    public HostedInstanceClass getObjectClass() {
        HostedInstanceClass result = (HostedInstanceClass) kindToType.get(JavaKind.Object);
        assert result != null;
        return result;
    }

    public synchronized HostedMethod createDeoptTarget(HostedMethod method) {
        if (method.compilationInfo.getDeoptTargetMethod() == null) {
            HostedMethod deoptTarget = new HostedMethod(this, method.getWrapped(), method.getDeclaringClass(), method.getSignature(), method.getConstantPool(), method.getExceptionHandlers());
            assert method.staticAnalysisResults != null;
            deoptTarget.staticAnalysisResults = method.staticAnalysisResults;
            method.compilationInfo.setDeoptTarget(deoptTarget);
        }
        return method.compilationInfo.getDeoptTargetMethod();
    }

    public boolean contains(JavaType type) {
        return types.containsKey(type);
    }

    @Override
    public SVMHost hostVM() {
        return bb.getHostVM();
    }

    @Override
    public SnippetReflectionProvider getSnippetReflection() {
        return bb.getProviders().getSnippetReflection();
    }

    @Override
    public HostedType lookup(JavaType type) {
        JavaType result = lookupAllowUnresolved(type);
        if (result instanceof ResolvedJavaType) {
            return (HostedType) result;
        }
        throw new UnsupportedFeatureException("Unresolved type found. Probably there are some compilation or classpath problems. " + type.toJavaName(true));
    }

    @Override
    public JavaType lookupAllowUnresolved(JavaType type) {
        if (!(type instanceof ResolvedJavaType)) {
            return type;
        }
        assert types.containsKey(type) : type;
        return optionalLookup(type);
    }

    public HostedType optionalLookup(JavaType type) {
        return types.get(type);
    }

    @Override
    public HostedField lookup(JavaField field) {
        JavaField result = lookupAllowUnresolved(field);
        if (result instanceof ResolvedJavaField) {
            return (HostedField) result;
        }
        throw new UnsupportedFeatureException("Unresolved field found. Probably there are some compilation or classpath problems. " + field.format("%H.%n"));
    }

    @Override
    public JavaField lookupAllowUnresolved(JavaField field) {
        if (!(field instanceof ResolvedJavaField)) {
            return field;
        }
        assert fields.containsKey(field) : field;
        return optionalLookup(field);
    }

    public HostedField optionalLookup(JavaField field) {
        return fields.get(field);
    }

    @Override
    public HostedMethod lookup(JavaMethod method) {
        JavaMethod result = lookupAllowUnresolved(method);
        if (result instanceof ResolvedJavaMethod) {
            return (HostedMethod) result;
        }
        throw new UnsupportedFeatureException("Unresolved method found. Probably there are some compilation or classpath problems. " + method.format("%H.%n(%p)"));
    }

    @Override
    public JavaMethod lookupAllowUnresolved(JavaMethod method) {
        if (!(method instanceof ResolvedJavaMethod)) {
            return method;
        }
        assert methods.containsKey(method) : method;
        return optionalLookup(method);
    }

    public HostedMethod optionalLookup(JavaMethod method) {
        return methods.get(method);
    }

    public HostedMethod[] lookup(JavaMethod[] inputs) {
        HostedMethod[] result = new HostedMethod[inputs.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = lookup(inputs[i]);
        }
        return result;
    }

    @Override
    public WrappedSignature lookup(Signature signature, WrappedJavaType defaultAccessingClass) {
        assert signatures.containsKey(signature) : signature;
        return signatures.get(signature);
    }

    @Override
    public WrappedConstantPool lookup(ConstantPool constantPool, WrappedJavaType defaultAccessingClass) {
        assert constantPools.containsKey(constantPool) : constantPool;
        return constantPools.get(constantPool);
    }

    @Override
    public JavaConstant lookup(JavaConstant constant) {
        // There should not be any conversion necessary for constants.
        return constant;
    }

    public Collection<HostedType> getTypes() {
        return orderedTypes;
    }

    public Collection<HostedField> getFields() {
        return orderedFields;
    }

    public Collection<HostedMethod> getMethods() {
        return orderedMethods;
    }

    public Inflation getBigBang() {
        return bb;
    }

    public ConstantReflectionProvider getConstantReflectionProvider() {
        return bb.getConstantReflectionProvider();
    }

    public ConstantFieldProvider getConstantFieldProvider() {
        return bb.getConstantFieldProvider();
    }

    @Override
    public ResolvedJavaMethod resolveSubstitution(ResolvedJavaMethod method) {
        return method;
    }

    @Override
    public HostedType objectType() {
        return types.get(bb.getUniverse().objectType());
    }
}
