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
package com.oracle.svm.core;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.PointerBase;

@RawStructure
public interface IsolateArguments extends PointerBase {

    @RawField
    void setArgc(int argc);

    @RawField
    int getArgc();

    @RawField
    void setArgv(CCharPointerPointer argv);

    @RawField
    CCharPointerPointer getArgv();

    /**
     * All argument values are stored as 8 byte values, regardless of their actual type. Therefore
     * {@code CLongPointer} and {@code long} are used if arbitrary option types are handled.
     */
    @RawField
    void setParsedArgs(CLongPointer ptr);

    @RawField
    CLongPointer getParsedArgs();

    @RawField
    void setProtectionKey(int pkey);

    @RawField
    int getProtectionKey();

    @RawField
    void setIsCompilationIsolate(boolean value);

    @RawField
    boolean getIsCompilationIsolate();

}
