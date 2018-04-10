/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.nativeimage.c.constant.CEnumLookup;

import com.oracle.graal.pointsto.meta.HostedProviders;
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
public class CEnumLookupCallWrapperMethod extends CustomSubstitutionMethod {
    private NativeLibraries nativeLibraries;

    CEnumLookupCallWrapperMethod(ResolvedJavaMethod method) {
        super(method);
    }

    void setNativeLibraries(NativeLibraries nativeLibraries) {
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

        ResolvedJavaType enumType = (ResolvedJavaType) method.getSignature().getReturnType(null);
        EnumInfo enumInfo = (EnumInfo) nativeLibraries.findElementInfo(enumType);
        JavaKind parameterKind = JavaKind.Int;
        ValueNode arg = kit.loadArguments(method.toParameterTypes()).get(0);

        CInterfaceEnumTool tool = new CInterfaceEnumTool(providers.getMetaAccess(), providers.getSnippetReflection());
        ValueNode piNode = tool.createEnumLookupInvoke(kit, enumType, enumInfo, parameterKind, arg);

        JavaKind pushKind = CInterfaceInvocationPlugin.pushKind(method);
        kit.getFrameState().push(pushKind, piNode);

        kit.createReturn(piNode, pushKind);

        kit.mergeUnwinds();

        assert graph.verify();
        return graph;
    }
}
