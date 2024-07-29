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

package com.oracle.objectfile.runtime.debugentry.range;

import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider;
import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugLocalValueInfo;
import com.oracle.objectfile.runtime.debugentry.MethodEntry;

import java.util.ArrayList;
import java.util.List;

public abstract class SubRange extends Range {
    /**
     * The root of the call tree the subrange belongs to.
     */
    private final PrimaryRange primary;
    /**
     * The range for the caller or the primary range when this range is for top level method code.
     */
    protected Range caller;
    /**
     * A link to a sibling callee, i.e., a range sharing the same caller with this range.
     */
    protected SubRange siblingCallee;
    /**
     * Values for the associated method's local and parameter variables that are available or,
     * alternatively, marked as invalid in this range.
     */
    private List<DebugLocalValueInfo> localValueInfos;
    /**
     * The set of local or parameter variables with which each corresponding local value in field
     * localValueInfos is associated. Local values which are associated with the same local or
     * parameter variable will share the same reference in the corresponding array entries. Local
     * values with which no local variable can be associated will have a null reference in the
     * corresponding array. The latter case can happen when a local value has an invalid slot or
     * when a local value that maps to a parameter slot has a different name or type to the
     * parameter.
     */
    private List<DebugLocalInfo> localInfos;

    @SuppressWarnings("this-escape")
    protected SubRange(MethodEntry methodEntry, long lo, long hi, int line, PrimaryRange primary, Range caller) {
        super(methodEntry, lo, hi, line, (caller == null ? 0 : caller.depth + 1));
        this.caller = caller;
        if (caller != null) {
            caller.addCallee(this);
        }
        assert primary != null;
        this.primary = primary;
    }

    public Range getPrimary() {
        return primary;
    }

    @Override
    public boolean isPrimary() {
        return false;
    }

    public Range getCaller() {
        return caller;
    }

    @Override
    public abstract SubRange getFirstCallee();

    @Override
    public abstract boolean isLeaf();

    public int getLocalValueCount() {
        return localValueInfos.size();
    }

    public DebugLocalValueInfo getLocalValue(int i) {
        assert i >= 0 && i < localValueInfos.size() : "bad index";
        return localValueInfos.get(i);
    }

    public DebugLocalInfo getLocal(int i) {
        assert i >= 0 && i < localInfos.size() : "bad index";
        return localInfos.get(i);
    }

    public void setLocalValueInfo(List<DebugLocalValueInfo> localValueInfos) {
        this.localValueInfos = localValueInfos;
        this.localInfos = new ArrayList<>();
        // set up mapping from local values to local variables
        for (DebugLocalValueInfo localValueInfo : localValueInfos) {
            localInfos.add(methodEntry.recordLocal(localValueInfo));
        }
    }

    public DebugLocalValueInfo lookupValue(DebugLocalInfo local) {
        for (int i = 0; i < localValueInfos.size(); i++) {
            if (getLocal(i).equals(local)) {
                return getLocalValue(i);
            }
        }
        return null;
    }

    public SubRange getSiblingCallee() {
        return siblingCallee;
    }
}
