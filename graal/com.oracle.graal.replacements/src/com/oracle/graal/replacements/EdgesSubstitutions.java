/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.replacements.nodes.*;

/**
 * Substitutions for improving the performance of some critical methods in {@link Edges}. These
 * substitutions improve the performance by forcing the relevant methods to be inlined
 * (intrinsification being a special form of inlining) and removing a checked cast. The latter
 * cannot be done directly in Java code as {@link DeferredPiNode} is not available to the project
 * containing {@link Edges}.
 */
@ClassSubstitution(Edges.class)
public class EdgesSubstitutions {

    @MethodSubstitution
    private static Node getNode(Node node, long offset) {
        return PiNode.piCast(UnsafeLoadNode.load(node, offset, Kind.Object, LocationIdentity.ANY_LOCATION), Node.class);
    }

    @MethodSubstitution
    private static NodeList<?> getNodeList(Node node, long offset) {
        return PiNode.piCast(UnsafeLoadNode.load(node, offset, Kind.Object, LocationIdentity.ANY_LOCATION), NodeList.class);
    }

    @MethodSubstitution
    private static void putNode(Node node, long offset, Node value) {
        UnsafeStoreNode.store(node, offset, value, Kind.Object, LocationIdentity.ANY_LOCATION);
    }

    @MethodSubstitution
    private static void putNodeList(Node node, long offset, NodeList<?> value) {
        UnsafeStoreNode.store(node, offset, value, Kind.Object, LocationIdentity.ANY_LOCATION);
    }
}
