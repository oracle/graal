/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.profile;

import com.sun.max.util.timer.*;

/**
 * This class represents a context in which profiling is performed. Typically,
 * a profiling context is thread-specific, meaning that it collects metrics such as
 * execution times on a per-thread basis.
 */
public class ContextTree {

    protected static class Node {
        protected final long id;
        protected Node sibling;
        protected Node child;
        protected Timer timer;

        public Node(long id) {
            this.id = id;
        }

        public Node findChild(long searchId) {
            Node pos = child;
            while (pos != null) {
                if (pos.id == this.id) {
                    return pos;
                }
                pos = pos.sibling;
            }
            return null;
        }

        public Node addChild(long searchId, Clock clock) {
            Node foundChild = findChild(searchId);
            if (foundChild == null) {
                foundChild = new Node(searchId);
                foundChild.timer = new SingleUseTimer(clock);
                foundChild.sibling = this.child;
                this.child = foundChild;
            }
            return foundChild;
        }
    }

    public static final int MAXIMUM_DEPTH = 1024;

    protected final Clock clock;
    protected final Node[] stack;
    protected int depth;

    public ContextTree(Clock clock) {
        this.clock = clock;
        this.stack = new Node[MAXIMUM_DEPTH];
        depth = 0;
        stack[0] = new Node(Long.MAX_VALUE);
    }

    public void enter(long id) {
        final Node top = stack[depth];
        final Node child = top.addChild(id, clock);
        // push a new profiling node onto the stack
        stack[++depth] = child;
        child.timer.start();
    }

    public void exit(long id) {
        while (depth > 0) {
            // pop all profiling nodes until we find the correct ID.
            final Node top = stack[depth--];
            top.timer.stop();
            if (top.id == id) {
                break;
            }
        }
    }
}
