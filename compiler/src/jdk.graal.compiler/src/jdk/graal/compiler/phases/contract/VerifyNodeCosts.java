/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.contract;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.phases.VerifyPhase;

/**
 * Utility class that verifies that every {@link Class} extending {@link Node} specifies non default
 * values for {@link NodeCycles} and {@link NodeSize} in its {@link NodeInfo} annotation.
 */
public class VerifyNodeCosts {
    static boolean gr30893IsResolved = false;

    public static void verifyNodeClass(Class<?> clazz) {
        if (Node.class.isAssignableFrom(clazz)) {
            NodeInfo nodeInfo = clazz.getAnnotation(NodeInfo.class);
            if (nodeInfo == null) {
                throw new VerifyPhase.VerificationError("%s extends %s but does not specify a %s annotation.",
                                clazz.getName(), Node.class.getName(), NodeInfo.class.getName());
            }

            List<String> errors = new ArrayList<>();

            if (gr30893IsResolved && nodeInfo.cycles() == NodeCycles.CYCLES_UNKNOWN && nodeInfo.cyclesRationale().isEmpty()) {
                errors.add(String.format("Requires a non-empty value for cyclesRationale since its cycles value is %s.", NodeCycles.CYCLES_UNKNOWN));
            }
            if (gr30893IsResolved && nodeInfo.size() == NodeSize.SIZE_UNKNOWN && nodeInfo.sizeRationale().isEmpty()) {
                errors.add(String.format("Requires a non-empty value for sizeRationale since its size value is %s.", NodeSize.SIZE_UNKNOWN));
            }
            if (!Modifier.isAbstract(clazz.getModifiers())) {
                NodeClass<?> clazzType = NodeClass.get(clazz);
                boolean cyclesSet = walkCHUntil(clazzType, Node.TYPE, cur -> {
                    return cur.cycles() != NodeCycles.CYCLES_UNSET;
                });
                boolean sizeSet = walkCHUntil(clazzType, Node.TYPE, cur -> {
                    return cur.size() != NodeSize.SIZE_UNSET;
                });
                if (!cyclesSet) {
                    errors.add(String.format("Does not specify a %s value in its class hierarchy.", NodeCycles.class.getSimpleName()));
                }
                if (!sizeSet) {
                    errors.add(String.format("Does not specify a %s value in its class hierarchy.", NodeSize.class.getSimpleName()));
                }
            }
            if (!errors.isEmpty()) {
                throw new VerifyPhase.VerificationError("Errors for " + clazz.getName() + System.lineSeparator() + String.join(System.lineSeparator(), errors));
            }
        }
    }

    private static boolean walkCHUntil(NodeClass<?> start, NodeClass<?> until, Predicate<NodeClass<?>> p) {
        NodeClass<?> cur = start;
        while (cur != until && cur != null) {
            if (p.test(cur)) {
                return true;
            }
            cur = cur.getSuperNodeClass();
        }
        return false;
    }
}
