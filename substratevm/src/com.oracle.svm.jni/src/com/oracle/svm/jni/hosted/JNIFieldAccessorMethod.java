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
package com.oracle.svm.jni.hosted;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.nativeimage.c.function.CEntryPoint.FatalExceptionHandler;
import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode.LeaveAction;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Generated method for accessing a field via JNI. An accessor is specific to the {@link JavaKind
 * basic type} of the field, to static or non-static fields, and can either read or write the field.
 */
public final class JNIFieldAccessorMethod extends JNIGeneratedMethod {

    private final JavaKind fieldKind;
    private final boolean isSetter;
    private final boolean isStatic;
    private final ResolvedJavaType declaringClass;
    private final ConstantPool constantPool;
    private final String name;
    private final Signature signature;

    public JNIFieldAccessorMethod(JavaKind fieldKind, boolean isSetter, boolean isStatic, ResolvedJavaType declaringClass, ConstantPool constantPool, MetaAccessProvider metaAccess) {
        if (!EnumSet.of(JavaKind.Object, JavaKind.Boolean, JavaKind.Byte, JavaKind.Char, JavaKind.Short,
                        JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double).contains(fieldKind)) {

            throw VMError.shouldNotReachHere();
        }
        this.fieldKind = fieldKind;
        this.isSetter = isSetter;
        this.isStatic = isStatic;
        this.declaringClass = declaringClass;
        this.constantPool = constantPool;
        this.name = createName();
        this.signature = createSignature(metaAccess);
    }

    private String createName() {
        StringBuilder sb = new StringBuilder(32);
        if (isSetter) {
            sb.append("Set");
        } else {
            sb.append("Get");
        }
        if (isStatic) {
            sb.append("Static");
        }
        sb.append(fieldKind.name());
        sb.append("Field");
        return sb.toString();
    }

    private SimpleSignature createSignature(MetaAccessProvider metaAccess) {
        Class<?> valueClass = fieldKind.toJavaClass();
        if (fieldKind.isObject()) {
            valueClass = JNIObjectHandle.class;
        }
        ResolvedJavaType objectHandle = metaAccess.lookupJavaType(JNIObjectHandle.class);
        List<JavaType> args = new ArrayList<>();
        args.add(metaAccess.lookupJavaType(JNIEnvironment.class));
        args.add(objectHandle); // this (instance field) or class (static field)
        args.add(metaAccess.lookupJavaType(JNIFieldId.class));
        if (isSetter) {
            args.add(metaAccess.lookupJavaType(valueClass));
        }
        ResolvedJavaType returnType;
        if (isSetter) {
            returnType = metaAccess.lookupJavaType(Void.TYPE);
        } else {
            returnType = metaAccess.lookupJavaType(valueClass);
        }
        return new SimpleSignature(args, returnType);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method);
        StructuredGraph graph = kit.getGraph();
        FrameStateBuilder state = new FrameStateBuilder(null, method, graph);
        state.initializeForMethodStart(null, true, providers.getGraphBuilderPlugins());

        ValueNode vmThread = kit.loadLocal(0, signature.getParameterKind(0));
        kit.append(CEntryPointEnterNode.enter(vmThread));

        List<ValueNode> arguments = kit.loadArguments(signature.toParameterTypes(null));
        ValueNode object;
        if (isStatic) {
            if (fieldKind.isPrimitive()) {
                object = kit.getStaticPrimitiveFieldsArray();
            } else {
                object = kit.getStaticObjectFieldsArray();
            }
        } else {
            ValueNode handle = arguments.get(1);
            object = kit.unboxHandle(handle);
        }
        ValueNode fieldId = arguments.get(2);
        ValueNode offset = kit.getFieldOffsetFromId(fieldId);
        ValueNode returnValue;
        if (isSetter) {
            returnValue = null; // void
            ValueNode newValue = arguments.get(3);
            if (fieldKind.isObject()) {
                newValue = kit.unboxHandle(newValue);
            }
            kit.append(new RawStoreNode(object, offset, newValue, fieldKind, LocationIdentity.ANY_LOCATION));
        } else {
            returnValue = kit.append(new RawLoadNode(object, offset, fieldKind, LocationIdentity.ANY_LOCATION));
            if (fieldKind.isObject()) {
                returnValue = kit.boxObjectInLocalHandle(returnValue);
            }
        }
        kit.appendStateSplitProxy(state);
        CEntryPointLeaveNode leave = new CEntryPointLeaveNode(LeaveAction.Leave);
        kit.append(leave);
        JavaKind returnKind = isSetter ? JavaKind.Void : fieldKind;
        kit.createReturn(returnValue, returnKind);

        return kit.finalizeGraph();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
    }

    public CEntryPointData createEntryPointData() {
        return CEntryPointData.create(this, CEntryPointData.DEFAULT_NAME, CEntryPointData.DEFAULT_NAME_TRANSFORMATION, "",
                        NoPrologue.class, NoEpilogue.class, FatalExceptionHandler.class, Publish.NotPublished);
    }
}
