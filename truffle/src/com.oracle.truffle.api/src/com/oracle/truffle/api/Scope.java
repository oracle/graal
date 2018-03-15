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
package com.oracle.truffle.api;

import java.util.Objects;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Representation of a scope in a guest language program. The scope contains a set of declared and
 * valid variables. The scopes can be both lexical and dynamic.
 *
 * @since 0.30
 */
public final class Scope {

    private static final Scope EMPTY = new Scope();

    private final String name;
    private final Node node;
    private final Object arguments;
    private final Object variables;

    private Scope() {
        name = null;
        node = null;
        arguments = null;
        variables = null;
    }

    Scope(String name, Node node, Object arguments, Object variables) {
        this.name = name;
        this.node = node;
        assert arguments == null || TruffleLanguage.AccessAPI.interopAccess().isTruffleObject(arguments) : Objects.toString(arguments);
        this.arguments = arguments;
        assert TruffleLanguage.AccessAPI.interopAccess().isTruffleObject(variables) : Objects.toString(variables);
        this.variables = variables;
    }

    /**
     * Create a new Scope builder.
     * <p>
     * The properties representing the variables needs to have deterministic iteration order,
     * variable declaration order is recommended.
     *
     * @param name name of the scope, a name description like block, name of a function, closure,
     *            script, module, etc.
     * @param variables a {@link com.oracle.truffle.api.interop.TruffleObject} containing the
     *            variables as its properties, never <code>null</code>. Must respond true to
     *            HAS_KEYS.
     * @since 0.30
     */
    public static Builder newBuilder(String name, Object variables) {
        return EMPTY.new Builder(name, variables);
    }

    /**
     * Human readable name of this scope. A name description like block, name of a function,
     * closure, script, module, etc.
     *
     * @since 0.30
     */
    public String getName() {
        return name;
    }

    /**
     * Get a node representing this scope. Functional scopes return the appropriate {@link RootNode}
     * , top scopes do not have a node associated.
     *
     * @return the node, or <code>null<code> when no node is associated.
     * @since 0.30
     */
    public Node getNode() {
        return node;
    }

    /**
     * Get variables declared in this scope. When a {@link Node} (and a {@link Frame}) was provided
     * when this scope was obtained, variables are valid at that specified {@link Node} (and
     * {@link Frame}).
     * <p>
     * The properties representing the variables have deterministic iteration order, variable
     * declaration order is preferred.
     *
     * @return A {@link com.oracle.truffle.api.interop.TruffleObject} containing the variables as
     *         its properties, never <code>null</code>.
     * @since 0.30
     */
    public Object getVariables() {
        return variables;
    }

    /**
     * Get arguments of this scope. There might be different arguments returned in local scopes when
     * different {@link Frame} instances were provided.
     * <p>
     * The properties representing the arguments have deterministic iteration order, argument
     * declaration order is preferred.
     *
     * @return A {@link com.oracle.truffle.api.interop.TruffleObject} containing the arguments as
     *         its properties for named arguments, or as its array for unnamed arguments. A
     *         <code>null</code> is returned when this scope does not have a concept of arguments.
     *         An empty TruffleObject is provided when it has a sense to have arguments (e.g.
     *         function scope), but there are none.
     * @since 0.30
     */
    public Object getArguments() {
        return arguments;
    }

    /**
     * Builder to create a new {@link Scope} object.
     *
     * @since 0.30
     */
    public final class Builder {

        private final String name;
        private Node node;
        private Object arguments;
        private final Object variables;

        Builder(String name, Object variables) {
            assert name != null;
            assert TruffleLanguage.AccessAPI.interopAccess().isTruffleObject(variables) : Objects.toString(variables);
            this.name = name;
            this.variables = variables;
        }

        /**
         * Set node representing the scope. It is expected that the appropriate
         * block/function/closure/etc. node is provided.
         *
         * @param node the scope's node
         * @since 0.30
         */
        @SuppressWarnings("hiding")
        public Builder node(Node node) {
            this.node = node;
            return this;
        }

        /**
         * Set arguments of the scope.
         * <p>
         * The properties representing the arguments needs to have deterministic iteration order,
         * argument declaration order is recommended.
         *
         * @param arguments arguments of the scope
         * @since 0.30
         */
        @SuppressWarnings("hiding")
        public Builder arguments(Object arguments) {
            assert arguments == null || TruffleLanguage.AccessAPI.interopAccess().isTruffleObject(arguments) : Objects.toString(arguments);
            this.arguments = arguments;
            return this;
        }

        /**
         * Uses configuration of this builder to create new {@link Scope} object.
         *
         * @since 0.30
         */
        public Scope build() {
            return new Scope(name, node, arguments, variables);
        }
    }
}
