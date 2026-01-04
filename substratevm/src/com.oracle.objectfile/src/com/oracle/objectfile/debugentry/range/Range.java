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

package com.oracle.objectfile.debugentry.range;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.oracle.objectfile.debugentry.ConstantValueEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.LocalEntry;
import com.oracle.objectfile.debugentry.LocalValueEntry;
import com.oracle.objectfile.debugentry.MethodEntry;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

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
public abstract class Range implements Comparable<Range> {
    private final MethodEntry methodEntry;
    private final int loOffset;
    private int hiOffset;
    private final int line;
    private final int depth;

    /**
     * The range for the caller or the primary range when this range is for top level method code.
     */
    private final CallRange caller;

    private final PrimaryRange primary;

    /**
     * Values for the associated method's local and parameter variables that are available or,
     * alternatively, marked as invalid in this range.
     */
    private final Map<LocalEntry, LocalValueEntry> localValueInfos;
    /**
     * Mapping of local entries to subranges they occur in.
     */
    private Map<LocalEntry, List<Range>> varRangeMap = null;

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
    public static PrimaryRange createPrimary(MethodEntry methodEntry, int lo, int hi, int line, long codeOffset) {
        return new PrimaryRange(methodEntry, lo, hi, line, codeOffset);
    }

    /**
     * Create a subrange representing a segment of the address range for code of a top level or
     * inlined compiled method. The result will either be a call or a leaf range.
     * 
     * @param methodEntry the method from which code in the subrange is derived.
     * @param lo the lowest address included in the range.
     * @param hi the first address above the highest address in the range.
     * @param line the line number associated with the range
     * @param caller the range for which this is a subrange, either an inlined call range or the
     *            primary range.
     * @param isLeaf true if this is a leaf range with no subranges
     * @return a new subrange to be linked into the range tree below the primary range.
     */
    public static Range createSubrange(PrimaryRange primary, MethodEntry methodEntry, Map<LocalEntry, LocalValueEntry> localValueInfos, int lo, int hi, int line, CallRange caller, boolean isLeaf) {
        assert caller != null;
        if (caller != primary) {
            methodEntry.setInlined();
        }
        Range callee;
        if (isLeaf) {
            callee = new LeafRange(primary, methodEntry, localValueInfos, lo, hi, line, caller, caller.getDepth() + 1);
        } else {
            callee = new CallRange(primary, methodEntry, localValueInfos, lo, hi, line, caller, caller.getDepth() + 1);
        }

        caller.addCallee(callee);
        return callee;
    }

    protected Range(PrimaryRange primary, MethodEntry methodEntry, Map<LocalEntry, LocalValueEntry> localValueInfos, int loOffset, int hiOffset, int line, CallRange caller, int depth) {
        assert methodEntry != null;
        this.primary = primary;
        this.methodEntry = methodEntry;
        this.loOffset = loOffset;
        this.hiOffset = hiOffset;
        this.line = line;
        this.caller = caller;
        this.depth = depth;
        this.localValueInfos = localValueInfos;
    }

    public void seal() {
        // nothing to do here
    }

    /**
     * Splits an initial range at the given stack decrement point. The lower split will stay as is
     * with its high offset reduced to the stack decrement point. The higher split starts at the
     * stack decrement point and has updated local value entries for the parameters in the then
     * extended stack.
     *
     * @param splitLocalValueInfos the localValueInfos for the split off range
     * @param stackDecrement the offset to split the range at
     * @return the higher split, that has been split off the original {@code Range}
     */
    public Range split(Map<LocalEntry, LocalValueEntry> splitLocalValueInfos, int stackDecrement) {
        // This should be for an initial range extending beyond the stack decrement.
        assert loOffset == 0 && loOffset < stackDecrement && stackDecrement < hiOffset : "invalid split request";

        int splitHiOffset = hiOffset;
        hiOffset = stackDecrement;
        return Range.createSubrange(primary, methodEntry, splitLocalValueInfos, stackDecrement, splitHiOffset, line, caller, isLeaf());
    }

    public boolean contains(Range other) {
        return (loOffset <= other.loOffset && hiOffset >= other.hiOffset);
    }

    public boolean isPrimary() {
        return false;
    }

    public String getTypeName() {
        return methodEntry.getOwnerType().getTypeName();
    }

    public String getMethodName() {
        return methodEntry.getMethodName();
    }

    public String getSymbolName() {
        return methodEntry.getSymbolName();
    }

    public int getHiOffset() {
        return hiOffset;
    }

    public int getLoOffset() {
        return loOffset;
    }

    public long getCodeOffset() {
        return primary.getCodeOffset();
    }

    public long getLo() {
        return getCodeOffset() + loOffset;
    }

    public long getHi() {
        return getCodeOffset() + hiOffset;
    }

    public PrimaryRange getPrimary() {
        return primary;
    }

    public int getLine() {
        return line;
    }

    public String getFullMethodName() {
        return getExtendedMethodName(false);
    }

    public String getFullMethodNameWithParams() {
        return getExtendedMethodName(true);
    }

    public boolean isDeoptTarget() {
        return methodEntry.isDeopt();
    }

    private String getExtendedMethodName(boolean includeParams) {
        StringBuilder builder = new StringBuilder();
        if (getTypeName() != null) {
            builder.append(getTypeName());
            builder.append(".");
        }
        builder.append(getMethodName());
        if (includeParams) {
            methodEntry.getParamTypes().forEach(t -> {
                if (!builder.isEmpty()) {
                    builder.append(", ");
                }
                builder.append(t.getTypeName());
            });
            builder.insert(0, '(');
            builder.append(')');
        }
        return builder.toString();
    }

    public FileEntry getFileEntry() {
        return methodEntry.getFileEntry();
    }

    public int getModifiers() {
        return methodEntry.getModifiers();
    }

    @Override
    public String toString() {
        return String.format("Range(lo=0x%05x hi=0x%05x %s %s:%d)", getLo(), getHi(), getFullMethodNameWithParams(), methodEntry.getFullFileName(), line);
    }

    @Override
    public int compareTo(Range other) {
        int cmp = Long.compare(getLo(), other.getLo());
        if (cmp == 0) {
            cmp = Long.compare(getHi(), other.getHi());
        }
        if (cmp == 0) {
            return Integer.compare(line, other.line);
        }
        return cmp;
    }

    public String getFileName() {
        return methodEntry.getFileName();
    }

    public MethodEntry getMethodEntry() {
        return methodEntry;
    }

    public abstract boolean isLeaf();

    public boolean includesInlineRanges() {
        for (Range callee : getCallees()) {
            if (!callee.isLeaf()) {
                return true;
            }
        }
        return false;
    }

    public List<Range> getCallees() {
        return List.of();
    }

    public Stream<Range> rangeStream() {
        return Stream.of(this);
    }

    public int getDepth() {
        return depth;
    }

    public Map<LocalEntry, LocalValueEntry> getLocalValueInfos() {
        return localValueInfos;
    }

    /**
     * Returns the subranges grouped by local entries in the subranges. If the map is empty, it
     * first tries to populate the map with its callees {@link #localValueInfos}.
     * 
     * @return a mapping from local entries to subranges
     */
    public Map<LocalEntry, List<Range>> getVarRangeMap() {
        if (varRangeMap == null) {
            varRangeMap = new HashMap<>();
            for (Range callee : getCallees()) {
                for (LocalEntry local : callee.localValueInfos.keySet()) {
                    varRangeMap.computeIfAbsent(local, l -> new ArrayList<>()).add(callee);
                }
            }
            // Trim var range map.
            varRangeMap = Map.copyOf(varRangeMap);
        }

        return varRangeMap;
    }

    /**
     * Returns whether subranges contain a value in {@link #localValueInfos} for a given local
     * entry. A value is valid if it exists, and it can be represented as local values in the debug
     * info.
     * 
     * @param local the local entry to check for
     * @return whether a local entry has a value in any of this range's subranges
     */
    public boolean hasLocalValues(LocalEntry local) {
        for (Range callee : getVarRangeMap().getOrDefault(local, List.of())) {
            LocalValueEntry localValue = callee.lookupValue(local);
            if (localValue != null) {
                if (localValue instanceof ConstantValueEntry constantValueEntry) {
                    JavaConstant constant = constantValueEntry.constant();
                    // can only handle primitive or null constants just now
                    return constant instanceof PrimitiveConstant || constant.getJavaKind() == JavaKind.Object;
                } else {
                    // register or stack value
                    return true;
                }
            }
        }
        return false;
    }

    public Range getCaller() {
        return caller;
    }

    public LocalValueEntry lookupValue(LocalEntry local) {
        return localValueInfos.getOrDefault(local, null);
    }

    public boolean tryMerge(Range that) {
        assert this.caller == that.caller;
        assert this.isLeaf() == that.isLeaf();
        assert this.depth == that.depth : "should only compare sibling ranges";
        assert this.hiOffset <= that.loOffset : "later nodes should not overlap earlier ones";
        if (this.hiOffset != that.loOffset) {
            return false;
        }
        if (this.methodEntry != that.methodEntry) {
            return false;
        }
        if (this.line != that.line) {
            return false;
        }
        if (!this.localValueInfos.equals(that.localValueInfos)) {
            return false;
        }
        // merging just requires updating lo and hi range as everything else is equal
        this.hiOffset = that.hiOffset;
        caller.removeCallee(that);
        return true;
    }
}
