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

import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFieldInfo;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * An intermediate type that provides behaviour for managing fields. This unifies code for handling
 * header structures and Java instance and array classes that both support data members.
 */
public abstract class StructureTypeEntry extends TypeEntry {
    /**
     * Details of fields located in this instance.
     */
    protected final List<FieldEntry> fields;

    public StructureTypeEntry(String typeName, int size) {
        super(typeName, size);
        this.fields = new ArrayList<>();
    }

    public Stream<FieldEntry> fields() {
        return fields.stream();
    }

    public int fieldCount() {
        return fields.size();
    }

    protected void processField(DebugFieldInfo debugFieldInfo, DebugInfoBase debugInfoBase, DebugContext debugContext) {
        /* Delegate this so superclasses can override this and inspect the computed FieldEntry. */
        addField(debugFieldInfo, debugInfoBase, debugContext);
    }

    protected FieldEntry addField(DebugFieldInfo debugFieldInfo, DebugInfoBase debugInfoBase, DebugContext debugContext) {
        String fieldName = debugInfoBase.uniqueDebugString(debugFieldInfo.name());
        ResolvedJavaType valueType = debugFieldInfo.valueType();
        String valueTypeName = valueType.toJavaName();
        int fieldSize = debugFieldInfo.size();
        int fieldoffset = debugFieldInfo.offset();
        boolean fieldIsEmbedded = debugFieldInfo.isEmbedded();
        int fieldModifiers = debugFieldInfo.modifiers();
        if (debugContext.isLogEnabled()) {
            debugContext.log("typename %s adding %s field %s type %s%s size %s at offset 0x%x%n",
                            typeName, memberModifiers(fieldModifiers), fieldName, valueTypeName, (fieldIsEmbedded ? "(embedded)" : ""), fieldSize, fieldoffset);
        }
        TypeEntry valueTypeEntry = debugInfoBase.lookupTypeEntry(valueType);
        /*
         * n.b. the field file may differ from the owning class file when the field is a
         * substitution
         */
        FileEntry fileEntry = debugInfoBase.ensureFileEntry(debugFieldInfo);
        FieldEntry fieldEntry = new FieldEntry(fileEntry, fieldName, this, valueTypeEntry, fieldSize, fieldoffset, fieldIsEmbedded, fieldModifiers);
        fields.add(fieldEntry);
        return fieldEntry;
    }

    String memberModifiers(int modifiers) {
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
}
