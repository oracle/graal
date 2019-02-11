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

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.infrastructure.WrappedSignature;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CFunctionPointerStub;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

// Checkstyle: allow reflection

/**
 * A stub for calling native code generated from a method annotated with
 * {@link InvokeCFunctionPointer}.
 */
public final class CFunctionPointerCallStubMethod extends CCallStubMethod {

    static CFunctionPointerCallStubMethod create(AnalysisMethod aMethod, AnalysisMetaAccess aMetaAccess, WordTypes aWordTypes) {
        ResolvedJavaMethod method = aMethod.getWrapped();
        Signature signature = makeSignature(aMethod, aMetaAccess, aWordTypes);
        MetaAccessProvider metaAccess = aMetaAccess.getWrapped();
        ResolvedJavaType declaringClass = metaAccess.lookupJavaType(CFunctionPointerStub.class);
        ConstantPool constantPool = CFunctionPointerStub.getConstantPool(metaAccess);
        boolean needsTransition = (aMethod.getAnnotation(InvokeCFunctionPointer.class).transition() != CFunction.Transition.NO_TRANSITION);
        return new CFunctionPointerCallStubMethod(method, SubstrateUtil.uniqueShortName(method), signature, declaringClass, constantPool, needsTransition);
    }

    private static Signature makeSignature(AnalysisMethod method, AnalysisMetaAccess aMetaAccess, WordTypes aWordTypes) {
        ResolvedJavaType stubsType = aMetaAccess.lookupJavaType(CFunctionPointerStub.class).getWrapped();
        /*
         * Non-primitive types used in the signature of the original method might not be resolvable
         * in the context of this method and its enclosing class of CFunctionPointerStub. We replace
         * word types with WordBase to avoid this problem.
         */
        ResolvedJavaType wordBase = aMetaAccess.lookupJavaType(WordBase.class).getWrapped();
        WrappedSignature aSignature = method.getSignature();

        AnalysisType aReturnType = (AnalysisType) aSignature.getReturnType(null);
        ResolvedJavaType returnType = aWordTypes.isWord(aReturnType) ? wordBase : aReturnType.getWrapped().resolve(stubsType);

        int paramCount = aSignature.getParameterCount(true);
        JavaType[] paramTypes = new JavaType[paramCount];
        AnalysisType declaringType = method.getDeclaringClass();
        paramTypes[0] = aWordTypes.isWord(declaringType) ? wordBase : declaringType.getWrapped().resolve(stubsType);
        for (int i = 1; i < paramCount; i++) {
            AnalysisType aParamType = (AnalysisType) aSignature.getParameterType(i - 1, null);
            paramTypes[i] = aWordTypes.isWord(aParamType) ? wordBase : aParamType.getWrapped().resolve(stubsType);
        }
        return new SimpleSignature(paramTypes, returnType);
    }

    private final String name;
    private final Signature signature;
    private final ResolvedJavaType declaringClass;
    private final ConstantPool constantPool;

    private CFunctionPointerCallStubMethod(ResolvedJavaMethod original, String name, Signature signature, ResolvedJavaType declaringClass, ConstantPool constantPool, boolean needsTransition) {
        super(original, needsTransition);
        this.name = name;
        this.signature = signature;
        this.declaringClass = declaringClass;
        this.constantPool = constantPool;
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
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
    public int getModifiers() {
        return (super.getModifiers() & ~(Modifier.ABSTRACT | Modifier.INTERFACE)) | Modifier.STATIC;
    }

    @Override
    protected String getCorrespondingAnnotationName() {
        return InvokeCFunctionPointer.class.getSimpleName();
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        if (purpose == Purpose.PREPARE_RUNTIME_COMPILATION && needsTransition) {
            /*
             * C function calls that need a transition cannot be runtime compiled (and cannot be
             * inlined during runtime compilation). Deoptimization could be required while we are
             * blocked in native code, which means the deoptimization stub would need to do the
             * native-to-Java transition.
             */
            ImageSingletons.lookup(CFunctionFeature.class).warnRuntimeCompilationReachableCFunctionWithTransition(this);
            return null;
        }

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
                    HostedGraphKit kit, JavaType returnType, JavaType[] paramTypes, List<ValueNode> arguments) {
        // First argument is the call target address, not an actual argument
        arguments.remove(0);
        JavaType[] paramTypesNoReceiver = Arrays.copyOfRange(paramTypes, 1, paramTypes.length);
        return super.adaptSignatureAndConvertArguments(providers, nativeLibraries, kit, returnType, paramTypesNoReceiver, arguments);
    }

    @Override
    public Annotation[] getAnnotations() {
        return getDeclaredAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return needsTransition ? new Annotation[0] : new Annotation[]{UNINTERRUPTIBLE};
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (!needsTransition && annotationClass.equals(UNINTERRUPTIBLE.annotationType())) {
            return (T) UNINTERRUPTIBLE;
        }
        return null;
    }

    private static final Uninterruptible UNINTERRUPTIBLE = new Uninterruptible() {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Uninterruptible.class;
        }

        @Override
        public String reason() {
            return "@" + InvokeCFunctionPointer.class.getSimpleName() + " method is marked " + CFunction.Transition.NO_TRANSITION;
        }

        @Override
        public boolean callerMustBe() {
            return false;
        }

        @Override
        public boolean calleeMustBe() {
            return true;
        }

        @Override
        public boolean mayBeInlined() {
            return false;
        }
    };
}
