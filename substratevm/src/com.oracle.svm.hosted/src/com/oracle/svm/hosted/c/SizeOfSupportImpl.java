/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.nativeimage.impl.SizeOfSupport;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.SizableInfo;
import com.oracle.svm.hosted.c.info.StructInfo;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class SizeOfSupportImpl implements SizeOfSupport {
    private final NativeLibraries nativeLibraries;
    private final MetaAccessProvider metaAccess;

    public SizeOfSupportImpl(NativeLibraries nativeLibraries, MetaAccessProvider metaAccess) {
        this.nativeLibraries = nativeLibraries;
        this.metaAccess = metaAccess;
    }

    @Override
    public int sizeof(Class<? extends PointerBase> clazz) {
        ResolvedJavaType type = metaAccess.lookupJavaType(clazz);
        ElementInfo typeInfo = nativeLibraries.findElementInfo(type);
        if (typeInfo instanceof StructInfo && ((StructInfo) typeInfo).isIncomplete()) {
            throw UserError.abort("Class parameter " + type.toJavaName(true) + " of call to " + SizeOf.class.getSimpleName() + " is an incomplete structure, so no size is available");
        } else if (typeInfo instanceof SizableInfo) {
            return ((SizableInfo) typeInfo).getSizeInfo().getProperty();
        } else {
            throw UserError.abort("Class parameter " + type.toJavaName(true) + " of call to " + SizeOf.class.getSimpleName() + " is not an annotated C interface type");
        }
    }
}
