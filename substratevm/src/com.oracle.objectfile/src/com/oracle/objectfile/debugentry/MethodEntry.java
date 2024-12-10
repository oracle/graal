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

import java.util.List;

public class MethodEntry extends MemberEntry {
    private final LocalEntry thisParam;
    private final List<LocalEntry> paramInfos;
    private final int lastParamSlot;
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
                    int lastParamSlot, List<LocalEntry> locals) {
        super(fileEntry, line, methodName, ownerType, valueType, modifiers);
        this.paramInfos = paramInfos;
        this.thisParam = thisParam;
        this.symbolName = symbolName;
        this.isDeopt = isDeopt;
        this.isOverride = isOverride;
        this.isConstructor = isConstructor;
        this.vtableOffset = vtableOffset;
        this.lastParamSlot = lastParamSlot;
        this.locals = locals;

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

    public LocalEntry getParam(int i) {
        assert i >= 0 && i < paramInfos.size() : "bad param index";
        return paramInfos.get(i);
    }

    public List<LocalEntry> getParams() {
        return List.copyOf(paramInfos);
    }

    public LocalEntry getThisParam() {
        return thisParam;
    }

    public LocalEntry lookupLocalEntry(String name, int slot, TypeEntry type, int line) {
        if (slot < 0) {
            return null;
        }

        if (slot <= lastParamSlot) {
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
        } else {
            for (LocalEntry local : locals) {
                if (local.slot() == slot && local.name().equals(name) && local.type() == type) {
                    return local;
                }
            }

            LocalEntry local = new LocalEntry(name, type, slot, line);
            synchronized (locals) {
                if (!locals.contains(local)) {
                    locals.add(local);
                }
            }
            return local;
        }

        /*
         * The slot is within the range of the params, but none of the params exactly match. This
         * might be some local value that is stored in a slot where we expect a param. We just
         * ignore such values for now.
         *
         * This also applies to params that are inferred from frame values, as the types do not
         * match most of the time.
         */
        return null;
    }

    public List<LocalEntry> getLocals() {
        return List.copyOf(locals);
    }

    public int getLastParamSlot() {
        return lastParamSlot;
    }

    public boolean isStatic() {
        return thisParam == null;
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
}
