/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Tracks debug info associated with a primary method. i.e. a top level compiled method
 */
public class PrimaryEntry {
    /**
     * The primary range detailed by this object.
     */
    private Range primary;
    /**
     * Details of the class owning this range.
     */
    private ClassEntry classEntry;
    /**
     * A list of subranges associated with the primary range.
     */
    private List<Range> subranges;
    /**
     * A mapping from subranges to their associated file entry.
     */
    private HashMap<Range, FileEntry> subrangeIndex;
    /**
     * Details of of compiled method frame size changes.
     */
    private List<DebugFrameSizeChange> frameSizeInfos;
    /**
     * Size of compiled method frame.
     */
    private int frameSize;

    public PrimaryEntry(Range primary, List<DebugFrameSizeChange> frameSizeInfos, int frameSize, ClassEntry classEntry) {
        this.primary = primary;
        this.classEntry = classEntry;
        this.subranges = new LinkedList<>();
        this.subrangeIndex = new HashMap<>();
        this.frameSizeInfos = frameSizeInfos;
        this.frameSize = frameSize;
    }

    public void addSubRange(Range subrange, FileEntry subFileEntry) {
        /*
         * We should not see a subrange more than once.
         */
        assert !subranges.contains(subrange);
        assert subrangeIndex.get(subrange) == null;
        /*
         * We need to generate a file table entry for all ranges.
         */
        subranges.add(subrange);
        subrangeIndex.put(subrange, subFileEntry);
    }

    public Range getPrimary() {
        return primary;
    }

    public ClassEntry getClassEntry() {
        return classEntry;
    }

    public List<Range> getSubranges() {
        return subranges;
    }

    public FileEntry getSubrangeFileEntry(Range subrange) {
        return subrangeIndex.get(subrange);
    }

    public List<DebugFrameSizeChange> getFrameSizeInfos() {
        return frameSizeInfos;
    }

    public int getFrameSize() {
        return frameSize;
    }
}
