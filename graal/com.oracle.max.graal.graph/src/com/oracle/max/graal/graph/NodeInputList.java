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
package com.oracle.max.graal.graph;


public final class NodeInputList<T extends Node> extends NodeList<T> {

    private final Node self;

    public NodeInputList(Node self, int initialSize) {
        super(initialSize);
        this.self = self;
    }

    public NodeInputList(Node self) {
        this.self = self;
    }

    public NodeInputList(Node self, T[] elements) {
        super(elements);
        assert self.usages() == null;
        this.self = self;
    }

    @Override
    protected void update(T oldNode, T newNode) {
        self.updateUsages(oldNode, newNode);
    }

    @Override
    public boolean add(T node) {
        assert !node.isDeleted();
        self.incModCount();
        return super.add(node);
    }

    @Override
    public T remove(int index) {
        self.incModCount();
        return super.remove(index);
    }

    @Override
    public boolean remove(Object node) {
        self.incModCount();
        return super.remove(node);
    }

    @Override
    public void clear() {
        self.incModCount();
        super.clear();
    }

    @Override
    void copy(NodeList<T> other) {
        self.incModCount();
        super.copy(other);
    }

    @Override
    public void setAll(NodeList<T> values) {
        self.incModCount();
        super.setAll(values);
    }
}
