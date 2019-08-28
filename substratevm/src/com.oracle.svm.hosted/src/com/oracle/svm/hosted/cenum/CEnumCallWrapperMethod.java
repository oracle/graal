/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.cenum;

import java.lang.reflect.Modifier;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.phases.CInterfaceEnumTool;
import com.oracle.svm.hosted.phases.CInterfaceInvocationPlugin;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Generated code for patching {@link CEnumLookup} annotated methods and calling
 * EnumRuntimeData.convertCToJava(long).
 */
public class CEnumCallWrapperMethod extends CustomSubstitutionMethod {

    private final NativeLibraries nativeLibraries;

    CEnumCallWrapperMethod(NativeLibraries nativeLibraries, ResolvedJavaMethod method) {
        super(method);
        this.nativeLibraries = nativeLibraries;
    }

    @Override
    public int getModifiers() {
        return original.getModifiers() & ~Modifier.NATIVE;
    }

    @Override
    public boolean isSynthetic() {
        return true;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {

        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);
        StructuredGraph graph = kit.getGraph();

        ResolvedJavaType returnType = (ResolvedJavaType) method.getSignature().getReturnType(null);
        ValueNode arg = kit.loadArguments(method.toParameterTypes()).get(0);

        CInterfaceEnumTool tool = new CInterfaceEnumTool(providers.getMetaAccess(), providers.getSnippetReflection());

        JavaKind pushKind = CInterfaceInvocationPlugin.pushKind(method);
        ValueNode returnValue;
        if (method.getAnnotation(CEnumLookup.class) != null) {
            EnumInfo enumInfo = (EnumInfo) nativeLibraries.findElementInfo(returnType);
            JavaKind parameterKind = JavaKind.Int;
            returnValue = tool.createEnumLookupInvoke(kit, returnType, enumInfo, parameterKind, arg);
        } else if (method.getAnnotation(CEnumValue.class) != null) {
            ResolvedJavaType declaringType = method.getDeclaringClass();
            EnumInfo enumInfo = (EnumInfo) nativeLibraries.findElementInfo(declaringType);
            ValueNode invoke = tool.createEnumValueInvoke(kit, enumInfo, returnType.getJavaKind(), arg);

            ValueNode adapted = CInterfaceInvocationPlugin.adaptPrimitiveType(graph, invoke, invoke.stamp(NodeView.DEFAULT).getStackKind(), returnType.getJavaKind(), false);
            Stamp originalStamp = StampFactory.forKind(returnType.getJavaKind());
            returnValue = CInterfaceInvocationPlugin.adaptPrimitiveType(graph, adapted, returnType.getJavaKind(), originalStamp.getStackKind(), false);
        } else {
            throw VMError.shouldNotReachHere();
        }

        kit.getFrameState().push(pushKind, returnValue);
        kit.createReturn(returnValue, pushKind);

        return kit.finalizeGraph();
    }
}
