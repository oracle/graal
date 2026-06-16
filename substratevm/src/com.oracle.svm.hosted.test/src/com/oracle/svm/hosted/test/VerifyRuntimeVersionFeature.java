/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.Runtime.Version;

import com.oracle.svm.core.JavaVersionUtil;

import jdk.graal.compiler.core.test.VerifyPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Verify that SVM fetches {@link Version#feature} only via {@link JavaVersionUtil}.
 */
public class VerifyRuntimeVersionFeature extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        ResolvedJavaMethod caller = graph.method();

        if (caller.getName().equals("<clinit>") && caller.getDeclaringClass().equals(metaAccess.lookupJavaType(JavaVersionUtil.class))) {
            // JavaVersionUtil.JAVA_SPEC
            return;
        }

        if (caller.getDeclaringClass().toClassName().equals("com.oracle.svm.guest.hosted.BuildTimeSupport")) {
            // BuildTimeSupport cannot reference JavaVersionUtil
            return;
        }

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            if ("feature".equals(callee.getName()) && metaAccess.lookupJavaType(Version.class).equals(callee.getDeclaringClass())) {
                throw new VerificationError(t, "Call to %s is prohibited, use %s.JAVA_SPEC",
                                callee.format("%H.%n(%p)"),
                                JavaVersionUtil.class.getName());
            }
        }
    }
}
