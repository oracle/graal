/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.BuildPhaseProvider.AfterHostedUniverse;
import com.oracle.svm.core.heap.UnknownPrimitiveField;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

public final class CIsolateData<T extends PointerBase> {
    @Platforms(Platform.HOSTED_ONLY.class) //
    private final String name;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final long size;

    @UnknownPrimitiveField(availability = AfterHostedUniverse.class) //
    private UnsignedWord offset;

    @Platforms(Platform.HOSTED_ONLY.class)
    CIsolateData(String name, long size) {
        this.name = name;
        this.size = size;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public T get() {
        return CIsolateDataStorage.singleton().get(this);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public long getSize() {
        return size;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public String getName() {
        return name;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord getOffset() {
        return offset;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setOffset(UnsignedWord offset) {
        assert this.offset == null;
        this.offset = offset;
    }
}
