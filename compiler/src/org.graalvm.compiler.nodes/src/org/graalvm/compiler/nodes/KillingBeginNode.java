/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Simplifiable;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.word.LocationIdentity;

/**
 * A begin node that kills a single memory location. See {@link MultiKillingBeginNode} for a version
 * with multiple killed locations.
 *
 * @see WithExceptionNode for more details
 */
@NodeInfo(allowedUsageTypes = {Memory}, cycles = CYCLES_0, size = SIZE_0)
public final class KillingBeginNode extends AbstractBeginNode implements SingleMemoryKill, Simplifiable {

    public static final NodeClass<KillingBeginNode> TYPE = NodeClass.create(KillingBeginNode.class);
    protected LocationIdentity locationIdentity;

    public KillingBeginNode(LocationIdentity locationIdentity) {
        super(TYPE);
        this.locationIdentity = locationIdentity;
    }

    public static AbstractBeginNode begin(FixedNode with, LocationIdentity locationIdentity) {
        if (with instanceof KillingBeginNode) {
            return (KillingBeginNode) with;
        }
        AbstractBeginNode begin = with.graph().add(KillingBeginNode.create(locationIdentity));
        begin.setNext(with);
        return begin;
    }

    public static AbstractBeginNode create(LocationIdentity locationIdentity) {
        return new KillingBeginNode(locationIdentity);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (predecessor() instanceof FixedWithNextNode && predecessor() instanceof SingleMemoryKill) {
            SingleMemoryKill predecessor = (SingleMemoryKill) predecessor();
            if (getKilledLocationIdentity().equals(predecessor.getKilledLocationIdentity())) {
                // This killing begin node can be removed.
                tool.addToWorkList(next());
                graph().removeFixed(this);
            }
        }
    }

    @Override
    public void prepareDelete() {
        GraalError.guarantee(predecessor() instanceof SingleMemoryKill, "Cannot delete %s as its predecessor %s is not a SingleMemoryKill", this, predecessor());
        GraalError.guarantee(getKilledLocationIdentity().equals(((SingleMemoryKill) predecessor()).getKilledLocationIdentity()),
                        "Cannot delete %s as its predecessor %s kills a different location (%s vs. %s)", this, predecessor(), getKilledLocationIdentity(),
                        ((SingleMemoryKill) predecessor()).getKilledLocationIdentity());
        if (hasUsages()) {
            // Memory edges are moved to the predecessor.
            replaceAtUsages(predecessor(), InputType.Memory);
        }
        // The guards are moved up to the preceding begin node.
        super.prepareDelete();
    }
}
