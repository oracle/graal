/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c.struct;

import org.graalvm.nativeimage.c.struct.UniqueLocationIdentity;
import org.graalvm.word.LocationIdentity;

/**
 * The {@link LocationIdentity} implementation used for C interface accessors annotated with
 * {@link UniqueLocationIdentity} or C interface accessors that have no specified location identity.
 */
public class CInterfaceLocationIdentity extends LocationIdentity {

    /**
     * The {@link LocationIdentity} used for memory accesses of structures. An accessor method can
     * specify a custom {@link LocationIdentity} as the last parameter, or can be annotate with
     * {@link UniqueLocationIdentity} to get a unique location identity assigned automatically.
     * Otherwise, this {@link LocationIdentity} is used.
     */
    public static final LocationIdentity DEFAULT_LOCATION_IDENTITY = new CInterfaceLocationIdentity("C Interface Field");

    private final String name;

    public CInterfaceLocationIdentity(String name) {
        this.name = name;
    }

    @Override
    public boolean isImmutable() {
        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}
