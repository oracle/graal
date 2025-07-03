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

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * An intermediate type that provides behaviour for managing fields. This unifies code for handling
 * header structures and Java instance and array classes that both support data members.
 */
public abstract sealed class StructureTypeEntry extends TypeEntry permits ArrayTypeEntry, ClassEntry, ForeignStructTypeEntry, HeaderTypeEntry {
    /**
     * Details of fields located in this instance.
     */
    private final ConcurrentSkipListSet<FieldEntry> fields;

    /**
     * The type signature of this types' layout. The layout of a type contains debug info of fields
     * and methods of a type, which is needed for representing the class hierarchy. The super type
     * entry in the debug info needs to directly contain the type info instead of a pointer.
     */
    protected long layoutTypeSignature;

    public StructureTypeEntry(String typeName, int size, long classOffset, long typeSignature,
                    long compressedTypeSignature, long layoutTypeSignature) {
        super(typeName, size, classOffset, typeSignature, compressedTypeSignature);
        this.layoutTypeSignature = layoutTypeSignature;

        this.fields = new ConcurrentSkipListSet<>(Comparator.comparingInt(FieldEntry::getOffset));
    }

    public long getLayoutTypeSignature() {
        return layoutTypeSignature;
    }

    public void addField(FieldEntry field) {
        fields.add(field);
    }

    public List<FieldEntry> getFields() {
        return List.copyOf(fields);
    }
}
