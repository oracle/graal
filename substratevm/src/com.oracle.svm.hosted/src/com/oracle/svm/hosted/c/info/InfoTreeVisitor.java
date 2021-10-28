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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.oracle.svm.core.util.UserError;

public abstract class InfoTreeVisitor {

    /**
     * Compares the the Class name, the name and the result of
     * {@link ElementInfo#getAnnotatedElement()} to get a stable order for sorting the children of
     * an {@link ElementInfo} node.
     *
     * Note: We cannot use the {@link ElementInfo#getUniqueID()} method because it only looks at the
     * parent path, without looking at any children, so for intermediate nodes in the tree there may
     * be ID conflicts. This is not a problem when storing the tree as the children are stored as an
     * {@link ArrayList}. It is also not a problem when writing the tree out as only the leaf nodes
     * are written. However, to get a stable processing order we need extra information.
     */
    static final Comparator<ElementInfo> elementInfoComparator;
    static {
        /* Defining the comparator chain on multiple lines requires less type annotations. */
        Comparator<ElementInfo> classNameComparator = Comparator.comparing(e -> e.getClass().getName());
        Comparator<ElementInfo> nameComparator = classNameComparator.thenComparing(e -> e.getName());
        elementInfoComparator = nameComparator.thenComparing(e -> e.getAnnotatedElement().toString());
    }

    protected final void processChildren(ElementInfo info) {
        List<ElementInfo> children = info.getChildren();
        /*
         * Sort the children before processing. Although storing the children already sorted is
         * possible, that is not necessary. Sorting them in the visitor is enough to get a stable
         * processing order.
         */
        children.sort(elementInfoComparator);
        for (ElementInfo child : children) {
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

    protected void visitRawPointerToInfo(RawPointerToInfo info) {
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
