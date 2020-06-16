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
package com.oracle.svm.core.code;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArrays;

/**
 * Provides access to {@link UntetheredCodeInfo} objects. All methods in here should only be called
 * from uninterruptible code as the GC could free the {@link UntetheredCodeInfo} otherwise.
 */
public final class UntetheredCodeInfoAccess {
    private UntetheredCodeInfoAccess() {
    }

    /**
     * Try to avoid using this method. It provides direct access to the tether object without any
     * verification. The return type of this method is Object instead of CodeInfoTether to avoid
     * type casts that could fail if a GC is in progress.
     */
    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.")
    public static Object getTetherUnsafe(UntetheredCodeInfo info) {
        return getObjectFieldUnsafe(info, CodeInfoImpl.TETHER_OBJFIELD);
    }

    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.", callerMustBe = true)
    public static CodePointer getCodeStart(UntetheredCodeInfo info) {
        return cast(info).getCodeStart();
    }

    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.", callerMustBe = true)
    public static CodePointer getCodeEnd(UntetheredCodeInfo info) {
        CodeInfoImpl codeInfo = cast(info);
        return (CodePointer) ((UnsignedWord) codeInfo.getCodeStart()).add(codeInfo.getCodeSize());
    }

    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.", callerMustBe = true)
    public static UnsignedWord getCodeSize(UntetheredCodeInfo info) {
        return cast(info).getCodeSize();
    }

    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.", callerMustBe = true)
    public static int getTier(UntetheredCodeInfo info) {
        return cast(info).getTier();
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.", callerMustBe = true)
    public static <T> T getObjectFieldUnsafe(UntetheredCodeInfo info, int index) {
        return (T) NonmovableArrays.getObject(cast(info).getObjectFields(), index);
    }

    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.", callerMustBe = true)
    private static CodeInfoImpl cast(UntetheredCodeInfo info) {
        assert info.isNonNull();
        return (CodeInfoImpl) info;
    }
}
