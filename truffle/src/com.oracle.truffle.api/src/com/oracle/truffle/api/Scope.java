/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
