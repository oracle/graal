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

package com.oracle.objectfile.debugentry.range;

import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalValueInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Details of a specific address range in a compiled method either a primary range identifying a
 * whole compiled method or a sub-range identifying a sub-sequence of the compiled instructions that
 * may derive from top level or inlined code. Each sub-range is linked with its caller, (which may
 * be the primary range) and its callees, forming a call tree. Subranges are either leaf nodes with
 * no children or call nodes which have children.
 *
 * <ul>
 * <li>A leaf node at the top level (depth 0) records the start and extent of a sequence of compiled
 * code derived from the top level method. The leaf node reports itself as belonging to the top
 * level method.
 * <li>A leaf node at depth N records the start and extent of a sequence of compiled code derived
 * from a leaf inlined method at a call depth of N. The leaf node reports itself as belonging to the
 * leaf method.
 * <li>A call node at level 0 records the start and extent of a sequence of compiled code that
 * includes all compiled code derived from a top level call that has been inlined. All child nodes
 * of the call node (direct or indirect) should model ranges that lie within the parent range. The
 * call node reports itself as belonging to the top level method and its file and line information
 * identify the location of the call.
 * <li>A call node at level N records the start and extent of a sequence of compiled code that
 * includes all compiled code derived from an inline call at depth N. All child nodes of the call
 * node (direct or indirect) should model ranges that lie within the parent range. The call node
 * reports itself as belonging to the caller method at depth N and its file and line information
 * identify the location of the call.
 * <ul>
 *
 * Ranges also record the location of local and parameter values that are valid for the range's
 * extent. Each value maps to a corresponding parameter or local variable attached to the range's
 * method. So, a leaf or call node at level 0 records local and parameter values for separate
 * sub-extents of the top level method while a leaf or call node at level N+1 records local and
 * parameter values for separate sub-extents of an inline called method whose full extent is
 * represented by the parent call range at level N.
 */
public abstract class Range {
    private static final String CLASS_DELIMITER = ".";
    protected final MethodEntry methodEntry;
    protected final int lo;
    protected int hi;
    protected final int line;
    protected final int depth;

    /**
     * Create a primary range representing the root of the subrange tree for a top level compiled
     * method.
     * 
     * @param methodEntry the top level compiled method for this primary range.
     * @param lo the lowest address included in the range.
     * @param hi the first address above the highest address in the range.
     * @param line the line number associated with the range
     * @return a new primary range to serve as the root of the subrange tree.
     */
    public static PrimaryRange createPrimary(MethodEntry methodEntry, int lo, int hi, int line) {
        return new PrimaryRange(methodEntry, lo, hi, line);
    }

    /**
     * Create a subrange representing a segment of the address range for code of a top level or
     * inlined compiled method. The result will either be a call or a leaf range.
     * 
     * @param methodEntry the method from which code in the subrange is derived.
     * @param lo the lowest address included in the range.
     * @param hi the first address above the highest address in the range.
     * @param line the line number associated with the range
     * @param primary the primary range to which this subrange belongs
     * @param caller the range for which this is a subrange, either an inlined call range or the
     *            primary range.
     * @param isLeaf true if this is a leaf range with no subranges
     * @return a new subrange to be linked into the range tree below the primary range.
     */
    public static SubRange createSubrange(MethodEntry methodEntry, int lo, int hi, int line, PrimaryRange primary, Range caller, boolean isLeaf) {
        assert primary != null;
        assert primary.isPrimary();
        if (isLeaf) {
            return new LeafRange(methodEntry, lo, hi, line, primary, caller);
        } else {
            return new CallRange(methodEntry, lo, hi, line, primary, caller);
        }
    }

    protected Range(MethodEntry methodEntry, int lo, int hi, int line, int depth) {
        assert methodEntry != null;
        this.methodEntry = methodEntry;
        this.lo = lo;
        this.hi = hi;
        this.line = line;
        this.depth = depth;
    }

    protected abstract void addCallee(SubRange callee);

    public boolean contains(Range other) {
        return (lo <= other.lo && hi >= other.hi);
    }

    public abstract boolean isPrimary();

    public String getClassName() {
        return methodEntry.ownerType().getTypeName();
    }

    public String getMethodName() {
        return methodEntry.methodName();
    }

    public String getSymbolName() {
        return methodEntry.getSymbolName();
    }

    public int getHi() {
        return hi;
    }

    public int getLo() {
        return lo;
    }

    public int getLine() {
        return line;
    }

    public String getFullMethodName() {
        return constructClassAndMethodName();
    }

    public String getFullMethodNameWithParams() {
        return constructClassAndMethodNameWithParams();
    }

    public boolean isDeoptTarget() {
        return methodEntry.isDeopt();
    }

    private String getExtendedMethodName(boolean includeClass, boolean includeParams, boolean includeReturnType) {
        StringBuilder builder = new StringBuilder();
        if (includeReturnType && methodEntry.getValueType().getTypeName().length() > 0) {
            builder.append(methodEntry.getValueType().getTypeName());
            builder.append(' ');
        }
        if (includeClass && getClassName() != null) {
            builder.append(getClassName());
            builder.append(CLASS_DELIMITER);
        }
        builder.append(getMethodName());
        if (includeParams) {
            builder.append("(");
            TypeEntry[] paramTypes = methodEntry.getParamTypes();
            if (paramTypes != null) {
                String prefix = "";
                for (TypeEntry t : paramTypes) {
                    builder.append(prefix);
                    builder.append(t.getTypeName());
                    prefix = ", ";
                }
            }
            builder.append(')');
        }
        if (includeReturnType) {
            builder.append(" ");
            builder.append(methodEntry.getValueType().getTypeName());
        }
        return builder.toString();
    }

    private String constructClassAndMethodName() {
        return getExtendedMethodName(true, false, false);
    }

    private String constructClassAndMethodNameWithParams() {
        return getExtendedMethodName(true, true, false);
    }

    public FileEntry getFileEntry() {
        return methodEntry.getFileEntry();
    }

    public int getModifiers() {
        return methodEntry.getModifiers();
    }

    @Override
    public String toString() {
        return String.format("Range(lo=0x%05x hi=0x%05x %s %s:%d)", lo, hi, constructClassAndMethodNameWithParams(), methodEntry.getFullFileName(), line);
    }

    public String getFileName() {
        return methodEntry.getFileName();
    }

    public MethodEntry getMethodEntry() {
        return methodEntry;
    }

    public abstract SubRange getFirstCallee();

    public abstract boolean isLeaf();

    public boolean includesInlineRanges() {
        SubRange child = getFirstCallee();
        while (child != null && child.isLeaf()) {
            child = child.getSiblingCallee();
        }
        return child != null;
    }

    public int getDepth() {
        return depth;
    }

    public HashMap<DebugLocalInfo, List<SubRange>> getVarRangeMap() {
        MethodEntry calleeMethod;
        if (isPrimary()) {
            calleeMethod = getMethodEntry();
        } else {
            assert !isLeaf() : "should only be looking up var ranges for inlined calls";
            calleeMethod = getFirstCallee().getMethodEntry();
        }
        HashMap<DebugLocalInfo, List<SubRange>> varRangeMap = new HashMap<>();
        if (calleeMethod.getThisParam() != null) {
            varRangeMap.put(calleeMethod.getThisParam(), new ArrayList<>());
        }
        for (int i = 0; i < calleeMethod.getParamCount(); i++) {
            varRangeMap.put(calleeMethod.getParam(i), new ArrayList<>());
        }
        for (int i = 0; i < calleeMethod.getLocalCount(); i++) {
            varRangeMap.put(calleeMethod.getLocal(i), new ArrayList<>());
        }
        return updateVarRangeMap(varRangeMap);
    }

    public HashMap<DebugLocalInfo, List<SubRange>> updateVarRangeMap(HashMap<DebugLocalInfo, List<SubRange>> varRangeMap) {
        // leaf subranges of the current range may provide values for param or local vars
        // of this range's method. find them and index the range so that we can identify
        // both the local/param and the associated range.
        SubRange subRange = this.getFirstCallee();
        while (subRange != null) {
            addVarRanges(subRange, varRangeMap);
            subRange = subRange.siblingCallee;
        }
        return varRangeMap;
    }

    public void addVarRanges(SubRange subRange, HashMap<DebugLocalInfo, List<SubRange>> varRangeMap) {
        int localValueCount = subRange.getLocalValueCount();
        for (int i = 0; i < localValueCount; i++) {
            DebugLocalValueInfo localValueInfo = subRange.getLocalValue(i);
            DebugLocalInfo local = subRange.getLocal(i);
            if (local != null) {
                switch (localValueInfo.localKind()) {
                    case REGISTER:
                    case STACKSLOT:
                    case CONSTANT:
                        List<SubRange> varRanges = varRangeMap.get(local);
                        assert varRanges != null : "local not present in var to ranges map!";
                        varRanges.add(subRange);
                        break;
                    case UNDEFINED:
                        break;
                }
            }
        }
    }

}
