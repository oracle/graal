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
package org.graalvm.compiler.replacements.arraycopy;

import static org.graalvm.word.LocationIdentity.any;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.replacements.nodes.BasicArrayCopyNode;
import org.graalvm.word.LocationIdentity;

/**
 * This node intrinsifies {@link System#arraycopy}.
 *
 * Lowering is implemented in the platform/VM specific
 * {@link org.graalvm.compiler.nodes.spi.LoweringProvider LoweringProvider}. Most of them eventually
 * go through
 * {@link ArrayCopySnippets.Templates#lower(ArrayCopyNode, boolean, org.graalvm.compiler.nodes.spi.LoweringTool)}.
 */
@NodeInfo
public final class ArrayCopyNode extends BasicArrayCopyNode implements Lowerable {

    public static final NodeClass<ArrayCopyNode> TYPE = NodeClass.create(ArrayCopyNode.class);

    protected final boolean forceAnyLocation;

    protected ArrayCopyNode(int bci, ValueNode src, ValueNode srcPos, ValueNode dst, ValueNode dstPos, ValueNode length, boolean forceAnyLocation) {
        super(TYPE, src, srcPos, dst, dstPos, length, null, bci);
        assert StampTool.isPointerNonNull(src) && StampTool.isPointerNonNull(dst) : "must have been null checked";
        this.forceAnyLocation = forceAnyLocation;
        if (!forceAnyLocation) {
            elementKind = selectComponentKind(this);
        } else {
            assert elementKind == null;
        }
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        if (!forceAnyLocation && elementKind == null) {
            elementKind = selectComponentKind(this);
        }
        if (elementKind != null) {
            return NamedLocationIdentity.getArrayLocation(elementKind);
        }
        return any();
    }

    public boolean killsAnyLocation() {
        return forceAnyLocation;
    }
}
