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
import com.oracle.truffle.tools.profiler.impl.SourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents a node in the call tree built up by sampling the shadow stack. Additional data can be
 * attached to this class through the template parameter, allowing individual tools to attached
 * needed data to the call tree.
 *
 * @param <T> The type of data that should be associated with this node.
 * @since 0.29
 */
public final class CallTreeNode<T> {

    CallTreeNode(CallTreeNode<T> parent, SourceLocation sourceLocation, T payload) {
        this.parent = parent;
        this.sourceLocation = sourceLocation;
        this.sync = parent.sync;
        this.payload = payload;
    }

    CallTreeNode(Object sync, T payload) {
        if (sync == null) {
            throw new IllegalStateException("Sync must be provided for root");
        }
        this.parent = null;
        this.sourceLocation = null;
        this.sync = sync;
        this.payload = payload;
    }

    private final T payload;
    private final CallTreeNode<T> parent;
    private final SourceLocation sourceLocation;
    Map<SourceLocation, CallTreeNode<T>> children;
    private final Object sync;

    /**
     * @return the children of this {@link CallTreeNode}
     * @since 0.29
     */
    public Collection<CallTreeNode<T>> getChildren() {
        if (children == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(children.values());
    }

    /**
     * @return the parent of this {@link CallTreeNode}
     * @since 0.29
     */
    public CallTreeNode<T> getParent() {
        return parent;
    }

    /**
     * @return true if the parent chain contains a {@link CallTreeNode} with the same
     *         {@link SourceLocation}, otherwise false
     * @since 0.29
     */
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

    /**
     * @return the {@link SourceSection} associated with this {@link CallTreeNode}
     * @since 0.29
     */
    public SourceSection getSourceSection() {
        return sourceLocation.getSourceSection();
    }

    /**
     * @return The name of the {@linkplain com.oracle.truffle.api.nodes.RootNode root node} in which
     *         the {@link SourceLocation} associated with this {@link CallTreeNode} appears
     * @since 0.29
     */
    public String getRootName() {
        return sourceLocation.getRootName();
    }

    /**
     * @return A set of {@link com.oracle.truffle.api.instrumentation.StandardTags tags} for the
     *         {@link SourceLocation} associated with this {@link CallTreeNode}
     * @since 0.29
     */
    public Set<Class<?>> getTags() {
        return sourceLocation.getTags();
    }

    /**
     * @return The additional information attached to this node through the template parameter
     * @since 0.29
     */
    public T getPayload() {
        return payload;
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

    SourceLocation getSourceLocation() {
        return sourceLocation;
    }

}
