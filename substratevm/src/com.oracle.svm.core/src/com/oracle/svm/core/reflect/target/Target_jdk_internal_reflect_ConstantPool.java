/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect.target;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * All usages of ConstantPool are substituted to go through
 * {@link com.oracle.svm.core.reflect.RuntimeMetadataDecoder.MetadataAccessor}.
 * <p>
 * In Native Image, the constant pool is not used. However, in the context of Layered Image, the
 * constant pool needs to be able to provide the layer number it is associated with. This is because
 * the {@link com.oracle.svm.core.reflect.RuntimeMetadataDecoder.MetadataAccessor} needs a layer
 * number for retrieving the information in the correct layer and in some cases, only the constant
 * pool can provide this information. The constant pool is only used with Layered Image, and only to
 * provide a layer number.
 */
@TargetClass(className = "jdk.internal.reflect.ConstantPool")
@Substitute
public final class Target_jdk_internal_reflect_ConstantPool {
    /**
     * The layer number associated with this constant pool.
     */
    private final int layerId;

    public Target_jdk_internal_reflect_ConstantPool(int layerId) {
        this.layerId = layerId;
    }

    public int getLayerId() {
        return layerId;
    }
}
