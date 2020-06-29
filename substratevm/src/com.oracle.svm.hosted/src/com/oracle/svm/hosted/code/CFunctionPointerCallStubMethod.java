/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

// Checkstyle: allow reflection

/**
 * A stub for calling native code generated from a method annotated with
 * {@link InvokeCFunctionPointer}.
 */
public final class CFunctionPointerCallStubMethod extends CCallStubMethod {

    static CFunctionPointerCallStubMethod create(AnalysisMethod aMethod) {
        assert !aMethod.isSynthetic() : "Creating a stub for a stub? " + aMethod;
        ResolvedJavaMethod method = aMethod.getWrapped();
        int newThreadStatus = StatusSupport.getNewThreadStatus(aMethod.getAnnotation(InvokeCFunctionPointer.class).transition());
        return new CFunctionPointerCallStubMethod(method, newThreadStatus);
    }

    private CFunctionPointerCallStubMethod(ResolvedJavaMethod original, int newThreadStatus) {
        super(original, newThreadStatus);
    }

    @Override
    public Signature getSignature() {
        return new Signature() {
            private final Signature wrapped = getOriginal().getSignature();

            @Override
            public int getParameterCount(boolean receiver) {
                return wrapped.getParameterCount(true);
            }

            @Override
            public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
                if (index == 0) {
                    return getOriginal().getDeclaringClass().resolve(accessingClass);
                }
                return wrapped.getParameterType(index - 1, accessingClass);
            }

            @Override
            public JavaType getReturnType(ResolvedJavaType accessingClass) {
                return wrapped.getReturnType(accessingClass);
            }
        };
    }

    @Override
    public int getModifiers() {
        return (super.getModifiers() & ~(Modifier.ABSTRACT | Modifier.INTERFACE)) | Modifier.STATIC;
    }

    /**
     * Overriding this method is necessary in addition to adding the {@link Modifier#STATIC}
     * modifier.
     */
    @Override
    public boolean canBeStaticallyBound() {
        return true;
    }

    @Override
    protected String getCorrespondingAnnotationName() {
        return InvokeCFunctionPointer.class.getSimpleName();
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
    protected JavaType[] getParameterTypesForLoad(ResolvedJavaMethod method) {
        return method.toParameterTypes(); // include receiver = call target address
    }

    @Override
    protected ValueNode createTargetAddressNode(HostedGraphKit kit, HostedProviders providers, List<ValueNode> arguments) {
        return arguments.get(0);
    }

    @Override
    protected Signature adaptSignatureAndConvertArguments(HostedProviders providers, NativeLibraries nativeLibraries,
                    HostedGraphKit kit, ResolvedJavaMethod method, JavaType returnType, JavaType[] paramTypes, List<ValueNode> arguments) {
        // First argument is the call target address, not an actual argument
        arguments.remove(0);
        JavaType[] paramTypesNoReceiver = Arrays.copyOfRange(paramTypes, 1, paramTypes.length);
        return super.adaptSignatureAndConvertArguments(providers, nativeLibraries, kit, method, returnType, paramTypesNoReceiver, arguments);
    }
}
