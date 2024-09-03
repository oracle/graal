/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c.function;

import org.graalvm.nativeimage.Isolates.CreateIsolateParameters;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

/**
 * Parameters for the creation of an isolate.
 */
@CStruct("graal_create_isolate_params_t")
public interface CEntryPointCreateIsolateParameters extends PointerBase {
    @CField("version")
    int version();

    @CField("version")
    void setVersion(int version);

    /* fields below: version 1 */

    @CField("reserved_address_space_size")
    UnsignedWord reservedSpaceSize();

    @CField("reserved_address_space_size")
    void setReservedSpaceSize(UnsignedWord reservedSpaceSize);

    /* fields below: version 2 */

    @CField("auxiliary_image_path")
    CCharPointer auxiliaryImagePath();

    @CField("auxiliary_image_path")
    void setAuxiliaryImagePath(CCharPointer filePath);

    @CField("auxiliary_image_reserved_space_size")
    UnsignedWord auxiliaryImageReservedSpaceSize();

    @CField("auxiliary_image_reserved_space_size")
    void setAuxiliaryImageReservedSpaceSize(UnsignedWord auxImageReservedSize);

    /* fields below: version 3 */

    @CField("pkey")
    void setProtectionKey(int key);

    @CField("pkey")
    int protectionKey();

    /**
     * C arguments passed from the C main function into the isolate creation. These fields are not
     * public API, therefore they are named "reserved" in the C header files, and they are not
     * listed in the {@link CreateIsolateParameters} Java API to create isolates.
     */

    @CField("_reserved_1")
    int getArgc();

    @CField("_reserved_1")
    void setArgc(int value);

    @CField("_reserved_2")
    CCharPointerPointer getArgv();

    @CField("_reserved_2")
    void setArgv(CCharPointerPointer value);

    /* fields below: version 4 */
    @CField("_reserved_3")
    boolean getIgnoreUnrecognizedArguments();

    @CField("_reserved_3")
    void setIgnoreUnrecognizedArguments(boolean value);

    @CField("_reserved_4")
    boolean getExitWhenArgumentParsingFails();

    @CField("_reserved_4")
    void setExitWhenArgumentParsingFails(boolean value);

    /* fields below: version 5 */
    @CField("_reserved_5")
    boolean getIsCompilationIsolate();

    @CField("_reserved_5")
    void setIsCompilationIsolate(boolean value);
}
