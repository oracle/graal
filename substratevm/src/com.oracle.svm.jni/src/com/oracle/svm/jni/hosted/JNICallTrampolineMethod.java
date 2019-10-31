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

// Checkstyle: allow reflection

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.nodes.DeadEndNode;
import com.oracle.svm.core.graal.nodes.UnreachableNode;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.code.CompileQueue.CompileFunction;
import com.oracle.svm.hosted.code.CompileQueue.ParseFunction;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.hosted.thread.VMThreadMTFeature;
import com.oracle.svm.jni.access.JNIAccessibleMethod;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A trampoline for implementing JNI functions for calling Java methods from native code:
 * <p>
 * <code>
 * NativeType CallStatic<type>Method(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
 * NativeType Call<type>Method(JNIEnv *env, jobject obj, jmethodID methodID, ...);
 * </code>
 * <p>
 * The {@code jmethodID} values that we pass out are the addresses of {@link JNIAccessibleMethod}
 * objects, which are made immutable so that they are never moved by the garbage collector. The
 * trampoline simply jumps to the address of a specific call wrapper that is stored in a
 * {@link #callWrapperField field} of the object. The wrappers then take care of spilling
 * callee-saved registers, transitioning from native to Java and back, obtaining the arguments in a
 * particular form (varargs, array, va_list) and boxing/unboxing object handles as necessary.
 */
public class JNICallTrampolineMethod extends CustomSubstitutionMethod {
    private final ResolvedJavaField callWrapperField;
    private final boolean nonVirtual;

    public JNICallTrampolineMethod(ResolvedJavaMethod original, ResolvedJavaField callWrapperField, boolean nonVirtual) {
        super(original);
        this.callWrapperField = callWrapperField;
        this.nonVirtual = nonVirtual;
    }

    @Override
    public int getModifiers() {
        return super.getModifiers() & ~Modifier.NATIVE;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        HostedGraphKit kit = new JNIGraphKit(debug, providers, method);
        kit.append(new UnreachableNode());
        kit.append(new DeadEndNode());

        return kit.finalizeGraph();
    }

    public ParseFunction createCustomParseFunction() {
        return (debug, method, reason, config) -> {
            // no parsing necessary
        };
    }

    public CompileFunction createCustomCompileFunction() {
        return (debug, method, identifier, reason, config) -> {
            SubstrateBackend backend = config.getBackendForNormalMethod();

            // Determine register for jmethodID argument
            HostedProviders providers = (HostedProviders) config.getProviders();
            List<JavaType> parameters = new ArrayList<>();
            parameters.add(providers.getMetaAccess().lookupJavaType(JNIEnvironment.class));
            parameters.add(providers.getMetaAccess().lookupJavaType(JNIObjectHandle.class));
            if (nonVirtual) {
                parameters.add(providers.getMetaAccess().lookupJavaType(JNIObjectHandle.class));
            }
            parameters.add(providers.getMetaAccess().lookupJavaType(JNIMethodId.class));
            ResolvedJavaType returnType = providers.getWordTypes().getWordImplType();
            CallingConvention callingConvention = backend.getCodeCache().getRegisterConfig().getCallingConvention(
                            SubstrateCallingConventionType.NativeCall, returnType, parameters.toArray(new JavaType[0]), backend);
            RegisterValue threadArg = null;
            int threadIsolateOffset = -1;
            if (SubstrateOptions.SpawnIsolates.getValue()) {
                threadArg = (RegisterValue) callingConvention.getArgument(0); // JNIEnv
                if (SubstrateOptions.MultiThreaded.getValue()) {
                    threadIsolateOffset = ImageSingletons.lookup(VMThreadMTFeature.class).offsetOf(VMThreads.IsolateTL);
                }
                // NOTE: GR-17030: JNI is currently broken in the single-threaded, multi-isolate
                // case. Fixing this also requires changes to how trampolines are generated.
            }
            RegisterValue methodIdArg = (RegisterValue) callingConvention.getArgument(parameters.size() - 1);

            return backend.createJNITrampolineMethod(method, identifier, threadArg, threadIsolateOffset, methodIdArg, getFieldOffset(providers));
        };
    }

    private int getFieldOffset(HostedProviders providers) {
        HostedMetaAccess metaAccess = (HostedMetaAccess) providers.getMetaAccess();
        HostedUniverse universe = (HostedUniverse) metaAccess.getUniverse();
        AnalysisUniverse analysisUniverse = universe.getBigBang().getUniverse();
        HostedField hostedField = universe.lookup(analysisUniverse.lookup(callWrapperField));
        assert hostedField.hasLocation();
        return hostedField.getLocation();
    }
}
