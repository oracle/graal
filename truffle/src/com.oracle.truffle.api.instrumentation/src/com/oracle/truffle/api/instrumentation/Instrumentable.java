/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
