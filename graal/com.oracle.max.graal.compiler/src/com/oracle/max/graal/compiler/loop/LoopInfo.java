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
package com.oracle.max.graal.compiler.loop;

import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.util.*;

public class LoopInfo {
    private final NodeMap<Loop> nodeToLoop;
    private final List<Loop> rootLoops;

    public LoopInfo(NodeMap<Loop> nodeToLoop, List<Loop> rootLoops) {
        this.nodeToLoop = nodeToLoop;
        this.rootLoops = rootLoops;
    }

    public Loop loop(Node n) {
        return nodeToLoop.get(n);
    }

    public List<Loop> rootLoops() {
        return rootLoops;
    }

    public Iterable<Loop> loops() {
        return new Iterable<Loop>() {
            @Override
            public Iterator<Loop> iterator() {
                return new TreeIterators.PrefixTreeIterator<Loop>(rootLoops()) {
                    @Override
                    protected Iterable<Loop> children(Loop node) {
                        return node.children();
                    }
                };
            }
        };
    }

    public void print() {
        for (Loop loop : rootLoops) {
            print(loop);
        }
    }

    private void print(Loop loop) {
        TTY.println("%s", loop.loopBegin());
        TTY.println("-- subnodes");
        for (Node node : loop.fixedNodes()) {
            TTY.println("  " + node);
        }
        TTY.println("-- subloops");
        for (Loop sub : loop.children()) {
            print(sub);
        }
        TTY.println("-- sub");
        TTY.println();
    }
}
