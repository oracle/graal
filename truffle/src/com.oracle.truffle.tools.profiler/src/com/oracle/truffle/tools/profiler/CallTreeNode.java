/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler;

import com.oracle.truffle.api.source.SourceSection;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class CallTreeNode<T> {

    protected final T payload;
    protected final CallTreeNode<T> parent;
    protected final SourceLocation sourceLocation;
    protected Map<SourceLocation, CallTreeNode<T>> children;
    protected final Object sync;

    CallTreeNode(Object sync, T payload) {
        if (sync == null) {
            throw new IllegalStateException("Sync must be provided for root");
        }
        this.parent = null;
        this.sourceLocation = null;
        this.sync = sync;
        this.payload = payload;
    }

    CallTreeNode(CallTreeNode<T> parent, SourceLocation sourceLocation, T payload) {
        this.parent = parent;
        this.sourceLocation = sourceLocation;
        this.sync = parent.sync;
        this.payload = payload;
    }

    public Collection<CallTreeNode<T>> getChildren() {
        if (children == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(children.values());
    }

    public CallTreeNode<T> getParent() {
        return parent;
    }

    CallTreeNode<T> findChild(SourceLocation childLocation) {
        if (children != null) {
            return children.get(childLocation);
        }
        return null;
    }

    void addChild(SourceLocation childLocation, CallTreeNode<T> child) {
        synchronized (sync) {
            if (children == null) {
                children = new HashMap<>();
            }
            children.put(childLocation, child);
        }
    }

    public boolean isRecursive() {
        return isRecursiveImpl(this);
    }

    private boolean isRecursiveImpl(CallTreeNode<T> source) {
        if (parent.sourceLocation == null) {
            return false;
        }
        if (parent.sourceLocation.equals(source.sourceLocation)) {
            return true;
        }
        return parent.isRecursiveImpl(source);
    }

    public SourceSection getSourceSection() {
        return sourceLocation.getSourceSection();
    }

    public String getRootName() {
        return sourceLocation.getRootName();
    }

    public Set<Class<?>> getTags() {
        return sourceLocation.getTags();
    }

    SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public T getPayload() {
        return payload;
    }
}
