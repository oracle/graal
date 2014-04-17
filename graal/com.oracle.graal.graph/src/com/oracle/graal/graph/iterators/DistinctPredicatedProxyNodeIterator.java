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
package com.oracle.graal.graph.iterators;

import java.util.*;

import com.oracle.graal.graph.*;

public class DistinctPredicatedProxyNodeIterator<T extends Node> extends PredicatedProxyNodeIterator<T> {

    private NodeBitMap visited;

    public DistinctPredicatedProxyNodeIterator(Iterator<T> iterator, NodePredicate predicate) {
        super(iterator, predicate);
    }

    @Override
    protected void forward() {
        if (current == null) {
            super.forward();
            while (!accept(current)) {
                current = null;
                super.forward();
            }
        }
    }

    private boolean accept(T n) {
        if (n == null) {
            return true;
        }
        if (visited == null) {
            visited = n.graph().createNodeBitMap(true);
        }
        boolean accept = !visited.isMarked(n);
        visited.mark(n);
        return accept;
    }
}
