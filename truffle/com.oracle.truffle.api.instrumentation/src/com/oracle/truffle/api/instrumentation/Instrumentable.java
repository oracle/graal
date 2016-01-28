/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * <p>
 * Marks a guest language AST node class as instrumentable. An instrumentable node must provide a
 * {@link SourceSection} and a {@link #factory() wrapper factory} to create a wrapper for the
 * instrumentable node. Wrapper factories can be inherited by subclasses.
 * </p>
 * <p>
 * {@link Instrumentable} nodes must extend {@link Node}. The instrumentation framework will
 * {@link Node#replace(Node) replace} the instrumentable node with a {@link WrapperNode} and
 * delegate to the original node. For that at least one method starting with execute must be
 * non-private and non-final.
 * </p>
 * <p>
 * Example for a minimal implementation of an {@link Instrumentable instrumentable} node with a
 * generated wrapper.
 *
 * <pre>
 * &#064;Instrumentable(factory = BaseNodeWrapper.class)
 * public abstract class BaseNode extends Node {
 *     public abstract Object execute(VirtualFrame frame);
 * }
 * </pre>
 *
 * If the instrumentable node requires more than one parameter it the constructor it can either
 * provide a default constructor or it can also provide a copy constructor. For example:
 *
 * <pre>
 * &#064;Instrumentable(factory = BaseNodeWrapper.class)
 * public abstract class BaseNode extends Node {
 *     private final String addtionalData;
 *
 *     public BaseNode(String additonalData) {
 *         this.additionalData = additionalData;
 *     }
 *
 *     public BaseNode(BaseNode delegate) {
 *         this.additionalData = delegate.additionalData;
 *     }
 *
 *     public abstract Object execute(VirtualFrame frame);
 * }
 * </pre>
 *
 * @see WrapperNode
 * @see ProbeNode
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Instrumentable {

    /**
     * Assigns a wrapper factory to a {@link Node node} class annotated as {@link Instrumentable
     * instrumentable}. To use the generated wrapper factory use the is generated with the original
     * class name and the "Wrapper" suffix. So if the class was called <code>StatementNode</code>
     * then the generated factory class will be called <code>StatementNodeWraper</code>.
     *
     * <pre>
     * &#064;Instrumentable(factory = BaseNodeWrapper.class)
     * public abstract class BaseNode extends Node {
     *     public abstract Object execute(VirtualFrame frame);
     * }
     * </pre>
     */
    Class<? extends InstrumentableFactory<? extends Node>> factory();

}
