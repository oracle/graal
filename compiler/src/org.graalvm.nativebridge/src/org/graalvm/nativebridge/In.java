/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures an array parameter as an in-parameter. For an in-parameter, the array value is copied
 * over the boundary into a called method. The {@link In} is the default behavior. It's needed only
 * in combination with {@link Out} for in-out parameters.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface In {

    /**
     * Copy only a part of the array starting at the offset given by the
     * {@code arrayOffsetParameter} method parameter. By default, the whole array is copied. The
     * {@code arrayOffsetParameter} can be used to improve the performance and copy only a part of
     * the array over the boundary.
     */
    String arrayOffsetParameter() default "";

    /**
     * Limits copying only to many of the elements given by the {@code arrayLengthParameter}
     * parameter. By default, the whole array is copied. The {@code arrayLengthParameter} can be
     * used to improve the performance and copy only a part of the array over the boundary.
     */
    String arrayLengthParameter() default "";
}
