/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

/**
 * Node used to convert unknown value objects, e.g., objects that resulted from a low level memory
 * read, i.e., word-to-object reads, to a proper object. This node needs to be inserted in the right
 * place by the analysis client.
 *
 * By default during analysis all objects that result from low level reads are treated as having
 * unknown value. We only convert them to proper objects when they are used as a receiver object in
 * calls, loads, stores, etc. Thus, objects that are never used as proper Java objects, but only
 * passed around as data will not interfere with the points-to analysis. If an unknown value object
 * is used as a proper object, for example as a receiver for a call, an unsupported feature will be
 * reported.
 *
 * The stamp of the node is used to reduce the type of the return value from the all instantiated
 * types to the type subtree of the specified type. If the type is Object no actual type reduction
 * is done.
 */
@NodeInfo(size = SIZE_IGNORED, cycles = CYCLES_IGNORED)
public final class ConvertUnknownValueNode extends FixedWithNextNode implements Lowerable {
    public static final NodeClass<ConvertUnknownValueNode> TYPE = NodeClass.create(ConvertUnknownValueNode.class);

    @Input ValueNode object;

    public ConvertUnknownValueNode(ValueNode object, Stamp stamp) {
        super(TYPE, stamp);
        this.object = object;
    }

    public ValueNode getObject() {
        return object;
    }

    @Override
    public void lower(LoweringTool tool) {
        graph().replaceFixed(this, object);
    }
}
