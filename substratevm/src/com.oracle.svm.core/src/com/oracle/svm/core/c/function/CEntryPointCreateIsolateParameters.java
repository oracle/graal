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

import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
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
}
