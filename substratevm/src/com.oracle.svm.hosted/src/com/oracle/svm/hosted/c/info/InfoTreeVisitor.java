/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.info;

import com.oracle.svm.core.util.UserError;

public abstract class InfoTreeVisitor {

    protected final void processChildren(ElementInfo info) {
        for (ElementInfo child : info.getChildren()) {
            try {
                child.accept(this);
            } catch (NumberFormatException e) {
                throw UserError.abort("Missing CAP cache value for: %s", child.getUniqueID());
            }
        }
    }

    protected void visitNativeCodeInfo(NativeCodeInfo info) {
        processChildren(info);
    }

    protected void visitStructInfo(StructInfo info) {
        processChildren(info);
    }

    protected void visitRawStructureInfo(RawStructureInfo info) {
        processChildren(info);
    }

    protected void visitStructFieldInfo(StructFieldInfo info) {
        processChildren(info);
    }

    protected void visitStructBitfieldInfo(StructBitfieldInfo info) {
        processChildren(info);
    }

    protected void visitConstantInfo(ConstantInfo info) {
        processChildren(info);
    }

    protected void visitPointerToInfo(PointerToInfo info) {
        processChildren(info);
    }

    protected void visitAccessorInfo(AccessorInfo info) {
        processChildren(info);
    }

    protected void visitElementPropertyInfo(PropertyInfo<?> info) {
        processChildren(info);
    }

    protected void visitEnumInfo(EnumInfo info) {
        processChildren(info);
    }

    protected void visitEnumConstantInfo(EnumConstantInfo info) {
        processChildren(info);
    }

    protected void visitEnumValueInfo(EnumValueInfo info) {
        processChildren(info);
    }

    protected void visitEnumLookupInfo(EnumLookupInfo info) {
        processChildren(info);
    }
}
