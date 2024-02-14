/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class VerifyStringCaseUsage extends VerifyPhase<CoreProviders> {
    private static final String TO_LOWER_CASE_METHOD_NAME = "toLowerCase";
    private static final String TO_UPPER_CASE_METHOD_NAME = "toUpperCase";

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        // Ensure methods of interest still exist.
        try {
            String.class.getDeclaredMethod(TO_LOWER_CASE_METHOD_NAME);
            String.class.getDeclaredMethod(TO_UPPER_CASE_METHOD_NAME);
        } catch (NoSuchMethodException e) {
            throw new VerificationError("Failed to find expected method. Has the String class been modified?", e);
        }
        // Find and check uses of methods of interest.
        final ResolvedJavaType stringType = context.getMetaAccess().lookupJavaType(String.class);
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            if (callee.getDeclaringClass().equals(stringType)) {
                if (callee.getParameters().length > 0) {
                    continue;
                }
                String calleeName = callee.getName();
                if (calleeName.equals(TO_LOWER_CASE_METHOD_NAME) || calleeName.equals(TO_UPPER_CASE_METHOD_NAME)) {
                    throw new VerificationError(t, "call to parameterless %s is prohibited to avoid localization issues. Please pass a locale such as 'Locale.ROOT' explicitly.",
                                    callee.format("%H.%n(%p)"));
                }
            }
        }
    }
}
