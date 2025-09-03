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
package com.oracle.svm.hosted.webimage.codegen.phase;

import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.Phase;

/**
 * Removes all {@link ProxyNode}s. This makes removing unnecessary {@link LoopExitNode} in
 * {@link RemoveUnusedExitsPhase} easier. {@link ProxyNode}s are not needed for generating
 * JavaScript code because variables are declared with the keyword {@code var} which declares a
 * variable for the entire function, e.g:
 *
 * <pre>
 *     while(condition) {
 *         ...
 *         var l1 = 1234;
 *         ...
 *     }
 *     var l2 = l1;
 * </pre>
 *
 * is valid JavaScript code.
 */
public class RemoveProxyNodePhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (ProxyNode proxy : graph.getNodes().filter(ProxyNode.class).snapshot()) {
            removeProxy(proxy);
        }
    }

    private static void removeProxy(ProxyNode proxy) {
        proxy.replaceAtUsages(proxy.value());
        proxy.safeDelete();
    }
}
