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

import jdk.graal.compiler.core.test.CheckGraalInvariants.InvariantsTool;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * {@link System#currentTimeMillis} shouldn't be used to measure elapsed time so require callers
 * that want the current time in milliseconds to go through {@link GraalServices#milliTimeStamp()}.
 * {@link System#nanoTime()} should be used for measuring elapsed time.
 */
public class VerifyCurrentTimeMillisUsage extends VerifyPhase<CoreProviders> {
    private static final String CURRENT_TIME_MILLIS_NAME = "currentTimeMillis";

    private final InvariantsTool tool;

    public VerifyCurrentTimeMillisUsage(InvariantsTool tool) {
        this.tool = tool;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        final ResolvedJavaType systemType = context.getMetaAccess().lookupJavaType(System.class);
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            if (callee.getDeclaringClass().equals(systemType)) {
                String calleeName = callee.getName();
                if (calleeName.equals(CURRENT_TIME_MILLIS_NAME)) {
                    final ResolvedJavaType declaringClass = graph.method().getDeclaringClass();
                    tool.verifyCurrentTimeMillis(context.getMetaAccess(), t, declaringClass);
                }
            }
        }
    }
}
