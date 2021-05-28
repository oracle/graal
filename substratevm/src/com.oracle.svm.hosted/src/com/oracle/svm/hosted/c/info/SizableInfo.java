/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.info;

import org.graalvm.nativeimage.c.constant.CConstant;

public abstract class SizableInfo extends ElementInfo {

    /**
     * Type information, also for implicit or explicit signedness information.
     */
    public enum ElementKind {
        /**
         * Integer value types. Signed/unsigned distinction is possible on this type.
         */
        INTEGER,
        /**
         * Pointer value types. Always unsigned.
         */
        POINTER,
        /**
         * Floating point value types. No signedness.
         */
        FLOAT,
        /**
         * {@link CConstant} that is a C String, which is automatically converted to a Java String
         * constant.
         */
        STRING,
        /**
         * {@link CConstant} that is a C String, which is automatically converted to a Java byte[]
         * constant.
         */
        BYTEARRAY,
        /**
         * Java object, can be stored in {@link com.oracle.svm.core.c.struct.PinnedObjectField} of
         * {@link org.graalvm.nativeimage.c.struct.RawStructure} only.
         */
        OBJECT,
        /**
         * Placeholder when type is not known or does not matter.
         */
        UNKNOWN,
    }

    /**
     * Possible values for the {@link SizableInfo#getSignednessInfo()}.
     */
    public enum SignednessValue {
        SIGNED,
        UNSIGNED,
    }

    private final ElementKind kind;
    private final PropertyInfo<Integer> sizeInfo;
    private final PropertyInfo<SignednessValue> signednessInfo;

    public SizableInfo(String name, ElementKind kind) {
        super(name);
        this.kind = kind;
        this.sizeInfo = adoptChild(new PropertyInfo<Integer>("size"));
        if (kind == ElementKind.INTEGER) {
            this.signednessInfo = adoptChild(new PropertyInfo<SignednessValue>("signedness"));
        } else {
            this.signednessInfo = null;
        }
    }

    public final ElementKind getKind() {
        return kind;
    }

    public final PropertyInfo<Integer> getSizeInfo() {
        return sizeInfo;
    }

    public final PropertyInfo<SignednessValue> getSignednessInfo() {
        assert signednessInfo != null;
        return signednessInfo;
    }

    public final boolean isUnsigned() {
        return getKind() == ElementKind.POINTER || (getKind() == ElementKind.INTEGER && getSignednessInfo().getProperty() == SignednessValue.UNSIGNED);
    }

    public final boolean isObject() {
        return getKind() == ElementKind.OBJECT;
    }
}
