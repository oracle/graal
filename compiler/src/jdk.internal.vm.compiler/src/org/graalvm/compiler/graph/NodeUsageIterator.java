/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.graph;

import java.util.Iterator;
import java.util.NoSuchElementException;

class NodeUsageIterator implements Iterator<Node> {

    final Node node;
    int index = -1;
    Node current;

    void advance() {
        current = null;
        index++;
        if (index == 0) {
            current = node.usage0;
        } else if (index == 1) {
            current = node.usage1;
        } else {
            int relativeIndex = index - Node.INLINE_USAGE_COUNT;
            if (relativeIndex < node.extraUsagesCount) {
                current = node.extraUsages[relativeIndex];
            }
        }
    }

    NodeUsageIterator(Node node) {
        this.node = node;
        advance();
    }

    @Override
    public boolean hasNext() {
        return current != null;
    }

    @Override
    public Node next() {
        Node result = current;
        if (result == null) {
            throw new NoSuchElementException();
        }
        advance();
        return result;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
