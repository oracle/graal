/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;

@NodeInfo
public class VirtualBoxingNode extends VirtualInstanceNode {

    private final Kind boxingKind;

    public static VirtualBoxingNode create(ResolvedJavaType type, Kind boxingKind) {
        return new VirtualBoxingNodeGen(type, boxingKind);
    }

    VirtualBoxingNode(ResolvedJavaType type, Kind boxingKind) {
        super(type, false);
        this.boxingKind = boxingKind;
    }

    @Override
    public VirtualBoxingNode duplicate() {
        return VirtualBoxingNode.create(type(), boxingKind);
    }

    @Override
    public ValueNode getMaterializedRepresentation(FixedNode fixed, ValueNode[] entries, LockState locks) {
        assert entries.length == 1;
        assert locks == null;
        return BoxNode.create(entries[0], type(), boxingKind);
    }
}
