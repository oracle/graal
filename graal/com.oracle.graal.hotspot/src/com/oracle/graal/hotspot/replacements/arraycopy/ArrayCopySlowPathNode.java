/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.replacements.arraycopy;

import static jdk.internal.jvmci.meta.LocationIdentity.any;
import jdk.internal.jvmci.code.BytecodeFrame;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.LocationIdentity;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.NamedLocationIdentity;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.replacements.SnippetTemplate;
import com.oracle.graal.replacements.nodes.BasicArrayCopyNode;

@NodeInfo
public final class ArrayCopySlowPathNode extends BasicArrayCopyNode {

    public static final NodeClass<ArrayCopySlowPathNode> TYPE = NodeClass.create(ArrayCopySlowPathNode.class);

    private final SnippetTemplate.SnippetInfo snippet;

    /**
     * Extra context for the slow path snippet.
     */
    private final Object argument;

    public ArrayCopySlowPathNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, JavaKind elementKind, SnippetTemplate.SnippetInfo snippet, Object argument) {
        super(TYPE, src, srcPos, dest, destPos, length, elementKind, BytecodeFrame.INVALID_FRAMESTATE_BCI);
        assert StampTool.isPointerNonNull(src) && StampTool.isPointerNonNull(dest) : "must have been null checked";
        this.snippet = snippet;
        this.argument = argument;
    }

    @NodeIntrinsic
    public static native void arraycopy(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind,
                    @ConstantNodeParameter SnippetTemplate.SnippetInfo snippet, @ConstantNodeParameter Object argument);

    public SnippetTemplate.SnippetInfo getSnippet() {
        return snippet;
    }

    public Object getArgument() {
        return argument;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        if (elementKind != null) {
            return NamedLocationIdentity.getArrayLocation(elementKind);
        }
        return any();
    }

    public void setBci(int bci) {
        this.bci = bci;
    }
}
