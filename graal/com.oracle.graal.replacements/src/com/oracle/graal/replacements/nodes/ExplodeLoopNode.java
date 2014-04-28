/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import java.util.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.replacements.Snippet.VarargsParameter;

/**
 * Placeholder node to denote to snippet preparation that the following loop must be completely
 * unrolled.
 * 
 * @see VarargsParameter
 */
public final class ExplodeLoopNode extends FixedWithNextNode {

    public ExplodeLoopNode() {
        super(StampFactory.forVoid());
    }

    public LoopBeginNode findLoopBegin() {
        Node next = next();
        ArrayList<Node> succs = new ArrayList<>();
        while (!(next instanceof LoopBeginNode)) {
            assert next != null : "cannot find loop after " + this;
            for (Node n : next.cfgSuccessors()) {
                succs.add(n);
            }
            if (succs.size() == 1) {
                next = succs.get(0);
            } else {
                return null;
            }
        }
        return (LoopBeginNode) next;
    }

    /**
     * A call to this method must be placed immediately prior to the loop that is to be exploded.
     */
    @NodeIntrinsic
    public static native void explodeLoop();
}
