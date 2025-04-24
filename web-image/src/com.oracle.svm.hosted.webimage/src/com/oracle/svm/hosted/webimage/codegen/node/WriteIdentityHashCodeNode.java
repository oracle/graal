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

package com.oracle.svm.hosted.webimage.codegen.node;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;

/**
 * Sets the identity hash code field for the given object.
 *
 * @see com.oracle.svm.hosted.webimage.snippets.WebImageIdentityHashCodeSnippets
 */
@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public class WriteIdentityHashCodeNode extends FixedWithNextNode {

    public static final NodeClass<WriteIdentityHashCodeNode> TYPE = NodeClass.create(WriteIdentityHashCodeNode.class);

    @Input ValueNode object;
    @Input ValueNode hashCode;

    public WriteIdentityHashCodeNode(ValueNode object, ValueNode hashCode) {
        super(TYPE, StampFactory.forVoid());
        this.object = object;
        this.hashCode = hashCode;
    }

    public ValueNode getObject() {
        return object;
    }

    public ValueNode getHashCode() {
        return hashCode;
    }

    @NodeIntrinsic
    public static native void set(Object o, int hashCode);
}
