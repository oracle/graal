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

import com.oracle.truffle.api.source.SourceSection;

/**
 * Coverage data of a particular {@link SourceSection}.
 *
 * @since 19.3.0
 */
public final class SectionCoverage {

    private final SourceSection sourceSection;
    private final boolean covered;
    private final long count;

    SectionCoverage(SourceSection sourceSection, boolean covered, long count) {
        this.sourceSection = sourceSection;
        this.covered = covered;
        this.count = count;
    }

    /**
     * @return The source section this coverage relates to.
     *
     * @since 19.3.0
     */
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    /**
     * @return Was this root covered (i.e. executed) during the tracking execution.
     *
     * @since 19.3.0
     */
    public boolean isCovered() {
        return covered;
    }

    /**
     * This value is only available if the
     * {@link com.oracle.truffle.tools.coverage.CoverageTracker.Config tracker config} specified to
     * count executions.
     *
     * @return How many times was the corresponding {@link SourceSection} executed. -1 if counting
     *         was disabled.
     *
     * @since 19.3.0
     */
    public long getCount() {
        return count;
    }
}
