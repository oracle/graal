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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import com.oracle.svm.hosted.c.info.SizableInfo.SignednessValue;

/**
 * Information of a C Struct bitfield.
 */
public class StructBitfieldInfo extends ElementInfo {

    private final PropertyInfo<Integer> byteOffset;
    private final PropertyInfo<Integer> startBit;
    private final PropertyInfo<Integer> endBit;
    private final PropertyInfo<SignednessValue> signednessInfo;

    public StructBitfieldInfo(String name) {
        super(name);
        this.byteOffset = adoptChild(new PropertyInfo<Integer>("byteOffset"));
        this.startBit = adoptChild(new PropertyInfo<Integer>("startBit"));
        this.endBit = adoptChild(new PropertyInfo<Integer>("endBit"));
        this.signednessInfo = adoptChild(new PropertyInfo<SignednessValue>("signedness"));
    }

    public PropertyInfo<Integer> getByteOffsetInfo() {
        return byteOffset;
    }

    public PropertyInfo<Integer> getStartBitInfo() {
        return startBit;
    }

    public PropertyInfo<Integer> getEndBitInfo() {
        return endBit;
    }

    public PropertyInfo<SignednessValue> getSignednessInfo() {
        return signednessInfo;
    }

    @Override
    public void accept(InfoTreeVisitor visitor) {
        visitor.visitStructBitfieldInfo(this);
    }

    @Override
    public Object getAnnotatedElement() {
        /* Use the first accessor method, that is the most helpful in error messages. */
        for (ElementInfo child : getChildren()) {
            if (child instanceof AccessorInfo) {
                return child.getAnnotatedElement();
            }
        }
        throw shouldNotReachHere("must have at least one accessor method that defined the field");
    }

    public boolean isUnsigned() {
        return getSignednessInfo().getProperty() == SignednessValue.UNSIGNED;
    }
}
