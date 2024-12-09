/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.heap;

import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CONSTRUCTOR_NAME;
import static com.oracle.svm.hosted.heap.SVMImageLayerSnapshotUtil.GENERATED_SERIALIZATION;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

import org.capnproto.Text;
import org.capnproto.TextList;
import org.capnproto.Void;

import com.oracle.graal.pointsto.heap.ImageLayerWriter;
import com.oracle.graal.pointsto.heap.ImageLayerWriterHelper;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod.WrappedMethod;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType.WrappedType;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.hosted.code.CEntryPointCallStubMethod;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.FactoryMethod;
import com.oracle.svm.hosted.jni.JNIJavaCallVariantWrapperMethod;
import com.oracle.svm.hosted.reflect.ReflectionExpandSignatureMethod;
import com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor;

import jdk.graal.compiler.java.LambdaUtils;

public class SVMImageLayerWriterHelper extends ImageLayerWriterHelper {
    public SVMImageLayerWriterHelper(ImageLayerWriter imageLayerWriter) {
        super(imageLayerWriter);
    }

    @Override
    protected void persistType(AnalysisType type, PersistedAnalysisType.Builder builder) {
        if (type.toJavaName(true).contains(GENERATED_SERIALIZATION)) {
            WrappedType.SerializationGenerated.Builder b = builder.getWrappedType().initSerializationGenerated();
            var key = SerializationSupport.singleton().getKeyFromConstructorAccessorClass(type.getJavaClass());
            b.setRawDeclaringClass(key.getDeclaringClass().getName());
            b.setRawTargetConstructor(key.getTargetConstructorClass().getName());
        } else if (LambdaUtils.isLambdaType(type)) {
            WrappedType.Lambda.Builder b = builder.getWrappedType().initLambda();
            b.setCapturingClass(LambdaUtils.capturingClass(type.toJavaName()));
        } else if (ProxyRenamingSubstitutionProcessor.isProxyType(type)) {
            builder.getWrappedType().setProxyType(Void.VOID);
        }
        super.persistType(type, builder);
    }

    @Override
    protected void persistMethod(AnalysisMethod method, PersistedAnalysisMethod.Builder builder) {
        if (method.wrapped instanceof FactoryMethod factoryMethod) {
            WrappedMethod.FactoryMethod.Builder b = builder.getWrappedMethod().initFactoryMethod();
            AnalysisMethod targetConstructor = method.getUniverse().lookup(factoryMethod.getTargetConstructor());
            b.setTargetConstructorId(targetConstructor.getId());
            b.setThrowAllocatedObject(factoryMethod.throwAllocatedObject());
            AnalysisType instantiatedType = method.getUniverse().lookup(factoryMethod.getInstantiatedType());
            b.setInstantiatedTypeId(instantiatedType.getId());
        } else if (method.wrapped instanceof CEntryPointCallStubMethod cEntryPointCallStubMethod) {
            WrappedMethod.CEntryPointCallStub.Builder b = builder.getWrappedMethod().initCEntryPointCallStub();
            AnalysisMethod originalMethod = CEntryPointCallStubSupport.singleton().getMethodForStub(cEntryPointCallStubMethod);
            b.setOriginalMethodId(originalMethod.getId());
            b.setNotPublished(cEntryPointCallStubMethod.isNotPublished());
        } else if (method.wrapped instanceof ReflectionExpandSignatureMethod reflectionExpandSignatureMethod) {
            WrappedMethod.WrappedMember.Builder b = builder.getWrappedMethod().initWrappedMember();
            b.setReflectionExpandSignature(Void.VOID);
            Executable member = reflectionExpandSignatureMethod.getMember();
            persistWrappedMember(b, member);
        } else if (method.wrapped instanceof JNIJavaCallVariantWrapperMethod jniJavaCallVariantWrapperMethod) {
            WrappedMethod.WrappedMember.Builder b = builder.getWrappedMethod().initWrappedMember();
            b.setJavaCallVariantWrapper(Void.VOID);
            Executable executable = jniJavaCallVariantWrapperMethod.getMember();
            persistWrappedMember(b, executable);
        }
        super.persistMethod(method, builder);
    }

    @Override
    protected void afterMethodAdded(AnalysisMethod method) {
        if (method.wrapped instanceof FactoryMethod factoryMethod) {
            AnalysisMethod targetConstructor = method.getUniverse().lookup(factoryMethod.getTargetConstructor());
            imageLayerWriter.ensureMethodPersisted(targetConstructor);
        }
        super.afterMethodAdded(method);
    }

    private static void persistWrappedMember(WrappedMethod.WrappedMember.Builder b, Executable member) {
        b.setName(member instanceof Constructor<?> ? CONSTRUCTOR_NAME : member.getName());
        b.setDeclaringClassName(member.getDeclaringClass().getName());
        Parameter[] params = member.getParameters();
        TextList.Builder atb = b.initArgumentTypeNames(params.length);
        for (int i = 0; i < params.length; i++) {
            atb.set(i, new Text.Reader(params[i].getName()));
        }
    }
}
