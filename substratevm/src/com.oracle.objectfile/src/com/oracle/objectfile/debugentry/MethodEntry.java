/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.ListIterator;

import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugCodeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalValueInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocationInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugMethodInfo;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class MethodEntry extends MemberEntry {
    private final TypeEntry[] paramTypes;
    private final DebugLocalInfo thisParam;
    private final DebugLocalInfo[] paramInfos;
    private final int firstLocalSlot;
    // local vars are accumulated as they are referenced sorted by slot, then name, then
    // type name. we don't currently deal handle references to locals with no slot.
    private final ArrayList<DebugLocalInfo> locals;
    static final int DEOPT = 1 << 0;
    static final int IN_RANGE = 1 << 1;
    static final int INLINED = 1 << 2;
    static final int IS_OVERRIDE = 1 << 3;
    static final int IS_CONSTRUCTOR = 1 << 4;
    int flags;
    int vtableOffset = -1;
    final String symbolName;

    public MethodEntry(DebugInfoBase debugInfoBase, DebugMethodInfo debugMethodInfo,
                    FileEntry fileEntry, int line, String methodName, ClassEntry ownerType,
                    TypeEntry valueType, TypeEntry[] paramTypes, DebugLocalInfo[] paramInfos, DebugLocalInfo thisParam) {
        super(fileEntry, line, methodName, ownerType, valueType, debugMethodInfo.modifiers());
        this.paramTypes = paramTypes;
        this.paramInfos = paramInfos;
        this.thisParam = thisParam;
        this.symbolName = debugMethodInfo.symbolNameForMethod();
        this.flags = 0;
        if (debugMethodInfo.isDeoptTarget()) {
            setIsDeopt();
        }
        if (debugMethodInfo.isConstructor()) {
            setIsConstructor();
        }
        if (debugMethodInfo.isOverride()) {
            setIsOverride();
        }
        vtableOffset = debugMethodInfo.vtableOffset();
        int paramCount = paramInfos.length;
        if (paramCount > 0) {
            DebugLocalInfo lastParam = paramInfos[paramCount - 1];
            firstLocalSlot = lastParam.slot() + lastParam.slotCount();
        } else {
            firstLocalSlot = (thisParam == null ? 0 : thisParam.slotCount());
        }
        locals = new ArrayList<>();
        updateRangeInfo(debugInfoBase, debugMethodInfo);
    }

    public String methodName() {
        return memberName;
    }

    @Override
    public ClassEntry ownerType() {
        assert ownerType instanceof ClassEntry;
        return (ClassEntry) ownerType;
    }

    public int getParamCount() {
        return paramInfos.length;
    }

    public TypeEntry getParamType(int idx) {
        assert idx < paramInfos.length;
        return paramTypes[idx];
    }

    public TypeEntry[] getParamTypes() {
        return paramTypes;
    }

    public String getParamTypeName(int idx) {
        assert idx < paramTypes.length;
        return paramTypes[idx].getTypeName();
    }

    public String getParamName(int idx) {
        assert idx < paramInfos.length;
        /* N.b. param names may be null. */
        return paramInfos[idx].name();
    }

    public int getParamLine(int idx) {
        assert idx < paramInfos.length;
        /* N.b. param names may be null. */
        return paramInfos[idx].line();
    }

    public DebugLocalInfo getParam(int i) {
        assert i >= 0 && i < paramInfos.length : "bad param index";
        return paramInfos[i];
    }

    public DebugLocalInfo getThisParam() {
        return thisParam;
    }

    public int getLocalCount() {
        return locals.size();
    }

    public DebugLocalInfo getLocal(int i) {
        assert i >= 0 && i < locals.size() : "bad param index";
        return locals.get(i);
    }

    private void setIsDeopt() {
        flags |= DEOPT;
    }

    public boolean isDeopt() {
        return (flags & DEOPT) != 0;
    }

    private void setIsInRange() {
        flags |= IN_RANGE;
    }

    public boolean isInRange() {
        return (flags & IN_RANGE) != 0;
    }

    private void setIsInlined() {
        flags |= INLINED;
    }

    public boolean isInlined() {
        return (flags & INLINED) != 0;
    }

    private void setIsOverride() {
        flags |= IS_OVERRIDE;
    }

    public boolean isOverride() {
        return (flags & IS_OVERRIDE) != 0;
    }

    private void setIsConstructor() {
        flags |= IS_CONSTRUCTOR;
    }

    public boolean isConstructor() {
        return (flags & IS_CONSTRUCTOR) != 0;
    }

    /**
     * Sets {@code isInRange} and ensures that the {@code fileEntry} is up to date. If the
     * MethodEntry was added by traversing the DeclaredMethods of a Class its fileEntry will point
     * to the original source file, thus it will be wrong for substituted methods. As a result when
     * setting a MethodEntry as isInRange we also make sure that its fileEntry reflects the file
     * info associated with the corresponding Range.
     *
     * @param debugInfoBase
     * @param debugMethodInfo
     */
    public void updateRangeInfo(DebugInfoBase debugInfoBase, DebugMethodInfo debugMethodInfo) {
        if (debugMethodInfo instanceof DebugLocationInfo) {
            DebugLocationInfo locationInfo = (DebugLocationInfo) debugMethodInfo;
            if (locationInfo.getCaller() != null) {
                /* this is a real inlined method */
                setIsInlined();
            }
        } else if (debugMethodInfo instanceof DebugCodeInfo) {
            /* this method is being notified as a top level compiled method */
            if (isInRange()) {
                /* it has already been seen -- just check for consistency */
                assert fileEntry == debugInfoBase.ensureFileEntry(debugMethodInfo);
            } else {
                /*
                 * If the MethodEntry was added by traversing the DeclaredMethods of a Class its
                 * fileEntry may point to the original source file, which will be wrong for
                 * substituted methods. As a result when setting a MethodEntry as isInRange we also
                 * make sure that its fileEntry reflects the file info associated with the
                 * corresponding Range.
                 */
                setIsInRange();
                fileEntry = debugInfoBase.ensureFileEntry(debugMethodInfo);
            }
        }
    }

    public boolean isVirtual() {
        return vtableOffset >= 0;
    }

    public int getVtableOffset() {
        return vtableOffset;
    }

    public String getSymbolName() {
        return symbolName;
    }

    /**
     * Return a unique local or parameter variable associated with the value, optionally recording
     * it as a new local variable or fail, returning null, when the local value does not conform
     * with existing recorded parameter or local variables. Values with invalid (negative) slots
     * always fail. Values whose slot is associated with a parameter only conform if their name and
     * type equal those of the parameter. Values whose slot is in the local range will always
     * succeed,. either by matchign the slot and name of an existing local or by being recorded as a
     * new local variable.
     * 
     * @param localValueInfo
     * @return the unique local variable with which this local value can be legitimately associated
     *         otherwise null.
     */
    public DebugLocalInfo recordLocal(DebugLocalValueInfo localValueInfo) {
        int slot = localValueInfo.slot();
        if (slot < 0) {
            return null;
        } else {
            if (slot < firstLocalSlot) {
                return matchParam(localValueInfo);
            } else {
                return matchLocal(localValueInfo);
            }
        }
    }

    private DebugLocalInfo matchParam(DebugLocalValueInfo localValueInfo) {
        if (thisParam != null) {
            if (checkMatch(thisParam, localValueInfo)) {
                return thisParam;
            }
        }
        for (int i = 0; i < paramInfos.length; i++) {
            DebugLocalInfo paramInfo = paramInfos[i];
            if (checkMatch(paramInfo, localValueInfo)) {
                return paramInfo;
            }
        }
        return null;
    }

    /**
     * wrapper class for a local value that stands in as a unique identifier for the associated
     * local variable while allowing its line to be adjusted when earlier occurrences of the same
     * local are identified.
     */
    private static class DebugLocalInfoWrapper implements DebugLocalInfo {
        DebugLocalValueInfo value;
        int line;

        DebugLocalInfoWrapper(DebugLocalValueInfo value) {
            this.value = value;
            this.line = value.line();
        }

        @Override
        public ResolvedJavaType valueType() {
            return value.valueType();
        }

        @Override
        public String name() {
            return value.name();
        }

        @Override
        public String typeName() {
            return value.typeName();
        }

        @Override
        public int slot() {
            return value.slot();
        }

        @Override
        public int slotCount() {
            return value.slotCount();
        }

        @Override
        public JavaKind javaKind() {
            return value.javaKind();
        }

        @Override
        public int line() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }
    }

    private DebugLocalInfo matchLocal(DebugLocalValueInfo localValueInfo) {
        ListIterator<DebugLocalInfo> listIterator = locals.listIterator();
        while (listIterator.hasNext()) {
            DebugLocalInfoWrapper next = (DebugLocalInfoWrapper) listIterator.next();
            if (checkMatch(next, localValueInfo)) {
                int currentLine = next.line();
                int newLine = localValueInfo.line();
                if ((currentLine < 0 && newLine >= 0) ||
                                (newLine >= 0 && newLine < currentLine)) {
                    next.setLine(newLine);
                }
                return next;
            } else if (next.slot() > localValueInfo.slot()) {
                // we have iterated just beyond the insertion point
                // so wind cursor back one element
                listIterator.previous();
                break;
            }
        }
        DebugLocalInfoWrapper newLocal = new DebugLocalInfoWrapper(localValueInfo);
        // add at the current cursor position
        listIterator.add(newLocal);
        return newLocal;
    }

    boolean checkMatch(DebugLocalInfo local, DebugLocalValueInfo value) {
        boolean isMatch = (local.slot() == value.slot() &&
                        local.name().equals(value.name()) &&
                        local.typeName().equals(value.typeName()));
        assert !isMatch || verifyMatch(local, value) : "failed to verify matched var and value";
        return isMatch;
    }

    private static boolean verifyMatch(DebugLocalInfo local, DebugLocalValueInfo value) {
        // slot counts are normally expected to match
        if (local.slotCount() == value.slotCount()) {
            return true;
        }
        // we can have a zero count for the local or value if it is undefined
        if (local.slotCount() == 0 || value.slotCount() == 0) {
            return true;
        }
        // pseudo-object locals can appear as longs
        if (local.javaKind() == JavaKind.Object && value.javaKind() == JavaKind.Long) {
            return true;
        }
        // something is wrong
        return false;
    }
}
