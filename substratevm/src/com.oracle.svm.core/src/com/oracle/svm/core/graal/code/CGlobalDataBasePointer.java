/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.c.CGlobalData;

/** Placeholder for the base address of {@link CGlobalData} memory during the image build. */
public final class CGlobalDataBasePointer implements PointerBase, RelocatedPointer {
    public static final CGlobalDataBasePointer INSTANCE = new CGlobalDataBasePointer();

    private CGlobalDataBasePointer() {
    }

    private static RuntimeException mustNotBeCalledHosted() {
        throw shouldNotReachHere("must not be called in hosted mode");
    }

    @Override
    public boolean equal(ComparableWord val) {
        throw mustNotBeCalledHosted();
    }

    @Override
    public boolean notEqual(ComparableWord val) {
        throw mustNotBeCalledHosted();
    }

    @Override
    public long rawValue() {
        throw mustNotBeCalledHosted();
    }

    @Override
    public boolean isNull() {
        throw mustNotBeCalledHosted();
    }

    @Override
    public boolean isNonNull() {
        throw mustNotBeCalledHosted();
    }
}
