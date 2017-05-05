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
package com.oracle.truffle.api.metadata;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Provider of guest language scope information.
 * <p>
 * Register the scope provider service for a guest language by directly implementing the
 * {@link com.oracle.truffle.api.TruffleLanguage} with this interface.
 *
 * @since 0.26
 */
public interface ScopeProvider<C> {

    /**
     * Find a hierarchy of scopes enclosing the given {@link Node node}. There must be at least one
     * scope provided, that corresponds to the enclosing function. The language might provide
     * additional block scopes, closure scopes, etc. The scope hierarchy should correspond with the
     * scope nesting, from the inner-most to the outer-most. The scopes are expected to contain
     * variables valid at the associated node.
     * <p>
     * In general, there can be a different list of scopes with different variables and arguments
     * returned for different {@link Frame} instances, as scopes may depend on runtime information.
     * Known lexical scopes are returned when <code>frame</code> argument is <code>null</code>.
     * <p>
     * The
     * {@link Scope#findScopes(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env, com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.frame.Frame)}
     * provides result of this method called on the implementation of the enclosing {@link RootNode}
     * . When the guest language does not implement this service, the enclosing {@link RootNode}'s
     * scope with variables read from its {@link FrameDescriptor}'s {@link FrameSlot}s is provided
     * by default.
     *
     * @param langContext the language execution context
     * @param node a node to find the enclosing scopes for. The node is inside a {@link RootNode}
     *            associated with this language.
     * @param frame The current frame the node is in, or <code>null</code> for lexical access when
     *            the program is not running, or is not suspended at the node's location.
     * @return an instance of {@link AbstractScope}.
     * @since 0.26
     */
    AbstractScope findScope(C langContext, Node node, Frame frame);

    /**
     * Abstraction of a scope in a guest language program. The scope is a section of a program that
     * contains a set of declared and valid variables. The scopes can be both lexical and dynamic.
     *
     * @since 0.26
     */
    abstract class AbstractScope {

        /**
         * @since 0.26
         */
        protected AbstractScope() {
        }

        /**
         * Human readable name of this scope. A name description like block, name of a function,
         * closure, script, module, etc.
         *
         * @since 0.26
         */
        protected abstract String getName();

        /**
         * Get a node representing this scope. Functional scopes should return the appropriate
         * {@link RootNode}.
         *
         * @return the node, or <code>null<code> when no node is associated.
         * @since 0.26
         */
        protected abstract Node getNode();

        /**
         * Provide variables declared in this scope and valid at the {@link Node} passed to
         * {@link ScopeProvider#findScope(java.lang.Object, com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.frame.Frame)}
         * . In general, there can be different variables returned when different {@link Frame}
         * instances are provided.
         * 
         * @param frame The current frame, or <code>null</code> for lexical access when the program
         *            is not running, or is not suspended at the scope's location. The variables
         *            might not be readable/writable with the <code>null</code> frame.
         * @return A {@link com.oracle.truffle.api.interop.TruffleObject} containing the variables
         *         as its properties. Should not be <code>null</code>, provide an empty
         *         TruffleObject when no variables are available.
         * @since 0.26
         */
        protected abstract Object getVariables(Frame frame);

        /**
         * Provide arguments of this scope. In general, there can be different arguments returned
         * when different {@link Frame} instances are provided.
         *
         * @param frame The current frame, or <code>null</code> for lexical access when the program
         *            is not running, or is not suspended at the scope's location. The arguments
         *            might not be readable/writable with the <code>null</code> frame.
         * @return A {@link com.oracle.truffle.api.interop.TruffleObject} containing the arguments
         *         as its properties for named arguments, or as its array for unnamed arguments.
         *         Return <code>null</code> when this scope does not have a concept of arguments.
         *         Return an empty TruffleObject when it has a sense to have arguments (e.g.
         *         function scope), but there are none.
         * @since 0.26
         */
        protected abstract Object getArguments(Frame frame);

        /**
         * Find a parent scope.
         *
         * @return the parent scope, or <code>null</code> when there is none.
         * @since 0.26
         */
        protected abstract AbstractScope findParent();

        /**
         * Convert an implementation of scope hierarchy to an iterable.
         */
        final Iterable<Scope> toIterable() {
            return new Iterable<Scope>() {
                @Override
                public Iterator<Scope> iterator() {
                    return new Iterator<Scope>() {

                        private AbstractScope currentScope = AbstractScope.this;
                        private AbstractScope oldScope = null;

                        @Override
                        public boolean hasNext() {
                            if (currentScope == null && oldScope != null) {
                                currentScope = oldScope.findParent();
                            }
                            return currentScope != null;
                        }

                        @Override
                        public Scope next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException();
                            }
                            oldScope = currentScope;
                            currentScope = null;
                            return new Scope(oldScope);
                        }
                    };
                }
            };
        }
    }

}
