/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jvmci;

import static jdk.vm.ci.common.InitTimer.timer;

import java.io.Serializable;
import java.util.EnumSet;

import com.oracle.truffle.espresso.jvmci.meta.EspressoConstantReflectionProvider;
import com.oracle.truffle.espresso.jvmci.meta.EspressoMetaAccessProvider;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedInstanceType;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedJavaMethod;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.runtime.JVMCIRuntime;

public final class EspressoJVMCIRuntime implements JVMCIRuntime {
    private static volatile EspressoJVMCIRuntime instance;

    private volatile JVMCICompiler compiler;
    private final JVMCIBackend hostBackend;
    private final EspressoResolvedInstanceType javaLangObject;
    private final EspressoResolvedInstanceType[] arrayInterfaces;

    private EspressoJVMCIRuntime() {
        EspressoMetaAccessProvider metaAccess = new EspressoMetaAccessProvider();
        CodeCacheProvider codeCache = new DummyCodeCacheProvider(getHostTarget());
        ConstantReflectionProvider constantReflection = new EspressoConstantReflectionProvider(metaAccess);
        StackIntrospection stackIntrospection = new DummyStackIntrospection();
        hostBackend = new JVMCIBackend(metaAccess, codeCache, constantReflection, stackIntrospection);
        javaLangObject = metaAccess.lookupJavaType(Object.class);
        arrayInterfaces = new EspressoResolvedInstanceType[]{
                        metaAccess.lookupJavaType(Cloneable.class),
                        metaAccess.lookupJavaType(Serializable.class)
        };
    }

    private static TargetDescription getHostTarget() {
        String archString = System.getProperty("os.arch");
        Architecture arch;
        switch (archString) {
            case "amd64":
            case "x86_64":
                EnumSet<AMD64.CPUFeature> x8664v2 = EnumSet.of(AMD64.CPUFeature.CMOV, AMD64.CPUFeature.CX8, AMD64.CPUFeature.FXSR, AMD64.CPUFeature.MMX, AMD64.CPUFeature.SSE, AMD64.CPUFeature.SSE2,
                                AMD64.CPUFeature.POPCNT, AMD64.CPUFeature.SSE3, AMD64.CPUFeature.SSE4_1, AMD64.CPUFeature.SSE4_2, AMD64.CPUFeature.SSSE3);
                arch = new AMD64(x8664v2);
                break;
            case "aarch64":
            case "arm64":
                arch = new AArch64(EnumSet.of(AArch64.CPUFeature.FP));
                break;
            default:
                throw JVMCIError.unimplemented(archString);
        }
        return new TargetDescription(arch, true, 16, 4096, true);
    }

    private native JVMCICompiler createEspressoGraalJVMCICompiler();

    // used by the VM
    @SuppressWarnings("try")
    public static EspressoJVMCIRuntime runtime() {
        EspressoJVMCIRuntime result = instance;
        if (result == null) {
            // Synchronize on JVMCI.class to avoid deadlock
            // between the two JVMCI initialization paths:
            // EspressoJVMCIRuntime.runtime() and JVMCI.getRuntime().
            synchronized (JVMCI.class) {
                result = instance;
                if (result == null) {
                    try (InitTimer t = timer("EspressoJVMCIRuntime.<init>")) {
                        instance = result = new EspressoJVMCIRuntime();
                    }
                }
            }
        }
        return result;
    }

    @Override
    public JVMCICompiler getCompiler() {
        if (compiler == null) {
            synchronized (this) {
                if (compiler == null) {
                    compiler = createEspressoGraalJVMCICompiler();
                }
            }
        }
        return compiler;
    }

    @Override
    public JVMCIBackend getHostJVMCIBackend() {
        return hostBackend;
    }

    @Override
    public <T extends Architecture> JVMCIBackend getJVMCIBackend(Class<T> arch) {
        throw JVMCIError.unimplemented(arch.getName());
    }

    public EspressoResolvedInstanceType getJavaLangObject() {
        return javaLangObject;
    }

    public EspressoResolvedInstanceType[] getArrayInterfaces() {
        return arrayInterfaces;
    }

    public native JavaType lookupType(String name, EspressoResolvedInstanceType accessingClass, boolean resolve);

    public native EspressoResolvedJavaMethod resolveMethod(EspressoResolvedInstanceType receiver, EspressoResolvedJavaMethod method, EspressoResolvedInstanceType callerType);
}
