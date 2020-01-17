/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.code;

import java.util.function.Supplier;

import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.CGlobalDataImpl;
import com.oracle.svm.core.util.VMError;

public final class CGlobalDataInfo {
    public static final String CGLOBALDATA_BASE_SYMBOL_NAME = "__svm_cglobaldata_base";
    public static final CGlobalData<Pointer> CGLOBALDATA_RUNTIME_BASE_ADDRESS = CGlobalDataFactory.forSymbol(CGLOBALDATA_BASE_SYMBOL_NAME);

    private final CGlobalDataImpl<?> data;
    private final boolean isSymbolReference;

    private int offset = -1;

    /** Cache until writing the image in case the {@link Supplier} is costly or has side-effects. */
    @Platforms(HOSTED_ONLY.class) private byte[] bytes;

    public CGlobalDataInfo(CGlobalDataImpl<?> data) {
        assert data != null;
        this.data = data;
        this.isSymbolReference = (data.bytesSupplier == null && data.sizeSupplier == null);
        assert !this.isSymbolReference || data.symbolName != null;
    }

    public CGlobalDataImpl<?> getData() {
        return data;
    }

    @SuppressWarnings("hiding")
    public void assign(int offset, byte[] bytes) {
        assert this.offset == -1 && this.bytes == null : "already initialized";
        assert offset >= 0;
        this.offset = offset;
        this.bytes = bytes;
    }

    public int getOffset() {
        VMError.guarantee(offset >= 0, "Offset has not been initialized");
        return offset;
    }

    public boolean isSymbolReference() {
        return isSymbolReference;
    }

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return data.toString();
    }
}
