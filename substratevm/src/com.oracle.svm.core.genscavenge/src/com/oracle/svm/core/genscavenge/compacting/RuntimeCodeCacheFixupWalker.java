/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.compacting;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.RuntimeCodeCache.CodeInfoVisitor;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.genscavenge.SerialGCOptions;

/** Before compaction, updates references from {@link CodeInfo} structures. */
public final class RuntimeCodeCacheFixupWalker implements CodeInfoVisitor {
    private final ObjectRefFixupVisitor visitor;

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeCodeCacheFixupWalker(ObjectRefFixupVisitor visitor) {
        assert SerialGCOptions.useCompactingOldGen();
        this.visitor = visitor;
    }

    @Override
    public boolean visitCode(CodeInfo codeInfo) {
        if (RuntimeCodeInfoAccess.areAllObjectsOnImageHeap(codeInfo)) {
            return true;
        }

        /*
         * Whether this CodeInfo remains valid or will be invalidated or freed during this GC, we
         * update all its references, including clearing those to objects that do not survive.
         */
        RuntimeCodeInfoAccess.walkStrongReferences(codeInfo, visitor);
        RuntimeCodeInfoAccess.walkWeakReferences(codeInfo, visitor);
        return true;
    }
}
