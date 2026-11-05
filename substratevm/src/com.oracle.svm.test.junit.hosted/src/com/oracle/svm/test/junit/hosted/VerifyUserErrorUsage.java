/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.test.VerifyStringFormatterUsage;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies that calls to {@link UserError#guarantee}, {@link UserError#abort} and
 * {@link AnalysisError#guarantee} conform with the constraints specified by
 * {@link VerifyStringFormatterUsage}.
 */
public class VerifyUserErrorUsage extends VerifyStringFormatterUsage {

    MetaAccessProvider metaAccess;

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        metaAccess = context.getMetaAccess();
        ResolvedJavaType stringType = metaAccess.lookupJavaType(String.class);
        ResolvedJavaType userErrorType = metaAccess.lookupJavaType(UserError.class);
        ResolvedJavaType vmErrorType = metaAccess.lookupJavaType(VMError.class);
        ResolvedJavaType analysisErrorType = metaAccess.lookupJavaType(AnalysisError.class);

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            String calleeName = callee.getName();
            if (callee.getDeclaringClass().equals(userErrorType) && calleeName.equals("guarantee")) {
                verifyParameters(metaAccess, t, t.arguments(), stringType, 0);
            } else if (callee.getDeclaringClass().equals(vmErrorType) && calleeName.equals("guarantee")) {
                verifyParameters(metaAccess, t, t.arguments(), stringType, 0);
            } else if (callee.getDeclaringClass().equals(analysisErrorType) && calleeName.equals("guarantee")) {
                verifyParameters(metaAccess, t, t.arguments(), stringType, 0);
            }
        }
    }
}
