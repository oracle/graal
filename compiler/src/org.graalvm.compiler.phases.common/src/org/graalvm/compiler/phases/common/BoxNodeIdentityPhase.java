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
package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.StageFlag;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.BoxNode.TrustedBoxedValue;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;

/**
 * Find code patterns that appear to assume identity of boxes. That is where two box values are
 * compared via {@code ==} or {@code !=}. Boxing operations in such patterns are shielded from
 * optimizations such as PEA and canonicalization that do not respect box identity.
 *
 * A common example is a utility that tries to find the values cached by the boxing methods. It does
 * this by probing the result of methods such as {@link Long#valueOf(long)} to find the highest (or
 * lowest) value for which 2 successive calls return objects with different identities. For example:
 *
 * <pre>
 * long maxCachedLong = -1;
 * while (maxCachedLong < Long.MAX_VALUE && Long.valueOf(maxCachedLong + 1) == Long.valueOf(maxCachedLong + 1)) {
 *     maxCachedLong += 1;
 * }
 * </pre>
 *
 * In the context of PEA, such code can run for a very long time since it is legal to reduce
 * {@code Long.valueOf(maxCachedLong + 1) == Long.valueOf(maxCachedLong + 1)} to {@code true}.
 */
public class BoxNodeIdentityPhase extends BasePhase<CoreProviders> {

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        assert !graph.isAfterStage(StageFlag.PARTIAL_ESCAPE) : this + " must be run before PEA";
        for (BoxNode box : graph.getNodes(BoxNode.TYPE)) {
            if (box.isAlive() && !box.hasIdentity() && !(box.getValue() instanceof TrustedBoxedValue)) {
                for (Node usage : box.usages()) {
                    if (usage instanceof ObjectEqualsNode) {
                        ObjectEqualsNode eq = (ObjectEqualsNode) usage;
                        ValueNode other = eq.getX();
                        if (other == box) {
                            other = eq.getY();
                        }
                        if (other instanceof BoxNode) {
                            BoxNode otherBox = (BoxNode) other;
                            if (box.getValue() == otherBox.getValue()) {
                                box.setHasIdentity();
                                otherBox.setHasIdentity();
                            }
                        }
                    }
                }
            }
        }
    }
}
