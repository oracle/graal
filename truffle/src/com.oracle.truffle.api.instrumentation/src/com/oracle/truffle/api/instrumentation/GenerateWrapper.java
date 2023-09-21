/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Generates a default wrapper subclass of an annotated {@link InstrumentableNode} subclass. The
 * generated subclass has the same class name as the original class name plus the 'Wrapper' suffix.
 * The generated class has default package visibility. All non-final and non-private methods
 * starting with execute or {@link #resumeMethodPrefix() resume prefix (when specified)} are
 * overridden by the generated wrapper. The generated overrides notifies execution events as
 * required by {@link ProbeNode probes}. Other abstract methods are directly delegated to the
 * wrapped node. No other methods are overridden by the generated wrapper. At least one method
 * starting with execute must be non-private and non-final. Every execute method must have
 * {@link VirtualFrame} as the first declared parameter.
 * <p>
 * Use methods with <code>execute</code> prefix for ordinary guest code execution. If the guest
 * language supports yield, add {@link #yieldExceptions()} argument and specify
 * {@link #resumeMethodPrefix() resume prefix} to mark method(s) that resume the yielded execution.
 * To successfully match the yielded/resumed execution, override
 * {@link RootNode#isSameFrame(Frame, Frame)} in case the resumed frame is not an identical instance
 * to the yielded frame.
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
 * <b>Example that converts incoming byte values to int:</b>
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
 * <p>
 * <b>Example that prevents instrumentation from being added to a method:</b>
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
 *     &#64;GenerateWrapper.Ignore
 *     abstract Object executeWithoutInstrumentation(VirtualFrame frame);
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
     * Exceptions that are thrown to yield the execution of a thread. They must implement
     * {@link YieldException} interface. When one of them is thrown,
     * {@link ProbeNode#onYield(VirtualFrame, Object) onYield()} is called automatically. To
     * indicate resume of the execution, provide {@link #resumeMethodPrefix()}.
     *
     * @since 24.0
     */
    Class<?>[] yieldExceptions() default {};

    /**
     * Prefix of methods that resume execution after {@link ProbeNode#onYield(VirtualFrame, Object)
     * yield}. The prefix is empty by default, which means no resume methods. When non-empty, the
     * generated wrapper calls {@link ProbeNode#onResume(VirtualFrame)} instead of
     * {@link ProbeNode#onEnter(VirtualFrame)}.
     *
     * @since 24.0
     */
    String resumeMethodPrefix() default "";

    /**
     * Annotates a method to be used as incoming value converter. The annotated method can be used
     * to convert incoming values that were introduced by instruments. Instruments may introduce new
     * values to the interpreter using the {@link EventContext#createUnwind(Object) unwind} feature.
     * Introduced values are interop values. If the language only supports a subset of interop
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
     * @since 19.0
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
     * @since 19.0
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD})
    public @interface OutgoingConverter {
    }

    /**
     * Annotates a method which should not be instrumented in the generated wrapper subclass. The
     * generated wrapper can still delegate to this method if it is abstract and can be overridden.
     *
     * @see GenerateWrapper for usage examples
     * @since 21.3
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD})
    public @interface Ignore {
    }

    /**
     * An interface to be implemented by {@link #yieldExceptions() yield exceptions}. When a yield
     * exception is thrown from an {@link InstrumentableNode} wrapped by {@link GenerateWrapper},
     * {@link ProbeNode#onYield(VirtualFrame, Object) onYield()} is called automatically.
     *
     * @since 24.0
     */
    public static interface YieldException {

        /**
         * Get the value provided by the yield.
         *
         * @since 24.0
         */
        Object getYieldValue();
    }
}
