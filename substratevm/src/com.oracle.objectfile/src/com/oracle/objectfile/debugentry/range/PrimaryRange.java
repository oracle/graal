/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debugentry.range;

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;

public class PrimaryRange extends Range {
    private static final DebugInfoProvider.DebugLocalInfo[] EMPTY_LOCAL_INFOS = new DebugInfoProvider.DebugLocalInfo[0];
    /**
     * The first subrange in the range covered by this primary or null if this primary as no
     * subranges.
     */
    protected SubRange firstCallee;
    /**
     * The last subrange in the range covered by this primary.
     */
    protected SubRange lastCallee;
    /**
     * Values for the associated method's local and parameter variables that are available or,
     * alternatively, marked as invalid in this range.
     */
    private DebugInfoProvider.DebugLocalValueInfo[] localValueInfos;
    /**
     * The set of local or parameter variables with which each corresponding local value in field
     * localValueInfos is associated. Local values which are associated with the same local or
     * parameter variable will share the same reference in the corresponding array entries. Local
     * values with which no local variable can be associated will have a null reference in the
     * corresponding array. The latter case can happen when a local value has an invalid slot or
     * when a local value that maps to a parameter slot has a different name or type to the
     * parameter.
     */
    private DebugInfoProvider.DebugLocalInfo[] localInfos;

    protected PrimaryRange(MethodEntry methodEntry, int lo, int hi, int line) {
        super(methodEntry, lo, hi, line, -1);
        this.firstCallee = null;
        this.lastCallee = null;
    }

    @Override
    public int getFileIndex() {
        ClassEntry owner = methodEntry.ownerType();
        return owner.localFilesIdx(getFileEntry());
    }

    @Override
    public boolean isPrimary() {
        return true;
    }

    @Override
    protected void addCallee(SubRange callee) {
        assert this.lo <= callee.lo;
        assert this.hi >= callee.hi;
        assert callee.caller == this;
        assert callee.siblingCallee == null;
        if (this.firstCallee == null) {
            assert this.lastCallee == null;
            this.firstCallee = this.lastCallee = callee;
        } else {
            this.lastCallee.siblingCallee = callee;
            this.lastCallee = callee;
        }
    }

    @Override
    public SubRange getFirstCallee() {
        return firstCallee;
    }

    @Override
    public boolean isLeaf() {
        return firstCallee == null;
    }

    @Override
    public boolean includesInlineRanges() {
        SubRange child = firstCallee;
        while (child != null && child.isLeaf()) {
            child = child.siblingCallee;
        }
        return child != null;
    }

    public int getLocalValueCount() {
        assert !this.isPrimary() : "primary range does not have local values";
        return localValueInfos.length;
    }

    public DebugInfoProvider.DebugLocalValueInfo getLocalValue(int i) {
        assert !this.isPrimary() : "primary range does not have local values";
        assert i >= 0 && i < localValueInfos.length : "bad index";
        return localValueInfos[i];
    }

    public DebugInfoProvider.DebugLocalInfo getLocal(int i) {
        assert !this.isPrimary() : "primary range does not have local vars";
        assert i >= 0 && i < localInfos.length : "bad index";
        return localInfos[i];
    }

    public void setLocalValueInfo(DebugInfoProvider.DebugLocalValueInfo[] localValueInfos) {
        int len = localValueInfos.length;
        this.localValueInfos = localValueInfos;
        this.localInfos = (len > 0 ? new DebugInfoProvider.DebugLocalInfo[len] : EMPTY_LOCAL_INFOS);
        // set up mapping from local values to local variables
        for (int i = 0; i < len; i++) {
            localInfos[i] = methodEntry.recordLocal(localValueInfos[i]);
        }
    }

    public DebugInfoProvider.DebugLocalValueInfo lookupValue(DebugInfoProvider.DebugLocalInfo local) {
        int localValueCount = getLocalValueCount();
        for (int i = 0; i < localValueCount; i++) {
            if (getLocal(i) == local) {
                return getLocalValue(i);
            }
        }
        return null;
    }
}
