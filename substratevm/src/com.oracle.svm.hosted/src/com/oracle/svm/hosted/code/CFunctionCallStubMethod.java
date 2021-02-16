/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * Call stub for invoking C functions via methods annotated with {@link CFunction}.
 */
public final class CFunctionCallStubMethod extends CCallStubMethod {
    private final CGlobalDataInfo linkage;

    CFunctionCallStubMethod(ResolvedJavaMethod original, CGlobalDataInfo linkage, int newThreadStatus) {
        super(original, newThreadStatus);
        this.linkage = linkage;
    }

    @Override
    protected String getCorrespondingAnnotationName() {
        return getAnnotationClass().getSimpleName();
    }

    private Class<?> getAnnotationClass() {
        if (original.getAnnotation(CFunction.class) != null) {
            return CFunction.class;
        } else {
            throw VMError.shouldNotReachHere("Method is not annotated with @" + CFunction.class.getSimpleName());
        }
    }

    @Override
    public boolean allowRuntimeCompilation() {
        /*
         * C function calls that need a transition cannot be runtime compiled (and cannot be inlined
         * during runtime compilation). Deoptimization could be required while we are blocked in
         * native code, which means the deoptimization stub would need to do the native-to-Java
         * transition.
         */
        boolean needsTransition = StatusSupport.isValidStatus(newThreadStatus);
        return !needsTransition;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        assert purpose != Purpose.PREPARE_RUNTIME_COMPILATION || allowRuntimeCompilation();

        return super.buildGraph(debug, method, providers, purpose);
    }

    @Override
    protected ValueNode createTargetAddressNode(HostedGraphKit kit, HostedProviders providers, List<ValueNode> arguments) {
        return kit.unique(new CGlobalDataLoadAddressNode(linkage));
    }

    @Override
    protected JavaType[] getParameterTypesForLoad(ResolvedJavaMethod method) {
        return method.toParameterTypes(); // include a potential receiver
    }

    @Override
    protected Signature adaptSignatureAndConvertArguments(HostedProviders providers, NativeLibraries nativeLibraries,
                    HostedGraphKit kit, ResolvedJavaMethod method, JavaType returnType, JavaType[] paramTypes, List<ValueNode> arguments) {
        JavaType[] adaptedParamTypes = paramTypes;
        if (method.hasReceiver()) {
            // For non-static methods, we ignore the receiver.
            arguments.remove(0);
            adaptedParamTypes = Arrays.copyOfRange(adaptedParamTypes, 1, adaptedParamTypes.length);
        }
        return super.adaptSignatureAndConvertArguments(providers, nativeLibraries, kit, method, returnType, adaptedParamTypes, arguments);
    }
}
