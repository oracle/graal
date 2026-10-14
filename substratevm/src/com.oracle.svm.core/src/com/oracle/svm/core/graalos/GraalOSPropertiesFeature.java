/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graalos;

import org.graalvm.nativeimage.hosted.RuntimeSystemProperties;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;

@AutomaticallyRegisteredFeature
public final class GraalOSPropertiesFeature implements InternalFeature {
    private static final String GRAALOS_PROPERTY = "org.graalvm.nativeimage.graalos";

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        /* Truffle includes language resources duringSetup, so publish the target before then. */
        System.setProperty(GRAALOS_PROPERTY, Boolean.toString(SubstrateOptions.GraalOS.getValue()));
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            RuntimeSystemProperties.register(GRAALOS_PROPERTY, Boolean.toString(SubstrateOptions.GraalOS.getValue()));
        }
    }
}
