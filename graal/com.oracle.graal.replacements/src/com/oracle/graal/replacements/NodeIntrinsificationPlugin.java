/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.replacements;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import com.oracle.graal.api.replacements.Fold;
import com.oracle.graal.debug.MethodFilter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.NodePlugin;

/**
 * An {@link NodePlugin} that handles methods annotated by {@link Fold} and {@link NodeIntrinsic}.
 */
public class NodeIntrinsificationPlugin implements NodePlugin {

    /**
     * Calls in replacements to methods matching one of these filters are elided. Only void methods
     * are considered for elision. The use of "snippets" in name of the variable and system property
     * is purely for legacy reasons.
     */
    private static final MethodFilter[] MethodsElidedInSnippets = getMethodsElidedInSnippets();

    private static MethodFilter[] getMethodsElidedInSnippets() {
        String commaSeparatedPatterns = System.getProperty("graal.MethodsElidedInSnippets");
        if (commaSeparatedPatterns != null) {
            return MethodFilter.parse(commaSeparatedPatterns);
        }
        return null;
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (MethodsElidedInSnippets != null) {
            if (MethodFilter.matches(MethodsElidedInSnippets, method)) {
                if (method.getSignature().getReturnKind() != JavaKind.Void) {
                    throw new JVMCIError("Cannot elide non-void method " + method.format("%H.%n(%p)"));
                }
                return true;
            }
        }
        return false;
    }
}
