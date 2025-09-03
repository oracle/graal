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

/**
 * An abstract class providing modelling a generic class member which includes behaviour and data
 * shared by both field and method entries.
 */
public abstract class MemberEntry {
    private final FileEntry fileEntry;
    private final int line;
    private final String memberName;
    private final StructureTypeEntry ownerType;
    private final TypeEntry valueType;
    private final int modifiers;

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

    public void seal() {
        // nothing to do here
    }

    public String getFileName() {
        if (fileEntry != null) {
            return fileEntry.fileName();
        } else {
            return "";
        }
    }

    public String getFullFileName() {
        if (fileEntry != null) {
            return fileEntry.getFullName();
        } else {
            return "";
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

    /**
     * Fetch the file index from its owner class entry with {@link ClassEntry#getFileIdx}. The file
     * entry must only be fetched for members whose owner is a {@link ClassEntry}.
     * 
     * @return the file index of this members file in the owner class entry
     */
    public int getFileIdx() {
        if (ownerType instanceof ClassEntry) {
            return ((ClassEntry) ownerType).getFileIdx(fileEntry);
        }
        // should not be asking for a file for header fields
        assert false : "not expecting a file lookup for header fields";
        return 1;
    }

    public String getMemberName() {
        return memberName;
    }

    public int getLine() {
        return line;
    }

    public StructureTypeEntry getOwnerType() {
        return ownerType;
    }

    public TypeEntry getValueType() {
        return valueType;
    }

    public int getModifiers() {
        return modifiers;
    }

    public static String memberModifiers(int modifiers) {
        StringBuilder builder = new StringBuilder();
        if (Modifier.isPublic(modifiers)) {
            builder.append("public ");
        } else if (Modifier.isProtected(modifiers)) {
            builder.append("protected ");
        } else if (Modifier.isPrivate(modifiers)) {
            builder.append("private ");
        }
        if (Modifier.isFinal(modifiers)) {
            builder.append("final ");
        }
        if (Modifier.isAbstract(modifiers)) {
            builder.append("abstract ");
        } else if (Modifier.isVolatile(modifiers)) {
            builder.append("volatile ");
        } else if (Modifier.isTransient(modifiers)) {
            builder.append("transient ");
        } else if (Modifier.isSynchronized(modifiers)) {
            builder.append("synchronized ");
        }
        if (Modifier.isNative(modifiers)) {
            builder.append("native ");
        }
        if (Modifier.isStatic(modifiers)) {
            builder.append("static");
        } else {
            builder.append("instance");
        }

        return builder.toString();
    }

    public String getModifiersString() {
        return memberModifiers(modifiers);
    }

    @Override
    public String toString() {
        return String.format("Member(%s %s %s owner=%s %s:%d)", getModifiersString(), valueType.getTypeName(), memberName, ownerType.getTypeName(), getFullFileName(), line);
    }
}
