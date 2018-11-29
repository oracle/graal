/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.nodes;

import java.util.Iterator;
import java.util.NoSuchElementException;

final class NodeIterator implements Iterator<Node> {

    private final NodeClass nodeClass;
    private final Object[] fields;
    private final Node node;
    private Node next;
    private int fieldsIndex;
    private int childrenIndex;
    private Object[] children;

    NodeIterator(NodeClass nodeClass, Node node, Object[] fields) {
        this.nodeClass = nodeClass;
        this.fields = fields;
        this.node = node;
        advance();
    }

    private void advance() {
        if (advanceChildren()) {
            return;
        }
        while (fieldsIndex < fields.length) {
            Object field = fields[fieldsIndex++];
            if (nodeClass.isChildField(field)) {
                next = (Node) nodeClass.getFieldObject(field, node);
                if (next != null) {
                    return;
                }
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
        }

        while (childrenIndex < children.length) {
            next = (Node) children[childrenIndex];
            childrenIndex++;
            if (next != null) {
                return true;
            }
        }

        children = null;
        childrenIndex = 0;
        return false;
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
