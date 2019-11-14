/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.coverage;

import com.oracle.truffle.api.source.Source;

/**
 * Coverage data of a particular {@link Source}. Coverage data is organized per {@link RootCoverage
 * root} (e.g. function, method, etc.) contained in this source.
 * 
 * @since 19.3.0
 */
public final class SourceCoverage {

    final Source source;
    final RootCoverage[] roots;

    SourceCoverage(Source source, RootCoverage[] roots) {
        this.source = source;
        this.roots = roots;
    }

    /**
     * @return The source this coverage relates to.
     *
     * @since 19.3.0
     */
    public Source getSource() {
        return source;
    }

    /**
     * @return Coverage data for the
     *         {@link com.oracle.truffle.api.instrumentation.StandardTags.RootTag roots} in this
     *         {@link Source}
     *
     * @since 19.3.0
     */
    public RootCoverage[] getRoots() {
        return roots;
    }
}
