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

import java.util.ArrayList;
import java.util.stream.Stream;

public class ForeignTypeEntry extends ClassEntry {
    private final static int FLAG_WORD = 1 << 0;
    private final static int FLAG_POINTER = 1 << 1;
    private final static int FLAG_INTEGRAL = 1 << 2;
    private final static int FLAG_SIGNED = 1 << 3;
    private final static int FLAG_FLOAT = 1 << 4;
    String typedefName;
    ArrayList<ClassEntry> parents;
    int flags;
    public ForeignTypeEntry(String className, FileEntry fileEntry, int size) {
        super(className, fileEntry, size);
        typedefName = null;
        parents = null;
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
        } else if (debugForeignTypeInfo.isPointer()) {
            flags = FLAG_POINTER;
        } else if (debugForeignTypeInfo.isIntegral()) {
            flags = FLAG_INTEGRAL;
        } else if (debugForeignTypeInfo.isFloat()) {
            flags = FLAG_FLOAT;
        }
        if (debugForeignTypeInfo.isSigned()) {
            flags |= FLAG_SIGNED;
        }
        debugContext.log("foreign type %s flags 0x%x", typeName, flags);
    }

    public String getTypedefName() {
        return typedefName;
    }

    private void addParent(ForeignTypeEntry parent) {
        if (parents == null) {
            parents = new ArrayList<>();
        }
        if (!parents.contains(parent)) {
            parents.add(parent);
        }
    }

    public Stream<ClassEntry> parentStream() {
        if (parents == null) {
            return Stream.empty();
        } else {
            return parents.stream();
        }
    }

    public boolean isWord() {
        return (flags & FLAG_WORD) != 0;
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
        if (parentEntry instanceof InterfaceClassEntry) {
            super.processInterface(interfaceType, debugInfoBase, debugContext);
        } else {
            assert parentEntry instanceof ForeignTypeEntry : "was only expecting an interface or a foreign type";
            // track this type as a 'parent' type for the foreign type
            // we can use this parent relationship to relate any underlying foreign
            // primitive, pointer or struct types that this type points to, whether that
            // is via a typedef chain or, in the latter case (of a struct), a super chain.
            addParent((ForeignTypeEntry) parentEntry);
        }
    }
}
