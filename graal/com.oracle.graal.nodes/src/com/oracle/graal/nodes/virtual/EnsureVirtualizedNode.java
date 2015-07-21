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
package com.oracle.graal.nodes.virtual;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;

@NodeInfo
public final class EnsureVirtualizedNode extends FixedWithNextNode implements Virtualizable, Lowerable {

    public static final NodeClass<EnsureVirtualizedNode> TYPE = NodeClass.create(EnsureVirtualizedNode.class);

    @Input ValueNode object;
    private final boolean localOnly;

    public EnsureVirtualizedNode(ValueNode object, boolean localOnly) {
        super(TYPE, StampFactory.forVoid());
        this.object = object;
        this.localOnly = localOnly;
    }

    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null && state.getState() == EscapeState.Virtual) {
            if (state.getVirtualObject() instanceof VirtualBoxingNode) {
                Throwable exception = new VerificationError("ensureVirtual is not valid for boxing objects: %s", state.getVirtualObject().type().getName());
                throw GraphUtil.approxSourceException(this, exception);
            }
            if (!localOnly) {
                state.setEnsureVirtualized(true);
            }
            tool.delete();
        }
    }

    public void lower(LoweringTool tool) {
        ensureVirtualFailure(this, object.stamp());
    }

    public static void ensureVirtualFailure(Node location, Stamp stamp) {
        Throwable exception = new VerificationError("Object should not be materialized (stamp=%s):", stamp);
        throw GraphUtil.approxSourceException(location, exception);
    }
}
