/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeInfo.Kind;

/**
 * Class containing general Truffle options.
 */
public class TruffleOptions {

    /** Enables/disables the rewriting of traces in the truffle runtime to stdout. */
    public static boolean TraceRewrites = false;

    /**
     * Filters rewrites that do not contain the given string in the qualified name of the source or
     * target class hierarchy.
     */
    public static String TraceRewritesFilterClass = null;

    /**
     * Filters rewrites which does not contain the {@link Kind} in its source {@link NodeInfo}. If
     * no {@link NodeInfo} is defined the element is filtered if the filter value is set.
     */
    public static NodeInfo.Kind TraceRewritesFilterFromKind = null;

    /**
     * Filters rewrites which does not contain the {@link Kind} in its target {@link NodeInfo}. If
     * no {@link NodeInfo} is defined the element is filtered if the filter value is set.
     */
    public static NodeInfo.Kind TraceRewritesFilterToKind = null;

}
