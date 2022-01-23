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

import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugCodeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLineInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugMethodInfo;

public class MethodEntry extends MemberEntry {
    private final TypeEntry[] paramTypes;
    private final String[] paramNames;
    static final int DEOPT = 1 << 0;
    static final int IN_RANGE = 1 << 1;
    static final int INLINED = 1 << 2;
    int flags;
    final String symbolName;

    public MethodEntry(DebugInfoBase debugInfoBase, DebugMethodInfo debugMethodInfo,
                    FileEntry fileEntry, String methodName, ClassEntry ownerType,
                    TypeEntry valueType, TypeEntry[] paramTypes, String[] paramNames) {
        super(fileEntry, methodName, ownerType, valueType, debugMethodInfo.modifiers());
        assert ((paramTypes == null && paramNames == null) ||
                        (paramTypes != null && paramNames != null && paramTypes.length == paramNames.length));
        this.paramTypes = paramTypes;
        this.paramNames = paramNames;
        this.symbolName = debugMethodInfo.symbolNameForMethod();
        this.flags = 0;
        if (debugMethodInfo.isDeoptTarget()) {
            setIsDeopt();
        }
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
        return (paramTypes == null ? 0 : paramTypes.length);
    }

    public TypeEntry getParamType(int idx) {
        assert paramTypes != null;
        assert idx < paramTypes.length;
        return paramTypes[idx];
    }

    public TypeEntry[] getParamTypes() {
        return paramTypes;
    }

    public String getParamTypeName(int idx) {
        assert paramTypes != null;
        assert idx < paramTypes.length;
        assert paramTypes[idx] != null;
        return paramTypes[idx].getTypeName();
    }

    public String getParamName(int idx) {
        assert paramNames != null;
        assert idx < paramNames.length;
        /* N.b. param names may be null. */
        return paramNames[idx];
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
        if (debugMethodInfo instanceof DebugLineInfo) {
            DebugLineInfo lineInfo = (DebugLineInfo) debugMethodInfo;
            if (lineInfo.getCaller() != null) {
                /* this is a real inlined method not just a top level primary range */
                setIsInlined();
            }
        } else if (debugMethodInfo instanceof DebugCodeInfo) {
            /* this method has been seen in a primary range */
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

    public String getSymbolName() {
        return symbolName;
    }
}
