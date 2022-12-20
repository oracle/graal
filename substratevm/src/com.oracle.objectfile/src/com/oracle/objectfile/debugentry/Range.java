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
public class Range {
    private static final DebugLocalInfo[] EMPTY_LOCAL_INFOS = new DebugLocalInfo[0];
    private static final String CLASS_DELIMITER = ".";
    private final MethodEntry methodEntry;
    private final String fullMethodName;
    private final int lo;
    private int hi;
    private final int line;
    private final int depth;
    /**
     * This is null for a primary range. For sub ranges it holds the root of the call tree they
     * belong to.
     */
    private final Range primary;

    /**
     * The range for the caller or the primary range when this range if for top level method code.
     */
    private Range caller;
    /**
     * The first direct callee whose range is wholly contained in this range or null if this is a
     * leaf range.
     */
    private Range firstCallee;

    /**
     * The last direct callee whose range is wholly contained in this range.
     */
    private Range lastCallee;

    /**
     * A link to a sibling callee, i.e., a range sharing the same caller with this range.
     */
    private Range siblingCallee;

    /**
     * Values for the associated method's local and parameter variables that are available or,
     * alternatively, marked as invalid in this range.
     */
    private DebugLocalValueInfo[] localValueInfos;

    /**
     * The set of local or parameter variables with which each corresponding local value in field
     * localvalueInfos is associated. Local values which are associated with the same local or
     * parameter variable will share the same reference in the corresponding array entries. Local
     * values with which no local variable can be associated will have a null reference in the
     * corresponding array. The latter case can happen when a local value has an invalid slot or
     * when a local value that maps to a parameter slot has a different name or type to the
     * parameter.
     */
    private DebugLocalInfo[] localInfos;

    public int getLocalValueCount() {
        assert !this.isPrimary() : "primary range does not have local values";
        return localValueInfos.length;
    }

    public DebugLocalValueInfo getLocalValue(int i) {
        assert !this.isPrimary() : "primary range does not have local values";
        assert i >= 0 && i < localValueInfos.length : "bad index";
        return localValueInfos[i];
    }

    public DebugLocalInfo getLocal(int i) {
        assert !this.isPrimary() : "primary range does not have local vars";
        assert i >= 0 && i < localInfos.length : "bad index";
        return localInfos[i];
    }

    /*
     * Create a primary range.
     */
    public Range(StringTable stringTable, MethodEntry methodEntry, int lo, int hi, int line) {
        this(stringTable, methodEntry, lo, hi, line, null, false, null);
    }

    /*
     * Create a primary or secondary range.
     */
    public Range(StringTable stringTable, MethodEntry methodEntry, int lo, int hi, int line, Range primary, boolean isTopLevel, Range caller) {
        assert methodEntry != null;
        if (methodEntry.fileEntry != null) {
            stringTable.uniqueDebugString(methodEntry.fileEntry.getFileName());
            stringTable.uniqueDebugString(methodEntry.fileEntry.getPathName());
        }
        this.methodEntry = methodEntry;
        this.fullMethodName = isTopLevel ? stringTable.uniqueDebugString(constructClassAndMethodName()) : stringTable.uniqueString(constructClassAndMethodName());
        this.lo = lo;
        this.hi = hi;
        this.line = line;
        this.primary = primary;
        this.firstCallee = null;
        this.lastCallee = null;
        this.siblingCallee = null;
        this.caller = caller;
        if (caller != null) {
            caller.addCallee(this);
        }
        if (this.isPrimary()) {
            this.depth = -1;
        } else {
            this.depth = caller.depth + 1;
        }
    }

    private void addCallee(Range callee) {
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

    public boolean contains(Range other) {
        return (lo <= other.lo && hi >= other.hi);
    }

    public boolean isPrimary() {
        return getPrimary() == null;
    }

    public Range getPrimary() {
        return primary;
    }

    public String getClassName() {
        return methodEntry.ownerType.typeName;
    }

    public String getMethodName() {
        return methodEntry.memberName;
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
        return fullMethodName;
    }

    public String getFullMethodNameWithParams() {
        return constructClassAndMethodNameWithParams();
    }

    public boolean isDeoptTarget() {
        return methodEntry.isDeopt();
    }

    private String getExtendedMethodName(boolean includeClass, boolean includeParams, boolean includeReturnType) {
        StringBuilder builder = new StringBuilder();
        if (includeReturnType && methodEntry.valueType.typeName.length() > 0) {
            builder.append(methodEntry.valueType.typeName);
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
            builder.append(methodEntry.valueType.typeName);
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
        return methodEntry.fileEntry;
    }

    public int getFileIndex() {
        // the primary range's class entry indexes all files defined by the compilation unit
        Range primaryRange = (isPrimary() ? this : getPrimary());
        ClassEntry owner = primaryRange.methodEntry.ownerType();
        return owner.localFilesIdx(getFileEntry());
    }

    public int getModifiers() {
        return methodEntry.modifiers;
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

    public Range getCaller() {
        return caller;
    }

    public Range getFirstCallee() {
        return firstCallee;
    }

    public Range getSiblingCallee() {
        return siblingCallee;
    }

    public boolean isLeaf() {
        return firstCallee == null;
    }

    public boolean includesInlineRanges() {
        Range child = firstCallee;
        while (child != null && child.isLeaf()) {
            child = child.siblingCallee;
        }
        return child != null;
    }

    public int getDepth() {
        return depth;
    }

    public void setLocalValueInfo(DebugLocalValueInfo[] localValueInfos) {
        int len = localValueInfos.length;
        this.localValueInfos = localValueInfos;
        this.localInfos = (len > 0 ? new DebugLocalInfo[len] : EMPTY_LOCAL_INFOS);
        // set up mapping from local values to local variables
        for (int i = 0; i < len; i++) {
            localInfos[i] = methodEntry.recordLocal(localValueInfos[i]);
        }
    }

    public HashMap<DebugLocalInfo, List<Range>> getVarRangeMap() {
        MethodEntry calleeMethod;
        if (isPrimary()) {
            calleeMethod = getMethodEntry();
        } else {
            assert !isLeaf() : "should only be looking up var ranges for inlined calls";
            calleeMethod = firstCallee.getMethodEntry();
        }
        HashMap<DebugLocalInfo, List<Range>> varRangeMap = new HashMap<>();
        if (calleeMethod.getThisParam() != null) {
            varRangeMap.put(calleeMethod.getThisParam(), new ArrayList<Range>());
        }
        for (int i = 0; i < calleeMethod.getParamCount(); i++) {
            varRangeMap.put(calleeMethod.getParam(i), new ArrayList<Range>());
        }
        for (int i = 0; i < calleeMethod.getLocalCount(); i++) {
            varRangeMap.put(calleeMethod.getLocal(i), new ArrayList<Range>());
        }
        return updateVarRangeMap(varRangeMap);
    }

    public HashMap<DebugLocalInfo, List<Range>> updateVarRangeMap(HashMap<DebugLocalInfo, List<Range>> varRangeMap) {
        // leaf subranges of the current range may provide values for param or local vars
        // of this range's method. find them and index the range so that we can identify
        // both the local/param and the associated range.
        Range subRange = this.firstCallee;
        while (subRange != null) {
            addVarRanges(subRange, varRangeMap);
            subRange = subRange.siblingCallee;
        }
        return varRangeMap;
    }

    public void addVarRanges(Range subRange, HashMap<DebugLocalInfo, List<Range>> varRangeMap) {
        int localValueCount = subRange.getLocalValueCount();
        for (int i = 0; i < localValueCount; i++) {
            DebugLocalValueInfo localValueInfo = subRange.getLocalValue(i);
            DebugLocalInfo local = subRange.getLocal(i);
            if (local != null) {
                switch (localValueInfo.localKind()) {
                    case REGISTER:
                    case STACKSLOT:
                    case CONSTANT:
                        List<Range> varRanges = varRangeMap.get(local);
                        assert varRanges != null : "local not present in var to ranges map!";
                        varRanges.add(subRange);
                        break;
                    case UNDEFINED:
                        break;
                }
            }
        }
    }

    public DebugLocalValueInfo lookupValue(DebugLocalInfo local) {
        int localValueCount = getLocalValueCount();
        for (int i = 0; i < localValueCount; i++) {
            if (getLocal(i) == local) {
                return getLocalValue(i);
            }
        }
        return null;
    }
}
