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
     *
     * @since 0.12
     */
    public final class StatementTag {
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
     * @since 0.12
     */
    public final class CallTag {
        private CallTag() {
            /* No instances */
        }
    }

    /**
     * Marks program locations as root of a function, method or closure. The root prolog should be
     * executed before and the epilog after the source location marked as root is executed.
     * <p>
     * Use case descriptions:
     * <ul>
     * <li><b>Profiler:</b> Marks every root that should be profiled.</li>
     * </ul>
     *
     * @since 0.12
     */
    public final class RootTag {
        private RootTag() {
            /* No instances */
        }
    }

}
