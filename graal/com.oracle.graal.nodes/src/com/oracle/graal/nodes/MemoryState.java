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
package com.oracle.graal.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;

public class MemoryState extends VirtualState {

    private MemoryMap<Node> memoryMap;
    @Input private Node object;

    public MemoryState(MemoryMap<Node> memoryMap, FixedNode object) {
        this.memoryMap = memoryMap;
        this.object = object;
    }

    public Node object() {
        return object;
    }

    public MemoryMap<Node> getMemoryMap() {
        return memoryMap;
    }

    @Override
    public VirtualState duplicateWithVirtualState() {
        throw new GraalInternalError("should not reach here");
    }

    @Override
    public void applyToNonVirtual(NodeClosure<? super ValueNode> closure) {
        throw new GraalInternalError("should not reach here");
    }

    @Override
    public void applyToVirtual(VirtualClosure closure) {
        throw new GraalInternalError("should not reach here");
    }

    @Override
    public boolean isPartOfThisState(VirtualState state) {
        throw new GraalInternalError("should not reach here");
    }
}
