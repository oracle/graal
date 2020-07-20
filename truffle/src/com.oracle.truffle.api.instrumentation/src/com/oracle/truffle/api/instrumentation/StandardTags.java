/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

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

    @SuppressWarnings({"rawtypes"}) static final Class[] ALL_TAGS = new Class[]{StatementTag.class, CallTag.class, RootTag.class, RootBodyTag.class, ExpressionTag.class, TryBlockTag.class,
                    ReadVariableTag.class, WriteVariableTag.class};

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
     * tagged with {@link StatementTag} must provide a {@link Node#getSourceSection() source
     * section}, if its root node provides a source section.
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
     * with {@link CallTag} must provide a {@link Node#getSourceSection() source section}, if its
     * root node provides a source section.
     * <p>
     * If the a node tagged with {@link CallTag call} returns a non <code>null</code> value then it
     * must be an interop value. There are assertions in place verifying this when Java assertions
     * are enabled (-ea).
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
     * instrumentation to work correctly. As a result, local scope might be incomplete for
     * instruments, as the prolog does not run before this node is entered and epilog may do
     * destructions before this node is exited.
     * <p>
     * Use case descriptions:
     * <ul>
     * <li><b>Debugger:</b> Use this tag to unwind frames.</li>
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
     * Marks program locations as bodies of a function, method or closure. The root prolog and
     * epilog is not a part of this node, what makes a difference from {@link RootTag}. In
     * particular, when the implementation copies {@link Frame#getArguments()} into
     * {@link FrameSlot}s, it should do it before this node for the instrumentation to work
     * correctly.
     * <p>
     * Use case descriptions:
     * <ul>
     * <li><b>Profiler:</b> Marks body of every root that should be profiled and where
     * {@link Scope#getArguments() arguments} and {@link Scope#getReceiver() receiver object} are
     * initialized and ready to be retrieved.</li>
     * </ul>
     *
     * The RootBodyTag uses the {@link Tag.Identifier identifier} <code>"ROOT_BODY"</code>. A node
     * tagged with {@link RootBodyTag} must provide a {@link Node#getSourceSection() source section}
     * , if its root node provides a source section.
     * <p>
     * If the a node tagged with {@link RootBodyTag root body} returns a non <code>null</code> value
     * then it must be an interop value. There are assertions in place verifying this when Java
     * assertions are enabled (-ea).
     *
     * @since 19.2.0
     */
    @Tag.Identifier("ROOT_BODY")
    public static final class RootBodyTag extends Tag {
        private RootBodyTag() {
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
     * node tagged with {@link ExpressionTag} must provide a {@link Node#getSourceSection() source
     * section}, if its root node provides a source section. *
     * <p>
     * If the a node tagged with {@link ExpressionTag expression} returns a non <code>null</code>
     * value then it must be an interop value. There are assertions in place verifying this when
     * Java assertions are enabled (-ea).
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
     * @since 19.0
     */
    @Tag.Identifier("TRY_BLOCK")
    public static final class TryBlockTag extends Tag {

        /**
         * Name of the <code>catches</code> function.
         *
         * @since 19.0
         */
        public static final String CATCHES = "catches";

        private TryBlockTag() {
            /* No instances */
        }

    }

    /**
     * Marks program locations to be considered as reads of variables of the languages.
     * <p>
     * Use case descriptions:
     * <ul>
     * <li><b>Language Server Protocol:</b> Marks every read of a variable to support the
     * <i>documentHighlight</i>, <i>hover</i>, <i>definition</i> and <i>references</i> requests.
     * </li>
     * </ul>
     * To determine the name of the variable, it is required that a node tagged with
     * {@link ReadVariableTag} also provides a {@link InstrumentableNode#getNodeObject() node
     * object} that has {@link ReadVariableTag#NAME} property. The value of that property is either:
     * <ul>
     * <li>a String name of the variable (in that case the node's {@link Node#getSourceSection()
     * source section} is considered as the variable's source section),
     * <li>an object that provides name and {@link SourceSection} via
     * {@link InteropLibrary#asString(Object)} and {@link InteropLibrary#getSourceLocation(Object)}
     * respectively,
     * <li>an array of objects when multiple variables are being read, where each array element
     * provides name and {@link SourceSection} as specified above.
     * </ul>
     * Furthermore, nodes tagged with {@link ReadVariableTag} have to provide a
     * {@link Node#getSourceSection() source section}.
     *
     * @since 20.0.0
     */
    @Tag.Identifier("READ_VARIABLE")
    public static final class ReadVariableTag extends Tag {

        /**
         * Property of the node object that contains name of the variable.
         *
         * @since 20.0.0
         */
        public static final String NAME = "readVariableName";

        private ReadVariableTag() {
            /* No instances */
        }

    }

    /**
     * Marks program locations to be considered as writes of variables of the languages.
     * <p>
     * Use case descriptions:
     * <ul>
     * <li><b>Language Server Protocol:</b> Marks every write of a variable to support the
     * <i>documentHighlight</i>, <i>hover</i>, <i>definition</i> and <i>references</i> requests.
     * </li>
     * </ul>
     * To determine the name of the variable, it is required that a node tagged with
     * {@link WriteVariableTag} also provides a {@link InstrumentableNode#getNodeObject() node
     * object} that has {@link WriteVariableTag#NAME} property. The value of that property is
     * either:
     * <ul>
     * <li>a String name of the variable (in that case the node's {@link Node#getSourceSection()
     * source section} is considered as the variable's source section),
     * <li>an object that provides name and {@link SourceSection} via
     * {@link InteropLibrary#asString(Object)} and {@link InteropLibrary#getSourceLocation(Object)}
     * respectively,
     * <li>an array of objects when multiple variables are being read, where each array element
     * provides name and {@link SourceSection} as specified above.
     * </ul>
     * Furthermore, nodes tagged with {@link WriteVariableTag} have to provide a
     * {@link Node#getSourceSection() source section}.
     *
     * @since 20.0.0
     */
    @Tag.Identifier("WRITE_VARIABLE")
    public static final class WriteVariableTag extends Tag {

        /**
         * Property of the node object that contains name of the variable.
         *
         * @since 20.0.0
         */
        public static final String NAME = "writeVariableName";

        private WriteVariableTag() {
            /* No instances */
        }

    }
}
