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
package com.oracle.svm.core.c;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.PointerBase;

public final class CGlobalDataImpl<T extends PointerBase> extends CGlobalData<T> {
    /**
     * The name of the symbol to create for this data (or null to create no symbol), or if the other
     * fields are null, the name of the symbol to be referenced by this instance.
     */
    public final String symbolName;

    public final Supplier<byte[]> bytesSupplier;
    public final IntSupplier sizeSupplier;
    public final boolean nonConstant;

    @Platforms(Platform.HOSTED_ONLY.class)
    CGlobalDataImpl(String symbolName, Supplier<byte[]> bytesSupplier) {
        this(symbolName, bytesSupplier, null, false); // pre-existing data
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    CGlobalDataImpl(String symbolName, IntSupplier sizeSupplier) {
        this(symbolName, null, sizeSupplier, false); // zero-initialized data
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    CGlobalDataImpl(String symbolName) {
        this(symbolName, null, null, false); // reference to symbol
    }

    /**
     * nonConstant parameter marks whether object have to be used as a compile-time constant. If
     * nonConstant is 'false', the symbolName should be known at compile time.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    CGlobalDataImpl(String symbolName, boolean nonConstant) {
        this(symbolName, null, null, nonConstant);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private CGlobalDataImpl(String symbolName, Supplier<byte[]> bytesSupplier, IntSupplier sizeSupplier, boolean nonConstant) {
        assert !(bytesSupplier != null && sizeSupplier != null);
        this.symbolName = symbolName;
        this.bytesSupplier = bytesSupplier;
        this.sizeSupplier = sizeSupplier;
        this.nonConstant = nonConstant;
    }

    @Override
    public String toString() {
        return "CGlobalData[symbol=" + symbolName + "]";
    }
}
