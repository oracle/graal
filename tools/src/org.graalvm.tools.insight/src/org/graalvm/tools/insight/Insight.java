/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight;

import java.util.function.Function;
import org.graalvm.polyglot.Engine;

/**
 * Programatic access to the Insight instrument. Obtain an instrument instance via its {@link #ID}:
 * <p>
 * {@codesnippet Embedding#apply}
 * 
 * and then {@link Function#apply(java.lang.Object) evaluate} {@link org.graalvm.polyglot.Source}
 * scripts written in any language accessing the {@code agent} variable exposed to them. Use
 * {@link #VERSION following API} when dealing with the {@code insight} variable:
 * <p>
 * {@codesnippet InsightAPI}
 * 
 * @since 20.1
 */
public final class Insight {
    private Insight() {
    }

    /**
     * The ID of the agent script instrument is {@code "insight"}. Use it to obtain access to an
     * {@link Insight} instruments inside of your {@link Engine}:
     * <p>
     * {@codesnippet Embedding#apply}
     * 
     * @since 20.1
     */
    public static final String ID = "insight";

    /**
     * Version of the Insight instrument. The current version understands following Java-like
     * polyglot <em>API</em> made available to the GraalVM Insight scripts via {@code insight}
     * reference:
     * <p>
     * {@codesnippet InsightAPI}
     * 
     * @since 20.1
     */
    public static final String VERSION = "0.6";

}
