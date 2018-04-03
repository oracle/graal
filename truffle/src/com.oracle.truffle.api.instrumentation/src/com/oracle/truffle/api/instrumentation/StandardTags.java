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

    @SuppressWarnings({"rawtypes"}) static final Class[] ALL_TAGS = new Class[]{StatementTag.class, CallTag.class, RootTag.class, ExpressionTag.class};

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
}
