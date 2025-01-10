/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.config.conditional;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.core.configure.ConfigurationFile;

/**
 * Models a call tree. Each node represents a method invocation, and each edge represents a call
 * from the parent method to the child method. A node's chain of parent pointers identifies the
 * calling context (i.e., call stack) at the point that the method was called. A node's
 * {@link #configuration} field is used to associate configuration metadata with the node's calling
 * context.
 *
 * <p>
 * For example, in the following graph,
 * </p>
 *
 * <pre>
 *                  Foo#foo
 *                  /    \
 *             Bar#bar   Baz#baz
 *                |         |
 *             Qux#qux   Qux#qux
 * </pre>
 *
 * the left {@code Qux#qux} node contains the configuration metadata traced for {@code Qux#qux} when
 * it was called by {@code Bar#bar} (which was itself called by {@code Foo#foo}). The right
 * {@code Qux#qux} represents a different calling context (with {@code Baz#baz} as the caller) and
 * tracks its configuration set independently of the left {@code Qux#qux}.
 * <p>
 * The {@link #createRoot() root node} of the tree is a sentinel node with no {@link #methodInfo}.
 * Its direct children represent entrypoint methods (i.e., methods at the base of the call stack).
 * </p>
 */
public final class MethodCallNode {

    public final MethodInfo methodInfo;
    public final MethodCallNode parent;
    public final Map<MethodInfo, MethodCallNode> calledMethods;
    public volatile ConfigurationSet configuration;

    private MethodCallNode(MethodInfo methodInfo, MethodCallNode parent) {
        this.methodInfo = methodInfo;
        this.parent = parent;
        this.calledMethods = new ConcurrentHashMap<>();
        this.configuration = null;
    }

    public MethodCallNode getOrCreateChild(MethodInfo info) {
        return calledMethods.computeIfAbsent(info, key -> new MethodCallNode(key, this));
    }

    public static MethodCallNode createRoot() {
        return new MethodCallNode(null, null);
    }

    public Set<MethodCallNode> getNodesWithNonEmptyConfig(ConfigurationFile configFile) {
        Set<MethodCallNode> nodesWithNonEmptyConfig = new HashSet<>();
        /*
         * Recursively construct a set of nodes with non empty config. These nodes will eventually
         * be printed. A node should be printed if it or any of it's children have configuration.
         */
        visitPostOrder(node -> {
            /* Nodes with configuration are always included */
            if (node.hasConfig(configFile)) {
                nodesWithNonEmptyConfig.add(node);
            }
            /* If a node is already included, also include its parent */
            if (nodesWithNonEmptyConfig.contains(node) && node.parent != null) {
                nodesWithNonEmptyConfig.add(node.parent);
            }
        });

        return nodesWithNonEmptyConfig;
    }

    public void mergeSubTree(MethodCallNode other, boolean mergeConfig) {
        if (mergeConfig) {
            configuration = getConfiguration().copyAndMerge(other.getConfiguration());
        }
        for (MethodCallNode child : other.calledMethods.values()) {
            calledMethods.compute(child.methodInfo, (key, value) -> {
                if (value == null) {
                    return child;
                } else {
                    value.mergeSubTree(child, true);
                    return value;
                }
            });
        }
    }

    public boolean hasConfig(ConfigurationFile configFile) {
        return configuration != null && !configuration.getConfiguration(configFile).isEmpty();
    }

    public ConfigurationSet getConfiguration() {
        if (configuration == null) {
            synchronized (this) {
                if (configuration == null) {
                    configuration = new ConfigurationSet();
                }
            }
        }
        return configuration;
    }

    public void visitPostOrder(Consumer<MethodCallNode> methodCallNodeConsumer) {
        for (MethodCallNode node : calledMethods.values()) {
            node.visitPostOrder(methodCallNodeConsumer);
        }
        methodCallNodeConsumer.accept(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MethodCallNode that = (MethodCallNode) o;
        return methodInfo.equals(that.methodInfo) && Objects.equals(parent, that.parent) && calledMethods.equals(that.calledMethods);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodInfo, parent);
    }
}
