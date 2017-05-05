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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.metadata.ScopeProvider.AbstractScope;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Representation of a scope in a guest language program. The scope is a section of a program that
 * contains a set of declared and valid variables. The scopes can be both lexical and dynamic.
 *
 * @see #findScopes(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env,
 *      com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.frame.Frame)
 * @since 0.26
 */
public final class Scope {

    private static final ScopeAccessor SPI = new ScopeAccessor();

    private final AbstractScope ascope;

    /**
     * Find a list of scopes enclosing the given {@link Node node}. There is at least one scope
     * provided, that corresponds to the enclosing function. The iteration order corresponds with
     * the scope nesting, from the inner-most to the outer-most. The scopes contain variables valid
     * at the provided node.
     * <p>
     * In general, there can be a different list of scopes with different variables and arguments
     * returned for different {@link Frame} instances, as scopes may depend on runtime information.
     * Known lexical scopes are returned when <code>frame</code> argument is <code>null</code>.
     *
     * @param instrumentEnv the instrument environment that is used to
     *            {@link com.oracle.truffle.api.instrumentation.TruffleInstrument.Env#lookup(com.oracle.truffle.api.nodes.LanguageInfo, java.lang.Class)
     *            look-up} the {@link ScopeProvider} service.
     * @param node a node to get the enclosing scopes for. The node needs to be inside a
     *            {@link RootNode} associated with a language.
     * @param frame The current frame the node is in, or <code>null</code> for lexical access when
     *            the program is not running, or is not suspended at the node's location.
     * @return an {@link Iterable} providing list of scopes from the inner-most to the outer-most.
     * @see ScopeProvider#findScope(java.lang.Object, com.oracle.truffle.api.nodes.Node,
     *      com.oracle.truffle.api.frame.Frame)
     * @since 0.26
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Iterable<Scope> findScopes(TruffleInstrument.Env instrumentEnv, Node node, Frame frame) {
        RootNode rootNode = node.getRootNode();
        if (rootNode == null) {
            throw new IllegalArgumentException("The node " + node + " does not have a RootNode.");
        }
        LanguageInfo languageInfo = rootNode.getLanguageInfo();
        if (languageInfo == null) {
            throw new IllegalArgumentException("The root node " + rootNode + " does not have a language associated.");
        }
        ScopeProvider scopeProvider = instrumentEnv.lookup(languageInfo, ScopeProvider.class);
        AbstractScope ascope;
        if (scopeProvider != null) {
            TruffleLanguage.Env env = Access.ENGINE.getEnvForInstrument(languageInfo);
            Object context = Access.LANGS.getContext(env);
            ascope = scopeProvider.findScope(context, node, frame);
        } else {
            ascope = new DefaultScopeVariables(node);
        }
        return ascope.toIterable();
    }

    Scope(AbstractScope ascope) {
        this.ascope = ascope;
    }

    /**
     * Human readable name of this scope. A name description like block, name of a function,
     * closure, script, module, etc.
     *
     * @since 0.26
     */
    public String getName() {
        return ascope.getName();
    }

    /**
     * Get a node representing this scope. Functional scopes return the appropriate {@link RootNode}
     * .
     *
     * @return the node, or <code>null<code> when no node is associated.
     * @since 0.26
     */
    public Node getNode() {
        return ascope.getNode();
    }

    /**
     * Get variables declared in this scope and valid at the {@link Node} passed to
     * {@link #findScopes(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env, com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.frame.Frame)}
     * . In general, there can be different variables returned when different {@link Frame}
     * instances are provided.
     * 
     * @param frame The current frame, or <code>null</code> for lexical access when the program is
     *            not running, or is not suspended at the scope's location. The variables might not
     *            be readable/writable with the <code>null</code> frame.
     * @return A {@link com.oracle.truffle.api.interop.TruffleObject} containing the variables as
     *         its properties, not <code>null</code>.
     * @since 0.26
     */
    public Object getVariables(Frame frame) {
        return ascope.getVariables(frame);
    }

    /**
     * Get arguments of this scope. In general, there can be different arguments returned when
     * different {@link Frame} instances are provided.
     *
     * @param frame The current frame, or <code>null</code> for lexical access when the program is
     *            not running, or is not suspended at the scope's location. The arguments might not
     *            be readable/writable with the <code>null</code> frame.
     * @return A {@link com.oracle.truffle.api.interop.TruffleObject} containing the arguments as
     *         its properties for named arguments, or as its array for unnamed arguments. A
     *         <code>null</code> is returned when this scope does not have a concept of arguments.
     *         An empty TruffleObject is provided when it has a sense to have arguments (e.g.
     *         function scope), but there are none.
     * @since 0.26
     */
    public Object getArguments(Frame frame) {
        return ascope.getArguments(frame);
    }

    static class Access {

        static final Accessor.LanguageSupport LANGS = ScopeAccessor.langs();
        static final Accessor.EngineSupport ENGINE = ScopeAccessor.engine();

    }

    static class ScopeAccessor extends Accessor {

        static LanguageSupport langs() {
            return SPI.languageSupport();
        }

        static EngineSupport engine() {
            return SPI.engineSupport();
        }

    }
}
