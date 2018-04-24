/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.security.AccessController;
import java.security.PrivilegedAction;

import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * Class containing general Truffle options.
 *
 * @since 0.8 or earlier
 */
public final class TruffleOptions {
    private TruffleOptions() {
    }

    /**
     * Enables/disables the rewriting of traces in the Truffle runtime to stdout.
     * <p>
     * Can be set with {@code -Dtruffle.TraceRewrites=true}.
     *
     * @since 0.8 or earlier
     */
    public static final boolean TraceRewrites;

    /**
     * Enables the generation of detailed rewrite reasons. Enabling this may introduce some overhead
     * for rewriting nodes.
     * <p>
     * Can be set with {@code -Dtruffle.DetailedRewriteReasons=true}.
     *
     * @since 0.8 or earlier
     */
    public static final boolean DetailedRewriteReasons;

    /**
     * Filters rewrites that do not contain the given string in the qualified name of the source or
     * target class hierarchy.
     * <p>
     * Can be set with {@code -Dtruffle.TraceRewritesFilterClass=name}.
     *
     * @since 0.8 or earlier
     */
    public static final String TraceRewritesFilterClass;

    /**
     * Filters rewrites which does not contain the {@link NodeCost} in its source {@link NodeInfo}.
     * If no {@link NodeInfo} is defined the element is filtered if the filter value is set.
     * <p>
     * Can be set with
     * {@code -Dtruffle.TraceRewritesFilterFromCost=NONE|MONOMORPHIC|POLYMORPHIC|MEGAMORPHIC}.
     *
     * @since 0.8 or earlier
     */
    public static final NodeCost TraceRewritesFilterFromCost;

    /**
     * Filters rewrites which does not contain the {@link NodeCost} in its target {@link NodeInfo}.
     * If no {@link NodeInfo} is defined the element is filtered if the filter value is set.
     * <p>
     * Can be set with
     * {@code -Dtruffle.TraceRewritesFilterToKind=UNINITIALIZED|SPECIALIZED|POLYMORPHIC|GENERIC}.
     *
     * @since 0.8 or earlier
     */
    public static final NodeCost TraceRewritesFilterToCost;

    /**
     * Enables the dumping of Node creations and AST rewrites in JSON format.
     * <p>
     * Can be set with {@code -Dtruffle.TraceASTJSON=true}.
     *
     * @since 0.8 or earlier
     * @deprecated to be removed without replacement
     */
    @Deprecated public static final boolean TraceASTJSON;

    /**
     * Forces ahead-of-time initialization.
     *
     * @since 0.8 or earlier
     */
    public static final boolean AOT;

    private static NodeCost parseNodeInfoKind(String kind) {
        if (kind == null) {
            return null;
        }

        return NodeCost.valueOf(kind);
    }

    static {
        final boolean[] values = new boolean[4];
        final Object[] objs = new Object[3];

        /*
         * Ensure TruffleRuntime gets initialized before TruffleOptions are set. This allows a
         * specific TruffleRuntime to effect the system properties that are used to determine the
         * values for the TruffleOptions below.
         */
        Truffle.getRuntime();

        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                values[0] = Boolean.getBoolean("truffle.TraceRewrites");
                objs[0] = System.getProperty("truffle.TraceRewritesFilterClass");
                objs[1] = parseNodeInfoKind(System.getProperty("truffle.TraceRewritesFilterFromCost"));
                objs[2] = parseNodeInfoKind(System.getProperty("truffle.TraceRewritesFilterToCost"));
                values[1] = Boolean.getBoolean("truffle.DetailedRewriteReasons");
                values[2] = Boolean.getBoolean("truffle.TraceASTJSON");
                values[3] = Boolean.getBoolean("com.oracle.truffle.aot");
                return null;
            }
        });
        TraceRewrites = values[0];
        DetailedRewriteReasons = values[1];
        TraceASTJSON = values[2];
        AOT = values[3];
        TraceRewritesFilterClass = (String) objs[0];
        TraceRewritesFilterFromCost = (NodeCost) objs[1];
        TraceRewritesFilterToCost = (NodeCost) objs[2];
    }
}
