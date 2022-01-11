/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.VerifyPhase;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * {@link Architecture} related methods are potentially unsafe to use within an InvocationPlugin if
 * the plugin might be used by a snippet as this can embed architecture dependent nodes in the
 * graphs that are prepared for libgraal.
 */
public class VerifyArchUsageInPlugins extends VerifyPhase<CoreProviders> {
    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        ResolvedJavaType invocationPluginClass = metaAccess.lookupJavaType(InvocationPlugin.class);
        ResolvedJavaMethod method = graph.method();
        if (!method.getName().equals("apply") || !invocationPluginClass.isAssignableFrom(method.getDeclaringClass()) || method.getDeclaringClass().equals(invocationPluginClass)) {
            // Only check the apply methods of subclasses
            return;
        }

        ResolvedJavaType architecture = metaAccess.lookupJavaType(Architecture.class);
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            if (architecture.isAssignableFrom(callee.getDeclaringClass())) {
                throw new VerificationError("Architecture methods are unsafe to use within InvocationPlugin.apply methods:" + callee);
            }
        }
    }

}
