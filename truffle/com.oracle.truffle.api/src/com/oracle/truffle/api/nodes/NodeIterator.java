/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import java.util.Iterator;
import java.util.NoSuchElementException;

final class NodeIterator implements Iterator<Node> {

    private final NodeClass nodeClass;
    private final Iterator<? extends Object> fields;
    private final Node node;
    private Node next;
    private int childrenIndex;
    private Object[] children;

    NodeIterator(NodeClass nodeClass, Node node, Iterator<? extends Object> fields) {
        this.nodeClass = nodeClass;
        this.fields = fields;
        this.node = node;
        advance();
    }

    private void advance() {
        if (advanceChildren()) {
            return;
        }
        while (fields.hasNext()) {
            Object field = fields.next();
            if (nodeClass.isChildField(field)) {
                next = (Node) nodeClass.getFieldObject(field, node);
                return;
            } else if (nodeClass.isChildrenField(field)) {
                children = (Object[]) nodeClass.getFieldObject(field, node);
                childrenIndex = 0;
                if (advanceChildren()) {
                    return;
                }
            } else if (nodeClass.nodeFieldsOrderedByKind()) {
                break;
            }
        }
        next = null;
    }

    private boolean advanceChildren() {
        if (children == null) {
            return false;
        } else if (childrenIndex < children.length) {
            next = (Node) children[childrenIndex];
            childrenIndex++;
            return true;
        } else {
            children = null;
            childrenIndex = 0;
            return false;
        }
    }

    public boolean hasNext() {
        return next != null;
    }

    public Node next() {
        Node result = next;
        if (result == null) {
            throw new NoSuchElementException();
        }
        advance();
        return result;
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

}
