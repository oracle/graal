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

import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.pltgot.PLTGOTConfiguration;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;

public abstract class HostedPLTGOTConfiguration extends PLTGOTConfiguration {
    public static final SectionName SVM_GOT_SECTION = new SectionName.ProgbitsSectionName("svm_got");

    protected MethodAddressResolutionSupport methodAddressResolutionSupport;
    private final GOTEntryAllocator gotEntryAllocator = new GOTEntryAllocator();

    private PLTSectionSupport pltSectionSupport;
    private HostedMetaAccess hostedMetaAccess;

    @SuppressWarnings("this-escape")
    public HostedPLTGOTConfiguration() {
        this.pltSectionSupport = new PLTSectionSupport(getArchSpecificPLTStubGenerator());
    }

    public static HostedPLTGOTConfiguration singleton() {
        return (HostedPLTGOTConfiguration) ImageSingletons.lookup(PLTGOTConfiguration.class);
    }

    public abstract Method getArchSpecificResolverAsMethod();

    public abstract Register getGOTPassingRegister(RegisterConfig registerConfig);

    public abstract PLTStubGenerator getArchSpecificPLTStubGenerator();

    public void setHostedMetaAccess(HostedMetaAccess metaAccess) {
        assert hostedMetaAccess == null : "The field hostedMetaAccess can't be set twice.";
        this.hostedMetaAccess = metaAccess;
    }

    public void initializeMethodAddressResolutionSupport(MethodAddressResolutionSupportFactory methodAddressResolutionSupportFactory) {
        assert methodAddressResolutionSupport == null : "The field methodAddressResolutionSupport can't be initialized twice.";
        methodAddressResolutionSupport = methodAddressResolutionSupportFactory.create();
        if (PLTGOTOptions.PrintPLTGOTCallsInfo.getValue()) {
            methodAddressResolutionSupport = new CollectPLTGOTCallSitesResolutionSupport(methodAddressResolutionSupport);
        }
        methodAddressResolver = getMethodAddressResolutionSupport().createMethodAddressResolver();
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
}
