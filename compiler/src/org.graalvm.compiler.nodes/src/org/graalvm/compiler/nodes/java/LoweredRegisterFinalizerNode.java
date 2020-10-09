/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;

/**
 * This node is used to perform the finalizer registration at the end of the java.lang.Object
 * constructor.
 *
 * @see RegisterFinalizerNode
 */
// @formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot estimate the time of a runtime call.",
          size = SIZE_8,
          sizeRationale = "Rough estimation for register handling & calling")
// @formatter:on
public final class LoweredRegisterFinalizerNode extends RegisterFinalizerNode {

    public static final NodeClass<LoweredRegisterFinalizerNode> TYPE = NodeClass.create(LoweredRegisterFinalizerNode.class);

    public LoweredRegisterFinalizerNode(ValueNode value) {
        super(TYPE, value);
    }

    @Override
    public void lower(LoweringTool tool) {
        // already lowered
    }

    @NodeIntrinsic
    public static native void registerFinalizer(Object object);

}
