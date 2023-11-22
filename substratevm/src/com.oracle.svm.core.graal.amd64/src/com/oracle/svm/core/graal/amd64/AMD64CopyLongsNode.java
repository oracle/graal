/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, BELLSOFT. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractStateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import com.oracle.svm.core.graal.amd64.SubstrateAMD64Backend.SubstrateAMD64LIRGenerator;

@NodeInfo
public class AMD64CopyLongsNode extends AbstractStateSplit implements LIRLowerable {

    public static final NodeClass<AMD64CopyLongsNode> TYPE = NodeClass.create(AMD64CopyLongsNode.class);

    @Input private ValueNode src;
    @Input private ValueNode dst;
    @Input private ValueNode len;
    private final boolean forward;

    protected AMD64CopyLongsNode(ValueNode src, ValueNode dst, ValueNode len, boolean forward) {
        super(TYPE, StampFactory.forVoid());
        this.src = src;
        this.dst = dst;
        this.len = len;
        this.forward = forward;
    }

    public static AMD64CopyLongsNode forward(ValueNode src, ValueNode dst, ValueNode len) {
        return new AMD64CopyLongsNode(src, dst, len, true);
    }

    public static AMD64CopyLongsNode backward(ValueNode src, ValueNode dst, ValueNode len) {
        return new AMD64CopyLongsNode(src, dst, len, false);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        SubstrateAMD64LIRGenerator tool = (SubstrateAMD64LIRGenerator) gen.getLIRGeneratorTool();
        tool.emitCopyLongs(null, gen.operand(src), gen.operand(dst), gen.operand(len), forward);
    }
}
