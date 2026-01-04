/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debugentry;

import java.util.List;
import java.util.stream.Stream;

import com.oracle.objectfile.debugentry.range.PrimaryRange;
import com.oracle.objectfile.debugentry.range.Range;

/**
 * Tracks debug info associated with a top level compiled method.
 *
 * @param primary The primary range detailed by this object.
 * @param ownerType Details of the class owning this range.
 * @param frameSizeInfos Details of compiled method frame size changes.
 * @param frameSize Size of compiled method frame.
 */
public record CompiledMethodEntry(PrimaryRange primary, List<FrameSizeChangeEntry> frameSizeInfos, int frameSize,
                ClassEntry ownerType) {

    /**
     * Returns a stream that traverses all the callees of the method associated with this entry. The
     * stream performs a depth-first pre-order traversal of the call tree.
     *
     * @return the stream of all ranges
     */
    public Stream<Range> topDownRangeStream(boolean includePrimary) {
        // skip the root of the range stream which is the primary range
        return primary.rangeStream().skip(includePrimary ? 0 : 1);
    }

    /**
     * Returns a stream that traverses the callees of the method associated with this entry and
     * returns only the leafs. The stream performs a depth-first pre-order traversal of the call
     * tree returning only ranges with no callees.
     *
     * @return the stream of leaf ranges
     */
    public Stream<Range> leafRangeStream() {
        return topDownRangeStream(false).filter(Range::isLeaf);
    }

    /**
     * Returns a stream that traverses the callees of the method associated with this entry and
     * returns only the call ranges. The stream performs a depth-first pre-order traversal of the
     * call tree returning only ranges with callees.
     *
     * @return the stream of call ranges
     */
    public Stream<Range> callRangeStream() {
        return topDownRangeStream(false).filter(range -> !range.isLeaf());
    }

    public void seal() {
        // Seal the primary range. Also seals all subranges recursively.
        primary.seal();
    }
}
