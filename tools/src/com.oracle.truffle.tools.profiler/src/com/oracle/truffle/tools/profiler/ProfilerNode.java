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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents a node in the call tree built up by sampling the shadow stack. Additional data can be
 * attached to this class through the template parameter, allowing individual tools to attached
 * needed data to the call tree.
 *
 * @param <T> The type of data that should be associated with this node.
 * @since 0.30
 */
public final class ProfilerNode<T> {

    ProfilerNode(ProfilerNode<T> parent, StackTraceEntry sourceLocation, T payload) {
        this.parent = parent;
        this.sourceLocation = sourceLocation;
        this.payload = payload;
    }

    ProfilerNode() {
        this.parent = null;
        this.sourceLocation = null;
        this.payload = null;
    }

    private final T payload;
    private final ProfilerNode<T> parent;
    private final StackTraceEntry sourceLocation;
    Map<StackTraceEntry, ProfilerNode<T>> children;

    /**
     * @return the children of this {@link ProfilerNode}
     * @since 0.30
     */
    public Collection<ProfilerNode<T>> getChildren() {
        if (children == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(children.values());
    }

    /**
     * @return the parent of this {@link ProfilerNode}
     * @since 0.30
     */
    public ProfilerNode<T> getParent() {
        return parent;
    }

    /**
     * @return true if the parent chain contains a {@link ProfilerNode} with the same
     *         {@link StackTraceEntry}, otherwise false
     * @since 0.30
     */
    public boolean isRecursive() {
        return isRecursiveImpl(this);
    }

    private boolean isRecursiveImpl(ProfilerNode<T> source) {
        if (parent.sourceLocation == null) {
            return false;
        }
        if (parent.sourceLocation.equals(source.sourceLocation)) {
            return true;
        }
        return parent.isRecursiveImpl(source);
    }

    /**
     * @return the {@link SourceSection} associated with this {@link ProfilerNode}
     * @since 0.30
     */
    public SourceSection getSourceSection() {
        return sourceLocation.getSourceSection();
    }

    /**
     * @return The name of the {@linkplain RootNode root node} in which this {@link ProfilerNode}
     *         appears.
     * @since 0.30
     */
    public String getRootName() {
        return sourceLocation.getRootName();
    }

    /**
     * Returns a set tags a stack location marked with. Common tags are {@link RootTag root},
     * {@link StatementTag statement} and {@link ExpressionTag expression}. Whether statement or
     * expression stack trace entries appear depends on the configured
     * {@link CPUSampler#setFilter(com.oracle.truffle.api.instrumentation.SourceSectionFilter)
     * filter}.
     *
     * @since 0.30
     */
    public Set<Class<?>> getTags() {
        return sourceLocation.getTags();
    }

    /**
     * @return The additional information attached to this node through the template parameter
     * @since 0.30
     */
    public T getPayload() {
        return payload;
    }

    ProfilerNode<T> findChild(StackTraceEntry childLocation) {
        if (children != null) {
            return children.get(childLocation);
        }
        return null;
    }

    void addChild(StackTraceEntry childLocation, ProfilerNode<T> child) {
        if (children == null) {
            children = new HashMap<>();
        }
        children.put(childLocation, child);
    }

    StackTraceEntry getSourceLocation() {
        return sourceLocation;
    }

    void deepCopyChildrenFrom(ProfilerNode<T> node, Function<T, T> copyPayload) {
        for (ProfilerNode<T> child : node.getChildren()) {
            final StackTraceEntry childSourceLocation = child.getSourceLocation();
            T childPayload = child.getPayload();
            T destinationPayload = copyPayload.apply(childPayload);
            ProfilerNode<T> destinationChild = new ProfilerNode<>(this, childSourceLocation, destinationPayload);
            if (children == null) {
                children = new HashMap<>();
            }
            children.put(childSourceLocation, destinationChild);
            destinationChild.deepCopyChildrenFrom(child, copyPayload);
        }
    }

    void deepMergeChildrenFrom(ProfilerNode<T> node, BiConsumer<T, T> mergePayload, Supplier<T> payloadFactory) {
        for (ProfilerNode<T> child : node.getChildren()) {
            final StackTraceEntry childSourceLocation = child.getSourceLocation();
            final T childPayload = child.getPayload();
            ProfilerNode<T> destinationChild = findBySourceLocation(childSourceLocation);
            if (destinationChild == null) {
                T destinationPayload = payloadFactory.get();
                mergePayload.accept(childPayload, destinationPayload);
                destinationChild = new ProfilerNode<>(this, childSourceLocation, destinationPayload);
                if (children == null) {
                    children = new HashMap<>();
                }
                children.put(childSourceLocation, destinationChild);
            } else {
                mergePayload.accept(childPayload, destinationChild.getPayload());
            }
            destinationChild.deepMergeChildrenFrom(child, mergePayload, payloadFactory);
        }
    }

    private ProfilerNode<T> findBySourceLocation(StackTraceEntry targetSourceLocation) {
        if (children != null) {
            for (ProfilerNode<T> child : children.values()) {
                if (child.getSourceLocation().equals(targetSourceLocation)) {
                    return child;
                }
            }
        }
        return null;
    }
}
