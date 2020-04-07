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

import org.graalvm.word.LocationIdentity;

/**
 * Information of a C Struct field.
 */
public class StructFieldInfo extends SizableInfo {

    private final PropertyInfo<Integer> offset;
    private LocationIdentity locationIdentity;

    public StructFieldInfo(String name, ElementKind kind) {
        super(name, kind);
        this.offset = adoptChild(new PropertyInfo<Integer>("offset"));
    }

    public PropertyInfo<Integer> getOffsetInfo() {
        return offset;
    }

    public AccessorInfo getAnyAccessorInfo() {
        return getAccessorInfo(false);
    }

    public AccessorInfo getAccessorInfoWithSize() {
        return getAccessorInfo(true);
    }

    private AccessorInfo getAccessorInfo(boolean withSize) {
        for (ElementInfo child : getChildren()) {
            if (child instanceof AccessorInfo) {
                if (withSize) {
                    AccessorInfo.AccessorKind kind = ((AccessorInfo) child).getAccessorKind();
                    if (kind != AccessorInfo.AccessorKind.GETTER && kind != AccessorInfo.AccessorKind.SETTER) {
                        continue;
                    }
                }
                return (AccessorInfo) child;
            }
        }

        throw shouldNotReachHere("must have at least one accessor method that defines the field with a type for: " + this);
    }

    @Override
    public void accept(InfoTreeVisitor visitor) {
        visitor.visitStructFieldInfo(this);
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

    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    public void setLocationIdentity(LocationIdentity locationIdentity) {
        assert this.locationIdentity == null;
        this.locationIdentity = locationIdentity;
    }
}
