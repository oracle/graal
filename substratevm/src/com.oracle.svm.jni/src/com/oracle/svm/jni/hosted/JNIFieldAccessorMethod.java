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
import com.oracle.svm.core.c.function.CEntryPointOptions.AutomaticPrologueBailout;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode.LeaveAction;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.EntryPointCallStubMethod;
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

/**
 * Generated method for accessing a field via JNI. An accessor is specific to the {@link JavaKind
 * basic type} of the field, to static or non-static fields, and can either read or write the field.
 * 
 * The generated method implements one of the following JNI functions:
 * 
 * <ul>
 * <li>{@code GetObjectField}</li>
 * <li>{@code GetBooleanField}</li>
 * <li>{@code GetByteField}</li>
 * <li>{@code GetCharField}</li>
 * <li>{@code GetShortField}</li>
 * <li>{@code GetIntField}</li>
 * <li>{@code GetLongField}</li>
 * <li>{@code GetFloatField}</li>
 * <li>{@code GetDoubleField}</li>
 * <li>{@code SetObjectField}</li>
 * <li>{@code SetBooleanField}</li>
 * <li>{@code SetByteField}</li>
 * <li>{@code SetCharField}</li>
 * <li>{@code SetShortField}</li>
 * <li>{@code SetIntField}</li>
 * <li>{@code SetLongField}</li>
 * <li>{@code SetFloatField}</li>
 * <li>{@code SetDoubleField}</li>
 * <li>{@code GetStaticObjectField}</li>
 * <li>{@code GetStaticBooleanField}</li>
 * <li>{@code GetStaticByteField}</li>
 * <li>{@code GetStaticCharField}</li>
 * <li>{@code GetStaticShortField}</li>
 * <li>{@code GetStaticIntField}</li>
 * <li>{@code GetStaticLongField}</li>
 * <li>{@code GetStaticFloatField}</li>
 * <li>{@code GetStaticDoubleField}</li>
 * <li>{@code SetStaticObjectField}</li>
 * <li>{@code SetStaticBooleanField}</li>
 * <li>{@code SetStaticByteField}</li>
 * <li>{@code SetStaticCharField}</li>
 * <li>{@code SetStaticShortField}</li>
 * <li>{@code SetStaticIntField}</li>
 * <li>{@code SetStaticLongField}</li>
 * <li>{@code SetStaticFloatField}</li>
 * <li>{@code SetStaticDoubleField}</li>
 * </ul>
 * 
 * @see <a href=
 *      "https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/functions.html">JNI
 *      Functions</a>
 */
public class JNIFieldAccessorMethod extends EntryPointCallStubMethod {

    public static class Factory {
        public JNIFieldAccessorMethod create(JavaKind kind, boolean isSetter, boolean isStatic, ResolvedJavaType generatedMethodClass, ConstantPool constantPool,
                        MetaAccessProvider wrappedMetaAccess) {
            return new JNIFieldAccessorMethod(kind, isSetter, isStatic, generatedMethodClass, constantPool, wrappedMetaAccess);
        }
    }

    protected final JavaKind fieldKind;
    protected final boolean isSetter;
    protected final boolean isStatic;

    protected JNIFieldAccessorMethod(JavaKind fieldKind, boolean isSetter, boolean isStatic, ResolvedJavaType declaringClass, ConstantPool constantPool, MetaAccessProvider metaAccess) {
        super(createName(fieldKind, isSetter, isStatic), declaringClass, createSignature(fieldKind, isSetter, metaAccess), constantPool);
        if (!EnumSet.of(JavaKind.Object, JavaKind.Boolean, JavaKind.Byte, JavaKind.Char, JavaKind.Short,
                        JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double).contains(fieldKind)) {

            throw VMError.shouldNotReachHere();
        }
        this.fieldKind = fieldKind;
        this.isSetter = isSetter;
        this.isStatic = isStatic;
    }

    private static String createName(JavaKind fieldKind, boolean isSetter, boolean isStatic) {
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

    private static SimpleSignature createSignature(JavaKind fieldKind, boolean isSetter, MetaAccessProvider metaAccess) {
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

        ValueNode vmThread = kit.loadLocal(0, getSignature().getParameterKind(0));
        kit.append(CEntryPointEnterNode.enter(vmThread));
        List<ValueNode> arguments = kit.loadArguments(getSignature().toParameterTypes(null));

        ValueNode returnValue = buildGraphBody(kit, arguments, state, providers.getMetaAccess());

        kit.appendStateSplitProxy(state);
        CEntryPointLeaveNode leave = new CEntryPointLeaveNode(LeaveAction.Leave);
        kit.append(leave);
        JavaKind returnKind = isSetter ? JavaKind.Void : fieldKind;
        kit.createReturn(returnValue, returnKind);

        return kit.finalizeGraph();
    }

    protected ValueNode buildGraphBody(JNIGraphKit kit, List<ValueNode> arguments, @SuppressWarnings("unused") FrameStateBuilder state, @SuppressWarnings("unused") MetaAccessProvider metaAccess) {
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
        return returnValue;
    }

    public CEntryPointData createEntryPointData() {
        return CEntryPointData.create(this, CEntryPointData.DEFAULT_NAME, CEntryPointData.DEFAULT_NAME_TRANSFORMATION, "",
                        NoPrologue.class, AutomaticPrologueBailout.class, NoEpilogue.class, FatalExceptionHandler.class, Publish.NotPublished);
    }
}
