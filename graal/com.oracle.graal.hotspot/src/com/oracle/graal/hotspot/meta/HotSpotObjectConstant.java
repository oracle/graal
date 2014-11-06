/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.meta;

import com.oracle.graal.api.meta.*;

/**
 * Represents a constant non-{@code null} object reference, within the compiler and across the
 * compiler/runtime interface.
 */
public interface HotSpotObjectConstant extends JavaValue, HotSpotConstant, VMConstant, Remote {

    JavaConstant compress();

    JavaConstant uncompress();

    /**
     * Gets the result of {@link Class#getClassLoader()} for the {@link Class} object represented by
     * this constant.
     *
     * @return {@code null} if this constant does not represent a {@link Class} object
     */
    @PureFunction
    JavaConstant getClassLoader();

    /**
     * Gets the {@linkplain System#identityHashCode(Object) identity} has code for the object
     * represented by this constant.
     */
    @PureFunction
    int getIdentityHashCode();
}
