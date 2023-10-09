/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugForeignTypeInfo;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;

public class ForeignTypeEntry extends ClassEntry {
    private static final int FLAG_WORD = 1 << 0;
    private static final int FLAG_STRUCT = 1 << 1;
    private static final int FLAG_POINTER = 1 << 2;
    private static final int FLAG_INTEGRAL = 1 << 3;
    private static final int FLAG_SIGNED = 1 << 4;
    private static final int FLAG_FLOAT = 1 << 5;
    private String typedefName;
    private ForeignTypeEntry parent;
    private TypeEntry pointerTo;
    private int flags;

    public ForeignTypeEntry(String className, FileEntry fileEntry, int size) {
        super(className, fileEntry, size);
        typedefName = null;
        parent = null;
        pointerTo = null;
        flags = 0;
    }

    @Override
    public DebugInfoProvider.DebugTypeInfo.DebugTypeKind typeKind() {
        return DebugInfoProvider.DebugTypeInfo.DebugTypeKind.FOREIGN;
    }

    @Override
    public void addDebugInfo(DebugInfoBase debugInfoBase, DebugTypeInfo debugTypeInfo, DebugContext debugContext) {
        assert debugTypeInfo instanceof DebugForeignTypeInfo;
        super.addDebugInfo(debugInfoBase, debugTypeInfo, debugContext);
        DebugForeignTypeInfo debugForeignTypeInfo = (DebugForeignTypeInfo) debugTypeInfo;
        this.typedefName = debugForeignTypeInfo.typedefName();
        if (debugForeignTypeInfo.isWord()) {
            flags = FLAG_WORD;
        } else if (debugForeignTypeInfo.isStruct()) {
            flags = FLAG_STRUCT;
            ResolvedJavaType parentIdType = debugForeignTypeInfo.parent();
            if (parentIdType != null) {
                TypeEntry parentTypeEntry = debugInfoBase.lookupClassEntry(parentIdType);
                assert parentTypeEntry instanceof ForeignTypeEntry;
                parent = (ForeignTypeEntry) parentTypeEntry;
            }
        } else if (debugForeignTypeInfo.isPointer()) {
            flags = FLAG_POINTER;
            ResolvedJavaType referent = debugForeignTypeInfo.pointerTo();
            if (referent != null) {
                pointerTo = debugInfoBase.lookupTypeEntry(referent);
            }
        } else if (debugForeignTypeInfo.isIntegral()) {
            flags = FLAG_INTEGRAL;
        } else if (debugForeignTypeInfo.isFloat()) {
            flags = FLAG_FLOAT;
        }
        if (debugForeignTypeInfo.isSigned()) {
            flags |= FLAG_SIGNED;
        }
        if (debugContext.isLogEnabled()) {
            if (isPointer() && pointerTo != null) {
                debugContext.log("foreign type %s flags 0x%x referent %s ", typeName, flags, pointerTo.getTypeName());
            } else {
                debugContext.log("foreign type %s flags 0x%x", typeName, flags);
            }
        }
    }

    public String getTypedefName() {
        return typedefName;
    }

    public ForeignTypeEntry getParent() {
        return parent;
    }

    public TypeEntry getPointerTo() {
        return pointerTo;
    }

    public boolean isWord() {
        return (flags & FLAG_WORD) != 0;
    }

    public boolean isStruct() {
        return (flags & FLAG_STRUCT) != 0;
    }

    public boolean isPointer() {
        return (flags & FLAG_POINTER) != 0;
    }

    public boolean isIntegral() {
        return (flags & FLAG_INTEGRAL) != 0;
    }

    public boolean isSigned() {
        return (flags & FLAG_SIGNED) != 0;
    }

    public boolean isFloat() {
        return (flags & FLAG_FLOAT) != 0;
    }

    @Override
    protected void processInterface(ResolvedJavaType interfaceType, DebugInfoBase debugInfoBase, DebugContext debugContext) {
        ClassEntry parentEntry = debugInfoBase.lookupClassEntry(interfaceType);
        // don't model the interface relationship when the Java interface actually identifies a
        // foreign type
        if (parentEntry instanceof InterfaceClassEntry) {
            super.processInterface(interfaceType, debugInfoBase, debugContext);
        } else {
            assert parentEntry instanceof ForeignTypeEntry : "was only expecting an interface or a foreign type";
        }
    }
}
