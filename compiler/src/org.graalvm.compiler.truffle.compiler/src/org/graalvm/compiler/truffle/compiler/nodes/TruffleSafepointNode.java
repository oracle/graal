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
package org.graalvm.compiler.truffle.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_4;

import java.util.Objects;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizingFixedWithNextNode;
import org.graalvm.compiler.nodes.spi.Lowerable;

@NodeInfo(cycles = NodeCycles.CYCLES_4, cyclesRationale = "read + call", size = SIZE_4)
public final class TruffleSafepointNode extends DeoptimizingFixedWithNextNode implements Lowerable {

    public static final NodeClass<TruffleSafepointNode> TYPE = NodeClass.create(TruffleSafepointNode.class);

    @Input private ConstantNode location;

    public TruffleSafepointNode(ConstantNode location) {
        super(TYPE, StampFactory.forVoid());
        Objects.requireNonNull(location);
        this.location = location;
    }

    public ConstantNode location() {
        return location;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

}
