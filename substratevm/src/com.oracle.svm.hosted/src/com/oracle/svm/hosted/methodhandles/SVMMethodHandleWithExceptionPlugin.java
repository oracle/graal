/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.methodhandles;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerWriter;

import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.replacements.MethodHandleWithExceptionPlugin;
import jdk.graal.compiler.replacements.nodes.MacroInvokable;
import jdk.vm.ci.meta.MethodHandleAccessProvider;

public class SVMMethodHandleWithExceptionPlugin extends MethodHandleWithExceptionPlugin {
    public SVMMethodHandleWithExceptionPlugin(MethodHandleAccessProvider methodHandleAccess, boolean safeForDeoptimization) {
        super(methodHandleAccess, safeForDeoptimization);
    }

    @Override
    protected void onCreateHook(MacroInvokable methodHandleNode, GraphBuilderContext b) {
        if (HostedImageLayerBuildingSupport.buildingSharedLayer()) {
            SVMImageLayerWriter writer = HostedImageLayerBuildingSupport.singleton().getWriter();
            if (methodHandleNode.getTargetMethod() instanceof AnalysisMethod methodHandleMethod && b.getMethod() instanceof AnalysisMethod caller) {
                writer.addPolymorphicSignatureCaller(methodHandleMethod, caller);
            }
        }
    }
}
