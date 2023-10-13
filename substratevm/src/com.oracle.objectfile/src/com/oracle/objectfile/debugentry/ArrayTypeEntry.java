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

import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugArrayTypeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;

public class ArrayTypeEntry extends StructureTypeEntry {
    private TypeEntry elementType;
    private int baseSize;
    private int lengthOffset;

    public ArrayTypeEntry(String typeName, int size) {
        super(typeName, size);
    }

    @Override
    public DebugTypeKind typeKind() {
        return DebugTypeKind.ARRAY;
    }

    @Override
    public void addDebugInfo(DebugInfoBase debugInfoBase, DebugTypeInfo debugTypeInfo, DebugContext debugContext) {
        super.addDebugInfo(debugInfoBase, debugTypeInfo, debugContext);
        DebugArrayTypeInfo debugArrayTypeInfo = (DebugArrayTypeInfo) debugTypeInfo;
        ResolvedJavaType eltType = debugArrayTypeInfo.elementType();
        this.elementType = debugInfoBase.lookupTypeEntry(eltType);
        this.baseSize = debugArrayTypeInfo.baseSize();
        this.lengthOffset = debugArrayTypeInfo.lengthOffset();
        /* Add details of fields and field types */
        debugArrayTypeInfo.fieldInfoProvider().forEach(debugFieldInfo -> this.processField(debugFieldInfo, debugInfoBase, debugContext));
        if (debugContext.isLogEnabled()) {
            debugContext.log("typename %s element type %s base size %d length offset %d%n", typeName, this.elementType.getTypeName(), baseSize, lengthOffset);
        }
    }

    public TypeEntry getElementType() {
        return elementType;
    }

    public String getLoaderId() {
        TypeEntry type = elementType;
        while (type.isArray()) {
            type = ((ArrayTypeEntry) type).elementType;
        }
        if (type.isClass()) {
            return ((ClassEntry) type).getLoaderId();
        }
        return "";
    }
}
