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

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.CallTarget;

/**
 * Set of standard tags usable by language agnostic tools. Language should {@link ProvidedTags
 * provide} an implementation of these tags in order to support a wide variety of tools.
 *
 * @since 0.12
 */
public final class StandardTags {

    private StandardTags() {
        /* No instances */
    }

    @SuppressWarnings({"rawtypes"}) static final Class[] ALL_TAGS = new Class[]{StatementTag.class, CallTag.class, RootTag.class, ExpressionTag.class, DeclarationTag.class};

    /**
     * Marks program locations that represent a statement of a language.
     * <p>
     * Use case descriptions:
     * <ul>
     * <li><b>Debugger:</b> Marks program locations where ordinary stepping should halt. The
     * debugger will halt just <em>before</em> a code location is executed that is marked with this
     * tag.
     * <p>
     * In most languages, this means statements are distinct from expressions and only one node
     * representing the statement should be tagged. Subexpressions are typically not tagged so that
     * for example a step-over operation will stop at the next independent statement to get the
     * desired behavior.</li>
     * </ul>
     * The StatemenTag uses the {@link Tag.Identifier identifier} <code>"STATEMENT"</code>. A node
     * tagged with {@link RootTag} must provide a {@link Node#getSourceSection() source section}, if
     * its root node provides a source section.
     * <p>
     * If the a node tagged with {@link StatementTag statement} returns a non <code>null</code>
     * value then it must be an interop value. There are assertions in place verifying this when
     * Java assertions are enabled (-ea).
     *
     * @since 0.12
     */
    @Tag.Identifier("STATEMENT")
    public static final class StatementTag extends Tag {
        private StatementTag() {
            /* No instances */
        }
    }

    /**
     * Marks program locations that represent a call to other guest language functions, methods or
     * closures.
     * <p>
     * Use case descriptions:
     * <ul>
     * <li><b>Debugger:</b> Marks program locations where <em>returning</em> or <em>stepping
     * out</em> from a method/procedure/closure call should halt. The debugger will halt at the code
     * location that has just executed the call that returned.</li>
     * </ul>
     *
     * The CallTag uses the {@link Tag.Identifier identifier} <code>"CALL"</code>. A node tagged
     * with {@link RootTag} must provide a {@link Node#getSourceSection() source section}, if its
     * root node provides a source section.
     * <p>
     * If the a node tagged with {@link CallTarget call} returns a non <code>null</code> value then
     * it must be an interop value. There are assertions in place verifying this when Java
     * assertions are enabled (-ea).
     *
     * @since 0.12
     */
    @Tag.Identifier("CALL")
    public static final class CallTag extends Tag {
        private CallTag() {
            /* No instances */
        }
    }

    /**
     * Marks program locations as root of a function, method or closure. The root prolog should be
     * executed by this node. In particular, when the implementation copies
     * {@link Frame#getArguments()} into {@link FrameSlot}s, it should do it here for the
     * instrumentation to work correctly.
     * <p>
     * Use case descriptions:
     * <ul>
     * <li><b>Profiler:</b> Marks every root that should be profiled.</li>
     * </ul>
     *
     * The RootTag uses the {@link Tag.Identifier identifier} <code>"ROOT"</code>. A node tagged
     * with {@link RootTag} must provide a {@link Node#getSourceSection() source section}, if its
     * root node provides a source section.
     * <p>
     * If the a node tagged with {@link RootTag root} returns a non <code>null</code> value then it
     * must be an interop value. There are assertions in place verifying this when Java assertions
     * are enabled (-ea).
     *
     * @since 0.12
     */
    @Tag.Identifier("ROOT")
    public static final class RootTag extends Tag {
        private RootTag() {
            /* No instances */
        }
    }

    /**
     * Marks program locations as to be considered expressions of the languages. Common examples for
     * expressions are:
     * <ul>
     * <li>Literal expressions
     * <li>Arithmetic expressions like addition and multiplication
     * <li>Condition expressions
     * <li>Function calls
     * <li>Array, Object or variable reads and writes
     * <li>Instantiations
     * </ul>
     * Use case descriptions:
     * <ul>
     * <li><b>Coverage:</b> To compute expression coverage.</li>
     * <li><b>Debugger:</b> Fine grained debugging of expressions. It is optional to implement the
     * expression tag to support debuggers.</li>
     * </ul>
     *
     * The ExpressionTag uses the {@link Tag.Identifier identifier} <code>"EXPRESSION"</code>. A
     * node tagged with {@link RootTag} must provide a {@link Node#getSourceSection() source
     * section}, if its root node provides a source section. *
     * <p>
     * If the a node tagged with {@link RootTag root} returns a non <code>null</code> value then it
     * must be an interop value. There are assertions in place verifying this when Java assertions
     * are enabled (-ea).
     *
     * @since 0.33
     */
    @Tag.Identifier("EXPRESSION")
    public static final class ExpressionTag extends Tag {

        private ExpressionTag() {
            /* No instances */
        }

    }

    /**
     * Marks program locations to be considered as try blocks, that are followed by catch. To
     * determine which exceptions are caught by {@link InstrumentableNode} tagged with this tag, the
     * node might provide a {@link InstrumentableNode#getNodeObject() node object} that has
     * <code>catches</code> function, which takes a {@link TruffleException#getExceptionObject()}
     * and returns a boolean return value indicating whether the try block catches the exception, or
     * not. When this block catches all exceptions, no special node object or catches function needs
     * to be provided.
     *
     * @since 1.0
     */
    @Tag.Identifier("TRY_BLOCK")
    public static final class TryBlockTag extends Tag {

        /**
         * Name of the <code>catches</code> function.
         *
         * @since 1.0
         */
        public static final String CATCHES = "catches";

        private TryBlockTag() {
            /* No instances */
        }

    }

    @Tag.Identifier("DECLARATION")
    public static final class DeclarationTag extends Tag {

        public static final String NAME = "name";
        public static final String KIND = "kind";
        public static final String CONTAINER = "container";

        public enum Kind {
            File(1),
            Module(2),
            Namespace(3),
            Package(4),
            Class(5),
            Method(6),
            Property(7),
            Field(8),
            Constructor(9),
            Enum(10),
            Interface(11),
            Function(12),
            Variable(13),
            Constant(14),
            String(15),
            Number(16),
            Boolean(17),
            Array(18),
            Object(19),
            Key(20),
            Null(21),
            EnumMember(22),
            Struct(23),
            Event(24),
            Operator(25),
            TypeParameter(26);

            public final int value;

            Kind(int value) {
                this.value = value;
            }

            public final int getValue() {
                return value;
            }

            public static Kind forValue(int value) {
                Kind[] allValues = Kind.values();
                if (value < 1 || value > allValues.length)
                    throw new IllegalArgumentException("Illegal enum value: " + value);
                return allValues[value - 1];
            }
        }

        private DeclarationTag() {
            /* No instances */
        }

    }
}
