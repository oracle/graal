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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.AllocateWithExceptionNode;
import jdk.graal.compiler.phases.Phase;

/**
 * Replaces {@link AllocateWithExceptionNode}s with their non-throwing counterparts.
 * <p>
 * Allocations in Web Image (for the JS and WasmGC backends) never throw Java exceptions and
 * directly use the run-time's allocation facilities.
 */
public class RemoveAllocateWithExceptionPhase extends Phase {
    @Override
    protected void run(StructuredGraph graph) {
        for (AllocateWithExceptionNode n : graph.getNodes().filter(AllocateWithExceptionNode.class)) {
            n.replaceWithNonThrowing();
        }

        assert checkNodes(graph);
    }

    private static boolean checkNodes(StructuredGraph graph) {
        List<AllocateWithExceptionNode> nodes = graph.getNodes().filter(AllocateWithExceptionNode.class).snapshot();

        if (!nodes.isEmpty()) {
            Set<String> classNames = nodes.stream().map(Object::getClass).map(Class::toString).collect(Collectors.toUnmodifiableSet());
            assert false : "These nodes have not been replaced with a non-throwing counterpart in replaceWithNonThrowing: " + classNames;
        }

        return true;
    }
}
