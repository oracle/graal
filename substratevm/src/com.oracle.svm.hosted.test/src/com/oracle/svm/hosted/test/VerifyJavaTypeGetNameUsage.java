/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.test;

import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.shared.util.ClassUtil;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.graal.compiler.core.test.VerifyPhase;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies that we don't introduce unintentional usages of {@link JavaType#getName()}. When
 * migrating from {@link Class} to {@link JavaType}, calls from {@link Class#getName()} should be
 * migrated to {@link JavaType#toClassName()} because the {@link JavaType#getName()} outputs a
 * different format than {@link Class#getName()}.
 * <p>
 * For new legitimate use cases of {@link JavaType#getName()}, update {@link #DEFAULT_EXCLUDE}.
 */
public class VerifyJavaTypeGetNameUsage extends VerifyPhase<CoreProviders> {

    /**
     * Verifier-local extension point for suites that need to replace or extend this verifier.
     */
    public interface Provider {
        VerifyJavaTypeGetNameUsage createVerifier();
    }

    private ResolvedJavaType classType;
    private ResolvedJavaMethod classGetName;
    private ResolvedJavaType javaType;
    private ResolvedJavaMethod javaTypeToClassName;
    private ResolvedJavaMethod javaTypeGetName;

    private final Set<String> exclude;

    private static final Set<String> DEFAULT_EXCLUDE = Set.of(
                    "com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow",
                    "com.oracle.graal.pointsto.heap.ImageHeapConstant",
                    "com.oracle.graal.pointsto.inspect.flow.AllInstantiatedFlowWrap",
                    "com.oracle.graal.pointsto.meta.AnalysisElement$ReachabilityTraceBuilder",
                    "com.oracle.graal.pointsto.meta.AnalysisType",
                    "com.oracle.graal.pointsto.PointsToAnalysis",
                    "com.oracle.graal.pointsto.PointsToAnalysis$ConstantObjectsProfiler",
                    "com.oracle.graal.pointsto.reports.StatisticsPrinter",
                    "com.oracle.graal.pointsto.typestate.SingleTypeState",
                    "com.oracle.svm.core.debug.SharedDebugInfoProvider",
                    "com.oracle.svm.core.hub.crema.CremaSupport",
                    "com.oracle.svm.core.jni.access.JNIAccessibleMethodDescriptor",
                    "com.oracle.svm.graal.isolated.DisableSnippetCountersPlugin",
                    "com.oracle.svm.graal.meta.RuntimeCodeInstaller",
                    "com.oracle.svm.graal.meta.SubstrateConstantFieldProvider",
                    "com.oracle.svm.hosted.ameta.AnalysisConstantFieldProvider",
                    "com.oracle.svm.hosted.annotation.CustomSubstitutionType",
                    "com.oracle.svm.hosted.c.info.InfoTreeVisitor",
                    "com.oracle.svm.hosted.code.DeoptimizationUtils",
                    "com.oracle.svm.hosted.dashboard.HeapBreakdown",
                    "com.oracle.svm.hosted.heap.SVMImageHeapVerifier",
                    "com.oracle.svm.hosted.image.HeapHistogram",
                    "com.oracle.svm.hosted.imagelayer.SVMImageLayerWriter",
                    "com.oracle.svm.hosted.image.NativeImageCodeCache",
                    "com.oracle.svm.hosted.image.NativeImageDebugInfoProvider",
                    "com.oracle.svm.hosted.image.NativeImageHeap",
                    "com.oracle.svm.hosted.jni.JNILibraryLoadFeature",
                    "com.oracle.svm.hosted.jni.JNINativeCallWrapperMethod",
                    "com.oracle.svm.hosted.meta.HostedInstanceClass",
                    "com.oracle.svm.hosted.meta.HostedType",
                    "com.oracle.svm.hosted.meta.HostedUniverse$TypeComparator",
                    "com.oracle.svm.hosted.meta.UniverseBuilder",
                    "com.oracle.svm.hosted.methodhandles.InjectedInvokerRenamingSubstitutionProcessor",
                    "com.oracle.svm.hosted.methodhandles.MethodHandleInvokerRenamingSubstitutionProcessor",
                    "com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin",
                    "com.oracle.svm.hosted.scala.ScalaAnalysisPlugin",
                    "com.oracle.svm.hosted.substitute.AutomaticUnsafeTransformationSupport",
                    "com.oracle.svm.hosted.substitute.InjectedFieldsType",
                    "com.oracle.svm.hosted.substitute.SubstitutionType",
                    "com.oracle.svm.interpreter.BuildTimeInterpreterUniverse",
                    "com.oracle.svm.interpreter.classfile.ClassFile",
                    "com.oracle.svm.interpreter.classfile.ClassFile$ConstantWrapper",
                    "com.oracle.svm.interpreter.CremaSupportImpl",
                    "com.oracle.svm.interpreter.DebuggerFeature",
                    "com.oracle.svm.interpreter.Interpreter",
                    "com.oracle.svm.interpreter.InterpreterToVM",
                    "com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField",
                    "com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod",
                    "com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType",
                    "com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType",
                    "com.oracle.svm.interpreter.metadata.MetadataUtil",
                    "com.oracle.svm.interpreter.metadata.serialization.Serializers",
                    "com.oracle.svm.interpreter.RuntimeInterpreterConstantPool",
                    "com.oracle.svm.jdwp.resident.ClassUtils",
                    "com.oracle.svm.jdwp.server.impl.Breakpoints",
                    "com.oracle.svm.jdwp.server.impl.ServerJDWP",
                    "com.oracle.svm.polyglot.scala.ScalaAnalysisPlugin",
                    "com.oracle.svm.truffle.tck.AbstractMethodListParser$SignaturePredicate",
                    "com.oracle.svm.util.JVMCIReflectionUtil",
                    "com.oracle.svm.interpreter.metadata.CremaResolvedJavaMethodImpl",
                    "com.oracle.svm.interpreter.metadata.CremaResolvedJavaMethodImpl$CremaResolvedNativeJavaMethod");

    protected VerifyJavaTypeGetNameUsage(Set<String> additionalExcludes) {
        this.exclude = Stream.concat(DEFAULT_EXCLUDE.stream(), additionalExcludes.stream()).collect(Collectors.toUnmodifiableSet());
    }

    public VerifyJavaTypeGetNameUsage() {
        this(Set.of());
    }

    public static VerifyJavaTypeGetNameUsage create() {
        VerifyJavaTypeGetNameUsage verifier = null;
        for (Provider provider : ServiceLoader.load(Provider.class)) {
            GraalError.guarantee(verifier == null, "Only one %s provider is supported", Provider.class.getName());
            verifier = provider.createVerifier();
        }
        return verifier == null ? new VerifyJavaTypeGetNameUsage() : verifier;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        init(metaAccess);
        ResolvedJavaMethod caller = graph.method();
        ResolvedJavaType declaringClass = caller.getDeclaringClass();

        if (exclude.contains(declaringClass.toJavaName())) {
            return;
        }

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            ResolvedJavaType calleeDeclaringClass = callee.getDeclaringClass();

            if (javaType.isAssignableFrom(calleeDeclaringClass)) {
                if ("getName".equals(callee.getName())) {
                    throw new VerificationError(t.invoke(),
                                    "Call to %s might not be intentional. If you want the behavior of `%s`, use `%s` instead. If you are sure you want `%s`, update exclude list in %s.java)",
                                    callee.format("%H.%n(%p)"),
                                    classGetName.format("%H.%n(%p)"),
                                    javaTypeToClassName.format("%H.%n(%p)"),
                                    javaTypeGetName.format("%H.%n(%p)"),
                                    ClassUtil.getUnqualifiedName(getClass()));
                }
            }
        }
    }

    private synchronized void init(MetaAccessProvider metaAccess) {
        if (classType == null) {
            classType = Objects.requireNonNull(metaAccess.lookupJavaType(Class.class));
            classGetName = JVMCIReflectionUtil.getUniqueDeclaredMethod(false, classType, "getName");
            javaType = Objects.requireNonNull(metaAccess.lookupJavaType(JavaType.class));
            javaTypeToClassName = JVMCIReflectionUtil.getUniqueDeclaredMethod(false, javaType, "toClassName");
            javaTypeGetName = JVMCIReflectionUtil.getUniqueDeclaredMethod(false, javaType, "getName");
        }
    }
}
