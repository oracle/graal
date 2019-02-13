/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.nodes;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

/**
 * Run-time check that the actual {@link #getInput() input} to this node conforms to the expected
 * {@link ValueNode#stamp(NodeView)}.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public final class AssertStampNode extends AssertValueNode implements Virtualizable {
    public static final NodeClass<AssertStampNode> TYPE = NodeClass.create(AssertStampNode.class);

    public static void create(ValueNode input) {
        insert(input, input.graph().add(new AssertStampNode(input.stamp(NodeView.DEFAULT))));
    }

    protected AssertStampNode(Stamp stamp) {
        super(TYPE, stamp);
    }

    @Override
    protected boolean alwaysHolds(boolean reportError) {
        if (getInput().isConstant()) {
            if (getInput().asJavaConstant().isNull()) {
                if (StampTool.isPointerNonNull(this)) {
                    if (reportError) {
                        throw shouldNotReachHere("Null constant not compatible with stamp: " + this + " : " + stamp(NodeView.DEFAULT));
                    }
                } else {
                    return true;
                }
            } else {
                if (!StampTool.typeOrNull(this).isAssignableFrom(StampTool.typeOrNull(getInput()))) {
                    if (reportError) {
                        throw shouldNotReachHere("Constant object not compatible with stamp: " + this + " : " + stamp(NodeView.DEFAULT) + ", " + StampTool.typeOrNull(getInput()));
                    }
                } else {
                    return true;
                }
            }
        }
        if ((getInput() instanceof AssertStampNode || getInput() instanceof AssertTypeStateNode) &&
                        stamp(NodeView.DEFAULT).join(getInput().stamp(NodeView.DEFAULT)).equals(getInput().stamp(NodeView.DEFAULT))) {
            /* Another node is already checking the same or a stronger stamp. */
            return true;
        }
        return false;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getInput());
        if (alias instanceof VirtualObjectNode) {
            if (!StampTool.typeOrNull(this).isAssignableFrom(StampTool.typeOrNull(alias))) {
                throw shouldNotReachHere("Virtual object not compatible with stamp: " + stamp(NodeView.DEFAULT) + ", " + alias.stamp(NodeView.DEFAULT));
            }
            tool.replaceWith(alias);
        }
    }
}
