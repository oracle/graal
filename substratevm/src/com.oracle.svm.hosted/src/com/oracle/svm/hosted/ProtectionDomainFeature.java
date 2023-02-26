/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.reflect.Field;
import java.security.CodeSource;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.ProtectionDomainSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
final class ProtectionDomainFeature implements InternalFeature {

    private Field executableURLSupplierField;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ProtectionDomainSupport.class, new ProtectionDomainSupport());
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        executableURLSupplierField = access.findField(ProtectionDomainSupport.class, "executableURLSupplier");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Boolean useApplicationCodeSourceLocation = ProtectionDomainSupport.Options.UseApplicationCodeSourceLocation.getValue();
        if (useApplicationCodeSourceLocation == null) {
            /* Option not set explicitly, so use automatic behavior based on reachability. */
            access.registerReachabilityHandler(this::enableCodeSource,
                            ReflectionUtil.lookupMethod(CodeSource.class, "getLocation"));
        } else if (useApplicationCodeSourceLocation) {
            /* Always enabled. */
            enableCodeSource(null);
        } else {
            /* Always disabled - nothing to do. */
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void enableCodeSource(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        ProtectionDomainSupport.enableCodeSource();
        if (access != null) {
            access.rescanField(ImageSingletons.lookup(ProtectionDomainSupport.class), executableURLSupplierField);
            if (!access.concurrentReachabilityHandlers()) {
                access.requireAnalysisIteration();
            }
        }
    }
}
