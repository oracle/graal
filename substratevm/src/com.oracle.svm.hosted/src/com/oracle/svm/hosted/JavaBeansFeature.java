/*
 * Copyright (c) 2022, BELLSOFT. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.JavaBeansSupport;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;

import java.beans.Introspector;
import java.lang.reflect.Field;

@AutomaticallyRegisteredFeature
final class JavaBeansFeature implements InternalFeature {

    private Field componentClassField;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(JavaBeansSupport.class, new JavaBeansSupport());
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        componentClassField = access.findField(JavaBeansSupport.class, "COMPONENT_CLASS");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(this::enableComponentClass, access.findClassByName("java.awt.Component"));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void enableComponentClass(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        JavaBeansSupport.enableComponentClass();
        access.rescanField(ImageSingletons.lookup(JavaBeansSupport.class), componentClassField);
        if (!access.concurrentReachabilityHandlers()) {
            access.requireAnalysisIteration();
        }
    }
}
