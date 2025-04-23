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
package com.oracle.svm.hosted.phases;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Currently JVMCI resolves some call targets methods as "phantom" {@link ResolvedJavaMethod}s where
 * its {@link ResolvedJavaMethod#getDeclaringClass()} is a class which does not explicitly declare
 * the method. These phantom methods are not accessible via reflection.
 * 
 * This is a problem for the open world type dispatch tables, where we do not want to have redundant
 * slots to account for these phantom methods. To handle this, we adjust these call targets to map
 * to method explicitly declared in a super class or interface.
 */
public class OpenTypeWorldConvertCallTargetPhase extends BasePhase<CoreProviders> {
    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.TYPE).snapshot()) {
            if (callTarget.invokeKind().isIndirect()) {
                maybeConvertCallTarget(callTarget);
            }
        }
    }

    public void maybeConvertCallTarget(MethodCallTargetNode target) {
        if (target.targetMethod() instanceof AnalysisMethod aMethod) {
            AnalysisMethod indirectTarget = aMethod.getIndirectCallTarget();
            if (!indirectTarget.equals(aMethod)) {
                target.setTargetMethod(indirectTarget);
            }
        }
    }
}
