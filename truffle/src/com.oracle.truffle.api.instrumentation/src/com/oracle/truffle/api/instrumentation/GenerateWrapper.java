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

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Generates a default wrapper subclass of an annotated {@link InstrumentableNode} subclass. The
 * generated subclass is has the same class name as the original class name plus the 'Wrapper'
 * suffix. The generated class has default package visibility. All non-final and non-private methods
 * starting with execute are overridden by the generated wrapper. The generated overrides notifies
 * execution events as required by {@link ProbeNode probes}. Other abstract methods are directly
 * delegated to the wrapped node. No other methods are overridden by the generated wrapper. At least
 * one method starting with execute must be non-private and non-final. Every execute method must
 * have {@link VirtualFrame} as the first declared parameter.
 * <p>
 * <b>Example Usage:</b>
 *
 * <pre>
 * &#64;GenerateWrapper
 * abstract class ExpressionNode extends Node implements InstrumentableNode {
 *
 *     abstract Object execute(VirtualFrame frame);
 *
 *     &#64;Override
 *     public WrapperNode createWrapper(ProbeNode probeNode) {
 *         return new ExpressionNodeWrapper(this, probeNode);
 *     }
 * }
 * </pre>
 * <p>
 * <b>Example that ignores return values:</b>
 *
 * <pre>
 * &#64;GenerateWrapper
 * abstract class ExpressionNode extends Node implements InstrumentableNode {
 *
 *     abstract Object execute(VirtualFrame frame);
 *
 *     &#64;Override
 *     public WrapperNode createWrapper(ProbeNode probeNode) {
 *         return new ExpressionNodeWrapper(this, probeNode);
 *     }
 *
 *     &#64;GenerateWrapper.OutgoingConverter
 *     final Object convertOutgoing(Object outgoingValue) {
 *         return null;
 *     }
 * }
 * </pre>
 *
 * <p>
 * <b>Example that converts incoming byte values to int.</b>
 *
 * <pre>
 * &#64;GenerateWrapper
 * abstract class ExpressionNode extends Node implements InstrumentableNode {
 *
 *     abstract Object execute(VirtualFrame frame);
 *
 *     &#64;Override
 *     public WrapperNode createWrapper(ProbeNode probeNode) {
 *         return new ExpressionNodeWrapper(this, probeNode);
 *     }
 *
 *     &#64;GenerateWrapper.IncomingConverter
 *     final Object convertIncoming(Object incomingValue) {
 *         if (incomingValue instanceof Byte) {
 *             return (int) ((byte) incomingValue);
 *         }
 *         return incomingValue;
 *     }
 * }
 * </pre>
 *
 *
 * @see InstrumentableNode
 * @see ProbeNode
 * @since 0.33
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface GenerateWrapper {

    /**
     * Annotates a method to be used as incoming value converter. The annotated method can be used
     * to convert incoming values that were introduced by instruments. Instruments may introduce new
     * values to the interpreter using the {@link EventContext#createUnwind(Object) unwind} feature.
     * Introduced values are Interop values. If the language only supports a subset of interop
     * values or requires them to be wrapped then the incoming converter can be used to perform the
     * necessary conversion. The incoming converter is only invoked if a wrapper is currently
     * inserted.
     * <p>
     * The return type and the single parameter of the annotated method must be of type
     * {@link Object}. The annotated method must have at least package-protected visibility. There
     * can only be a single method annotated with {@link IncomingConverter} per class annotated with
     * {@link GenerateWrapper}.
     *
     * @see GenerateWrapper for usage examples
     * @since 1.0
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD})
    public @interface IncomingConverter {
    }

    /**
     * Annotates a method to be used as outgoing value converter. The annotated method can be used
     * to convert outgoing return values of instrumentable nodes to instruments. This may be used to
     * convert internal non-interop capable values to interop values before instruments can access
     * it. The outgoing converter can also be used to just return <code>null</code> to indicate that
     * return value should be ignored from this wrapped node. The outgoing converter is only invoked
     * if a wrapper is currently inserted.
     * <p>
     * The annotated method is used to convert an guest language value to a format that can be read
     * by other guest languages and tools. The return type and the single parameter of the annotated
     * method must be of type {@link Object}. There can only be a single method annotated with
     * {@link OutgoingConverter} per class annotated with {@link GenerateWrapper}.
     *
     * @see GenerateWrapper for usage examples
     * @since 1.0
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD})
    public @interface OutgoingConverter {
    }

}
