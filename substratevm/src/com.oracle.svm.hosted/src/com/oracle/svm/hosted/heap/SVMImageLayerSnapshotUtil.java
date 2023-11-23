/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.heap;

import static com.oracle.svm.hosted.methodhandles.InjectedInvokerRenamingSubstitutionProcessor.isInjectedInvokerType;
import static com.oracle.svm.hosted.methodhandles.MethodHandleInvokerRenamingSubstitutionProcessor.isMethodHandleType;
import static com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor.isProxyType;
import static jdk.graal.compiler.java.LambdaUtils.isLambdaType;

import com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;

public class SVMImageLayerSnapshotUtil extends ImageLayerSnapshotUtil {
    @Override
    public String getTypeIdentifier(AnalysisType type, String moduleName) {
        if (isProxyType(type)) {
            return type.toJavaName(true);
        }
        return super.getTypeIdentifier(type, moduleName);
    }

    @Override
    public String getMethodIdentifier(AnalysisMethod method, String moduleName) {
        AnalysisType declaringClass = method.getDeclaringClass();
        if (!(method.wrapped instanceof HotSpotResolvedJavaMethod)) {
            return addModuleName(method.getQualifiedName(), moduleName);
        }
        /*
         * Those methods cannot use the name of the wrapped method as it would not use the name of
         * the SubstitutionType
         */
        if (isLambdaType(declaringClass) || isInjectedInvokerType(declaringClass) || isMethodHandleType(declaringClass) || isProxyType(declaringClass)) {
            return method.getQualifiedName();
        }
        return super.getMethodIdentifier(method, moduleName);
    }
}
