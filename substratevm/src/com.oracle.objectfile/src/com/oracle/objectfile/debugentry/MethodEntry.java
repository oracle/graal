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

import jdk.vm.ci.meta.JavaKind;

import java.util.ArrayList;
import java.util.List;

public class MethodEntry extends MemberEntry {
    private final LocalEntry thisParam;
    private final List<LocalEntry> paramInfos;
    private final int firstLocalSlot;
    // local vars are accumulated as they are referenced sorted by slot, then name, then
    // type name. we don't currently deal handle references to locals with no slot.
    private final List<LocalEntry> locals;
    private final boolean isDeopt;
    private boolean isInRange;
    private boolean isInlined;
    private final boolean isOverride;
    private final boolean isConstructor;
    private final int vtableOffset;
    private final String symbolName;

    @SuppressWarnings("this-escape")
    public MethodEntry(FileEntry fileEntry, int line, String methodName, StructureTypeEntry ownerType,
                       TypeEntry valueType, int modifiers, List<LocalEntry> paramInfos, LocalEntry thisParam,
                       String symbolName, boolean isDeopt, boolean isOverride, boolean isConstructor, int vtableOffset,
                       int firstLocalSlot) {
        super(fileEntry, line, methodName, ownerType, valueType, modifiers);
        this.paramInfos = paramInfos;
        this.thisParam = thisParam;
        this.symbolName = symbolName;
        this.isDeopt = isDeopt;
        this.isOverride = isOverride;
        this.isConstructor = isConstructor;
        this.vtableOffset = vtableOffset;
        this.firstLocalSlot = firstLocalSlot;

        this.locals = new ArrayList<>();
        this.isInRange = false;
        this.isInlined = false;
    }

    public String getMethodName() {
        return getMemberName();
    }

    @Override
    public ClassEntry getOwnerType() {
        StructureTypeEntry ownerType = super.getOwnerType();
        assert ownerType instanceof ClassEntry;
        return (ClassEntry) ownerType;
    }

    public int getParamCount() {
        return paramInfos.size();
    }

    public TypeEntry getParamType(int idx) {
        assert idx < paramInfos.size();
        return paramInfos.get(idx).type();
    }

    public List<TypeEntry> getParamTypes() {
        return paramInfos.stream().map(LocalEntry::type).toList();
    }

    public String getParamTypeName(int idx) {
        assert idx < paramInfos.size();
        return paramInfos.get(idx).type().getTypeName();
    }

    public String getParamName(int idx) {
        assert idx < paramInfos.size();
        /* N.b. param names may be null. */
        return paramInfos.get(idx).name();
    }

    public int getParamLine(int idx) {
        assert idx < paramInfos.size();
        /* N.b. param names may be null. */
        return paramInfos.get(idx).line();
    }

    public LocalEntry getParam(int i) {
        assert i >= 0 && i < paramInfos.size() : "bad param index";
        return paramInfos.get(i);
    }

    public LocalEntry getThisParam() {
        return thisParam;
    }

    public int getLocalCount() {
        return locals.size();
    }

    public LocalEntry getLocal(int i) {
        assert i >= 0 && i < locals.size() : "bad param index";
        return locals.get(i);
    }

    public boolean isDeopt() {
        return isDeopt;
    }

    public boolean isInRange() {
        return isInRange;
    }

    public void setInRange() {
        isInRange = true;
    }

    public boolean isInlined() {
        return isInlined;
    }

    public void setInlined() {
        isInlined = true;
    }

    public boolean isOverride() {
        return isOverride;
    }

    public boolean isConstructor() {
        return isConstructor;
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

    public LocalEntry lookupLocalEntry(String name, int slot, TypeEntry type, JavaKind kind, int line) {
        if (slot < 0) {
            return null;
        } else if (slot < firstLocalSlot) {
            if (thisParam != null) {
                if (thisParam.slot() == slot && thisParam.name().equals(name) && thisParam.type() == type) {
                    return thisParam;
                }
            }
            for (LocalEntry param : paramInfos) {
                if (param.slot() == slot && param.name().equals(name) && param.type() == type) {
                    return param;
                }
            }
            return null;
        } else {
            for (LocalEntry local : locals) {
                if (local.slot() == slot && local.name().equals(name) && local.type() == type) {
                    if (line >= 0 && (local.line() < 0 || line < local.line())) {
                        local.setLine(line);
                    }
                    return local;
                } else if (local.slot() > slot) {
                    LocalEntry newLocal = new LocalEntry(name, type, kind, slot, line);
                    locals.add(locals.indexOf(local), newLocal);
                    return newLocal;
                }
            }
            LocalEntry newLocal = new LocalEntry(name, type, kind, slot, line);
            locals.add(newLocal);
            return newLocal;
        }
    }
}
