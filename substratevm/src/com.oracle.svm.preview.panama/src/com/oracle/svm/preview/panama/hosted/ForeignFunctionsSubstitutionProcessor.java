/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.preview.panama.hosted;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * In charge of substituting
 * {@link com.oracle.svm.core.methodhandles.Util_java_lang_invoke_MethodHandle}'s stub with a
 * graph-based implementation.
 */
@Platforms(Platform.HOSTED_ONLY.class)
class ForeignFunctionsSubstitutionProcessor extends SubstitutionProcessor {
    private final Map<ResolvedJavaMethod, ResolvedJavaMethod> methodSubstitutions;

    ForeignFunctionsSubstitutionProcessor(MetaAccessProvider metaAccessProvider) {
        Method linkToNative = ReflectionUtil.lookupMethod(
                        ReflectionUtil.lookupClass(false, "com.oracle.svm.core.methodhandles.Util_java_lang_invoke_MethodHandle"),
                        "linkToNative",
                        Object[].class);
        ResolvedJavaMethod resolvedLinkToNative = metaAccessProvider.lookupJavaMethod(linkToNative);

        this.methodSubstitutions = Map.of(resolvedLinkToNative, new LinkToNative(resolvedLinkToNative));
    }

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        if (methodSubstitutions.containsKey(method)) {
            return methodSubstitutions.get(method);
        }
        return method;
    }

    @Override
    public ResolvedJavaMethod resolve(ResolvedJavaMethod method) {
        if (method instanceof LinkToNative ps) {
            return ps.getOriginal();
        }
        return method;
    }
}

class LinkToNative extends NonBytecodeMethod {

    private final ResolvedJavaMethod original;

    LinkToNative(ResolvedJavaMethod original) {
        super(original.getName(), original.isStatic(), original.getDeclaringClass(), original.getSignature(), original.getConstantPool());
        this.original = original;
    }

    public ResolvedJavaMethod getOriginal() {
        return original;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method, purpose);
        List<ValueNode> arguments = kit.loadArguments(original.toParameterTypes());

        assert arguments.size() == 1;
        ValueNode argumentArray = arguments.get(0);
        ValueNode nep = kit.getLastArrayElement(arguments.get(0), JavaKind.Object);
        ValueNode nepAddress = kit.createLoadField(
                        nep,
                        providers.getMetaAccess().lookupJavaField(ReflectionUtil.lookupField(
                                        ReflectionUtil.lookupClass(true, "jdk.internal.foreign.abi.NativeEntryPoint"),
                                        "downcallStubAddress")));

        ValueNode ret = kit.createIndirectCall(nepAddress, List.of(argumentArray), DowncallStub.createSignature(providers.getMetaAccess()), SubstrateCallingConventionKind.Java);
        kit.createReturn(ret, JavaKind.Object);

        return kit.finalizeGraph();
    }

}
