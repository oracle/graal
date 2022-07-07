/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
        /*
         * Ensure TruffleRuntime gets initialized before TruffleOptions are set. This allows a
         * specific TruffleRuntime to effect the system properties that are used to determine the
         * values for the TruffleOptions below.
         */
        Truffle.getRuntime();

        class GetOptions implements PrivilegedAction<Void> {
            boolean aot;
            boolean traceRewrites;
            boolean detailedRewriteReasons;
            String traceRewritesFilterClass;
            NodeCost traceRewritesFilterFromCost;
            NodeCost traceRewritesFilterToCost;

            @Override
            public Void run() {
                aot = Boolean.getBoolean("com.oracle.graalvm.isaot");
                traceRewrites = Boolean.getBoolean("truffle.TraceRewrites");
                detailedRewriteReasons = Boolean.getBoolean("truffle.DetailedRewriteReasons");
                traceRewritesFilterClass = System.getProperty("truffle.TraceRewritesFilterClass");
                traceRewritesFilterFromCost = parseNodeInfoKind(System.getProperty("truffle.TraceRewritesFilterFromCost"));
                traceRewritesFilterToCost = parseNodeInfoKind(System.getProperty("truffle.TraceRewritesFilterToCost"));
                return null;
            }
        }

        GetOptions options = new GetOptions();
        AccessController.doPrivileged(options);
        TraceRewrites = options.traceRewrites;
        DetailedRewriteReasons = options.detailedRewriteReasons;
        AOT = options.aot;
        TraceRewritesFilterClass = options.traceRewritesFilterClass;
        TraceRewritesFilterFromCost = options.traceRewritesFilterFromCost;
        TraceRewritesFilterToCost = options.traceRewritesFilterToCost;
    }
}
