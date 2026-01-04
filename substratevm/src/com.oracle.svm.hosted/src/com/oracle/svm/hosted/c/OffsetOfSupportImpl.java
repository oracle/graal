/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c;

import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.c.struct.OffsetOf;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.c.info.StructInfo;

import jdk.vm.ci.meta.ResolvedJavaType;

public final class OffsetOfSupportImpl implements OffsetOf.Support {
    private final NativeLibraries nativeLibraries;

    public OffsetOfSupportImpl(NativeLibraries nativeLibraries) {
        this.nativeLibraries = nativeLibraries;
    }

    @Override
    public int offsetOf(Class<? extends PointerBase> clazz, String fieldName) {
        ResolvedJavaType type = nativeLibraries.getMetaAccess().lookupJavaType(clazz);
        ElementInfo typeInfo = nativeLibraries.findElementInfo(type);
        VMError.guarantee(typeInfo instanceof StructInfo, "Class parameter %s of call to %s is not an annotated C struct", type, SizeOf.class.getSimpleName());
        StructInfo structInfo = (StructInfo) typeInfo;
        for (ElementInfo element : structInfo.getChildren()) {
            if (element instanceof StructFieldInfo field) {
                if (field.getName().equals(fieldName)) {
                    return field.getOffsetInfo().getProperty();
                }
            }
        }
        throw VMError.shouldNotReachHere("Field %s of C struct %s was not found", fieldName, type);
    }
}
