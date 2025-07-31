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

package com.oracle.objectfile.debugentry;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MethodEntry extends MemberEntry {

    public record Local(LocalEntry localEntry, int line) {
    }

    private final List<LocalEntry> params;
    private final int lastParamSlot;

    /*
     * Locals are accumulated when they are seen when parsing frame states.
     */
    private List<Local> locals;
    private final boolean isDeopt;
    private boolean isInRange;
    private boolean isInlined;
    private final boolean isOverride;
    private final boolean isConstructor;
    private final int vtableOffset;
    private final String symbolName;

    public MethodEntry(FileEntry fileEntry, int line, String methodName, StructureTypeEntry ownerType,
                    TypeEntry valueType, int modifiers, List<LocalEntry> params, String symbolName, boolean isDeopt, boolean isOverride, boolean isConstructor,
                    int vtableOffset, int lastParamSlot) {
        super(fileEntry, line, methodName, ownerType, valueType, modifiers);
        this.params = params;
        this.symbolName = symbolName;
        this.isDeopt = isDeopt;
        this.isOverride = isOverride;
        this.isConstructor = isConstructor;
        this.vtableOffset = vtableOffset;
        this.lastParamSlot = lastParamSlot;
        this.locals = new ArrayList<>();

        /*
         * Flags to identify compiled methods. We set inRange if there is a compilation for this
         * method and inlined if it is encountered as a CallNode when traversing the compilation
         * result frame tree.
         */
        this.isInRange = false;
        this.isInlined = false;
    }

    @Override
    public void seal() {
        super.seal();
        assert locals instanceof ArrayList<Local> : "MethodEntry should only be sealed once";
        // Sort and trim locals list.
        locals = locals.stream().sorted(Comparator.comparingInt(Local::line).thenComparing(Local::localEntry)).toList();
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

    public List<TypeEntry> getParamTypes() {
        return params.stream().map(LocalEntry::type).toList();
    }

    public List<LocalEntry> getParams() {
        return params;
    }

    public boolean isThisParam(LocalEntry param) {
        assert param != null && params.contains(param);
        return !isStatic() && param.slot() == 0;
    }

    /**
     * Looks up a {@code LocalEntry} in this method. Decides by slot number whether this is a
     * parameter or local variable. This method will only return a {@code LocalEntry} that is stored
     * in either {@link #params} or {@link #locals}.
     * <p>
     * We can get either of three results:
     * <ol>
     * <li>Invalid {@code LocalEntry}: Either negative slot or no match parameter found.</li>
     * <li>Parameter {@code LocalEntry}: 0 < slot <= {@link #lastParamSlot} and part of
     * {@link #params}.</li>
     * <li>Local Variable {@code LocalEntry}: slot > {@link #lastParamSlot}.</li>
     * </ol>
     * <p>
     * If a local was previously not known it is added to {@link #locals} with the given line.
     * Otherwise, we check if the line in {@link #locals} is lower than the given line for the local
     * variable.
     * <p>
     * This is only called during debug info generation. No more locals are added to this
     * {@code MethodEntry} when writing debug info to the object file.
     * 
     * @param localEntry the {@code LocalEntry} to lookup
     * @param line the given line
     * @return the local entry stored in {@link #locals}
     */
    public LocalEntry getOrAddLocalEntry(LocalEntry localEntry, int line) {
        assert locals instanceof ArrayList<Local> : "Can only add locals before a MethodEntry is sealed.";
        if (localEntry.slot() < 0) {
            return null;
        }

        if (localEntry.slot() <= lastParamSlot) {
            for (LocalEntry param : params) {
                if (param.equals(localEntry)) {
                    return param;
                }
            }
        } else {
            /*
             * Add a new local entry if it was not already present in the locals list. A local is
             * unique if it has different slot, name, and/or type. If the local is already
             * contained, we might update the line number to the lowest occurring line number.
             */
            Local local = new Local(localEntry, line);
            synchronized (locals) {
                if (locals.stream().noneMatch(l -> l.localEntry.equals(localEntry)) || locals.removeIf(l -> l.localEntry.equals(localEntry) && l.line > line)) {
                    locals.add(local);
                }
            }
            return localEntry;
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

    public List<Local> getLocals() {
        assert !(locals instanceof ArrayList<Local>) : "Can only access locals after a MethodEntry is sealed.";
        return locals;
    }

    public int getLastParamSlot() {
        return lastParamSlot;
    }

    public boolean isStatic() {
        return Modifier.isStatic(getModifiers());
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
