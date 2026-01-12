/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pltgot;

import java.lang.reflect.Method;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.code.ExplicitCallingConvention;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.pltgot.PLTGOTConfiguration;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.util.AnnotationUtil;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;

public abstract class HostedPLTGOTConfiguration extends PLTGOTConfiguration {
    public static final SectionName SVM_GOT_SECTION = new SectionName.ProgbitsSectionName("svm_got");

    protected MethodAddressResolutionSupport methodAddressResolutionSupport;
    private final GOTEntryAllocator gotEntryAllocator = new GOTEntryAllocator();

    private final PLTSectionSupport pltSectionSupport;
    private HostedMetaAccess hostedMetaAccess;

    @SuppressWarnings("this-escape")
    public HostedPLTGOTConfiguration() {
        this.pltSectionSupport = new PLTSectionSupport(getArchSpecificPLTStubGenerator());
    }

    public static HostedPLTGOTConfiguration singleton() {
        return (HostedPLTGOTConfiguration) ImageSingletons.lookup(PLTGOTConfiguration.class);
    }

    public static boolean canBeCalledViaPLTGOT(SharedMethod method) {
        if (AnnotationUtil.isAnnotationPresent(method, CEntryPoint.class)) {
            return false;
        }
        if (AnnotationUtil.isAnnotationPresent(method, CFunction.class)) {
            return false;
        }
        if (AnnotationUtil.isAnnotationPresent(method, StubCallingConvention.class)) {
            return false;
        }
        if (AnnotationUtil.isAnnotationPresent(method, Uninterruptible.class)) {
            return false;
        }
        if (AnnotationUtil.isAnnotationPresent(method, SubstrateForeignCallTarget.class)) {
            return false;
        }
        if (AnnotationUtil.isAnnotationPresent(method.getDeclaringClass(), InternalVMMethod.class)) {
            return false;
        }
        ExplicitCallingConvention ecc = AnnotationUtil.getAnnotation(method, ExplicitCallingConvention.class);
        if (ecc != null && ecc.value().equals(SubstrateCallingConventionKind.ForwardReturnValue)) {
            /*
             * Methods that use ForwardReturnValue calling convention can't be resolved with PLT/GOT
             * on AMD64 because AMD64MethodAddressResolutionDispatcher.resolveMethodAddress uses the
             * same calling convention, and we can't save the callers value of the `rax` register on
             * AMD64 without spilling it.
             */
            return false;
        }
        return true;
    }

    public abstract Method getArchSpecificResolverAsMethod();

    public abstract Register getGOTPassingRegister(RegisterConfig registerConfig);

    public abstract PLTStubGenerator getArchSpecificPLTStubGenerator();

    public void setHostedMetaAccess(HostedMetaAccess metaAccess) {
        assert hostedMetaAccess == null : "The field hostedMetaAccess can't be set twice.";
        this.hostedMetaAccess = metaAccess;
    }

    public MethodAddressResolutionSupport initializeMethodAddressResolutionSupport(MethodAddressResolutionSupport support) {
        assert methodAddressResolutionSupport == null : "The field methodAddressResolutionSupport can't be initialized twice.";
        methodAddressResolutionSupport = support;
        if (PLTGOTOptions.PrintPLTGOTCallsInfo.getValue()) {
            methodAddressResolutionSupport = new CollectPLTGOTCallSitesResolutionSupport(methodAddressResolutionSupport);
        }
        methodAddressResolver = getMethodAddressResolutionSupport().createMethodAddressResolver();
        return methodAddressResolutionSupport;
    }

    public MethodAddressResolutionSupport getMethodAddressResolutionSupport() {
        assert methodAddressResolutionSupport != null : "Must call initializeMethodAddressResolutionSupport before calling getMethodAddressResolutionSupport";
        return methodAddressResolutionSupport;
    }

    public PLTSectionSupport getPLTSectionSupport() {
        return pltSectionSupport;
    }

    public void markResolverMethodPatch() {
        pltSectionSupport.markResolverMethodPatch(getArchSpecificResolverAsHostedMethod());
    }

    public HostedMethod getArchSpecificResolverAsHostedMethod() {
        assert hostedMetaAccess != null : "Must set hostedMetaAccess before calling getArchSpecificResolverAsHostedMethod";
        return hostedMetaAccess.lookupJavaMethod(getArchSpecificResolverAsMethod());
    }

    public GOTEntryAllocator getGOTEntryAllocator() {
        return gotEntryAllocator;
    }

    @Override
    public boolean shouldCallViaPLTGOT(SharedMethod caller, SharedMethod callee) {
        return methodAddressResolutionSupport.shouldCallViaPLTGOT(caller, callee);
    }

    @Override
    public int getMethodGotEntry(SharedMethod method) {
        return gotEntryAllocator.getMethodGotEntry(method);
    }
}
