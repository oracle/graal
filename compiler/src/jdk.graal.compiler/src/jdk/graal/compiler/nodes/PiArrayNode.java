/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.spi.ArrayLengthProvider;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;

/**
 * A {@link PiNode} that also provides an array length in addition to a more refined stamp. A usage
 * that reads the array length, such as an {@link ArrayLengthNode}, can be canonicalized based on
 * this information.
 */
@NodeInfo
public final class PiArrayNode extends PiNode implements ArrayLengthProvider {

    public static final NodeClass<PiArrayNode> TYPE = NodeClass.create(PiArrayNode.class);
    @Input ValueNode length;

    @Override
    public ValueNode findLength(FindLengthMode mode, ConstantReflectionProvider constantReflection) {
        return length;
    }

    public PiArrayNode(ValueNode object, ValueNode length, Stamp stamp) {
        super(TYPE, object, stamp, null);
        this.length = length;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (GraphUtil.arrayLength(object(), ArrayLengthProvider.FindLengthMode.SEARCH_ONLY, tool.getConstantReflection()) != length) {
            return this;
        }
        return super.canonical(tool);
    }

    /**
     * Changes the stamp of an object inside a snippet to be the stamp of the node replaced by the
     * snippet.
     */
    @NodeIntrinsic(Placeholder.class)
    public static native Object piArrayCastToSnippetReplaceeStamp(Object object, int length);

    /**
     * A placeholder node in a snippet that will be replaced with a {@link PiArrayNode} when the
     * snippet is instantiated.
     */
    @NodeInfo(cycles = CYCLES_0, size = SIZE_0)
    public static class Placeholder extends PiNode.Placeholder {

        public static final NodeClass<Placeholder> TYPE = NodeClass.create(Placeholder.class);
        @Input ValueNode length;

        protected Placeholder(ValueNode object, ValueNode length) {
            super(TYPE, object);
            this.length = length;
        }

        @Override
        public void makeReplacement(Stamp snippetReplaceeStamp) {
            PiArrayNode piArray = graph().addOrUnique(new PiArrayNode(object(), length, snippetReplaceeStamp));
            replaceAndDelete(piArray);
        }
    }
}
