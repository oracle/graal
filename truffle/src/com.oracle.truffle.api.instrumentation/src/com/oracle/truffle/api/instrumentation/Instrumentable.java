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

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * <p>
 * Marks a guest language AST node class as <em>instrumentable</em>: an AST location where
 * {@linkplain com.oracle.truffle.api.instrumentation.TruffleInstrument Truffle instruments} are
 * permitted to listen to before and after execution events. An instrumentable node must provide a
 * {@link SourceSection} and a {@link #factory() wrapper factory} to create a wrapper for the
 * instrumentable node. Wrapper factories can be inherited by subclasses.
 * </p>
 * <p>
 * {@link Instrumentable} nodes must extend {@link Node}. The instrumentation framework will, when
 * needed during execution, {@link Node#replace(Node) replace} the instrumentable node with a
 * {@link com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode} and delegate to
 * the original node. After the replacement of an instrumentable node with a wrapper we refer to the
 * original node as an instrumented node.
 * </p>
 * <p>
 * Wrappers can be generated automatically using an annotation processor. For that a class literal
 * named {WrappedNode}Wrapper must be used. If the referenced class was not found on the class path
 * it will get generated. If the automatically generated wrapper factory and wrapper classes are not
 * suitable for the needs of the guest language then {@link InstrumentableFactory} can also be
 * implemented manually and referenced in {@link #factory()} using a class literal.
 * </p>
 * <p>
 * Example for a minimal implementation of an {@link Instrumentable instrumentable} node with a
 * generated wrapper. For that at least one method starting with execute must be non-private and
 * non-final.
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
 * @see com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode
 * @see ProbeNode
 * @since 0.12
 * @deprecated use {@link GenerateWrapper} and {@link InstrumentableNode} instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Deprecated
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
     *
     * @since 0.12
     */
    @SuppressWarnings("deprecation")
    Class<? extends InstrumentableFactory<? extends Node>> factory();

}
