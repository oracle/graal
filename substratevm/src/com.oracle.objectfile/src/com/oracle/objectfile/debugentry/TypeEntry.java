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

import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind;
import jdk.graal.compiler.debug.DebugContext;

import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind.ARRAY;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind.ENUM;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind.FOREIGN;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind.HEADER;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind.INSTANCE;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind.INTERFACE;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind.PRIMITIVE;

public abstract class TypeEntry {
    /**
     * The name of this type.
     */
    protected final String typeName;

    /**
     * The offset of the java.lang.Class instance for this class in the image heap or -1 if no such
     * object exists.
     */
    private long classOffset;

    /**
     * The size of an occurrence of this type in bytes.
     */
    protected final int size;

    protected TypeEntry(String typeName, int size) {
        this.typeName = typeName;
        this.size = size;
        this.classOffset = -1;
    }

    public long getClassOffset() {
        return classOffset;
    }

    public int getSize() {
        return size;
    }

    public String getTypeName() {
        return typeName;
    }

    public abstract DebugTypeKind typeKind();

    public boolean isPrimitive() {
        return typeKind() == PRIMITIVE;
    }

    public boolean isHeader() {
        return typeKind() == HEADER;
    }

    public boolean isArray() {
        return typeKind() == ARRAY;
    }

    public boolean isInstance() {
        return typeKind() == INSTANCE;
    }

    public boolean isInterface() {
        return typeKind() == INTERFACE;
    }

    public boolean isEnum() {
        return typeKind() == ENUM;
    }

    public boolean isForeign() {
        return typeKind() == FOREIGN;
    }

    /**
     * Test whether this entry is a class type, either an instance class, an interface type, an enum
     * type or a foreign type. The test excludes primitive and array types and the header type.
     *
     * n.b. Foreign types are considered to be class types because they appear like interfaces or
     * classes in the Java source and hence need to be modeled by a ClassEntry which can track
     * properties of the java type. This also allows them to be decorated with properties that
     * record details of the generated debug info. When it comes to encoding the model type as DWARF
     * or PECOFF method {@link #isForeign()} may need to be called in order to allow foreign types
     * ot be special cased.
     *
     * @return true if this entry is a class type otherwise false.
     */
    public boolean isClass() {
        return isInstance() || isInterface() || isEnum() || isForeign();
    }

    public boolean isStructure() {
        return isClass() || isHeader();
    }

    public void addDebugInfo(@SuppressWarnings("unused") DebugInfoBase debugInfoBase, DebugTypeInfo debugTypeInfo, @SuppressWarnings("unused") DebugContext debugContext) {
        /* Record the location of the Class instance in the heap if there is one */
        this.classOffset = debugTypeInfo.classOffset();
    }
}
