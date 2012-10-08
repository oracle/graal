/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.cfg;

import java.util.*;

import com.oracle.graal.nodes.*;

public class Loop {

    public final Loop parent;
    public final List<Loop> children;

    public final int depth;
    public final int index;
    public final Block header;
    public final List<Block> blocks;
    public final List<Block> exits;

    protected Loop(Loop parent, int index, Block header) {
        this.parent = parent;
        if (parent != null) {
            this.depth = parent.depth + 1;
            parent.children.add(this);
        } else {
            this.depth = 1;
        }
        this.index = index;
        this.header = header;
        this.blocks = new ArrayList<>();
        this.children = new ArrayList<>();
        this.exits = new ArrayList<>();
    }

    @Override
    public String toString() {
        return "loop " + index + " depth " + depth + (parent != null ? " outer " + parent.index : "");
    }

    public LoopBeginNode loopBegin() {
        return (LoopBeginNode) header.getBeginNode();
    }
}
