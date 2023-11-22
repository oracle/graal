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

/**
 * An abstract class providing modelling a generic class member which includes behaviour and data
 * shared by both field and method entries.
 */
public abstract class MemberEntry {
    protected FileEntry fileEntry;
    protected final int line;
    protected final String memberName;
    protected final StructureTypeEntry ownerType;
    protected final TypeEntry valueType;
    protected final int modifiers;

    public MemberEntry(FileEntry fileEntry, String memberName, StructureTypeEntry ownerType, TypeEntry valueType, int modifiers) {
        this(fileEntry, 0, memberName, ownerType, valueType, modifiers);
    }

    public MemberEntry(FileEntry fileEntry, int line, String memberName, StructureTypeEntry ownerType, TypeEntry valueType, int modifiers) {
        assert line >= 0;
        this.fileEntry = fileEntry;
        this.line = line;
        this.memberName = memberName;
        this.ownerType = ownerType;
        this.valueType = valueType;
        this.modifiers = modifiers;
    }

    public String getFileName() {
        if (fileEntry != null) {
            return fileEntry.getFileName();
        } else {
            return "";
        }
    }

    public String getFullFileName() {
        if (fileEntry != null) {
            return fileEntry.getFullName();
        } else {
            return null;
        }
    }

    @SuppressWarnings("unused")
    String getDirName() {
        if (fileEntry != null) {
            return fileEntry.getPathName();
        } else {
            return "";
        }
    }

    public FileEntry getFileEntry() {
        return fileEntry;
    }

    public int getFileIdx() {
        if (ownerType instanceof ClassEntry) {
            return ((ClassEntry) ownerType).getFileIdx(fileEntry);
        }
        // should not be asking for a file for header fields
        assert false : "not expecting a file lookup for header fields";
        return 1;
    }

    public int getLine() {
        return line;
    }

    public StructureTypeEntry ownerType() {
        return ownerType;
    }

    public TypeEntry getValueType() {
        return valueType;
    }

    public int getModifiers() {
        return modifiers;
    }

    public String getModifiersString() {
        return ownerType.memberModifiers(modifiers);
    }
}
