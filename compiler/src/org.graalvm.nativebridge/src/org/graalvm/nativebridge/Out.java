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
 * Configures an array parameter as an out-parameter. For an out-parameter, the array value is
 * copied over the boundary from a called method. It may be combined with {@link In} for in-out
 * parameters. Example showing the configuration for
 * {@link java.io.OutputStream#write(byte[], int, int)}.
 *
 * <pre>
 * &#64;Override
 * public abstract int read(&#64;Out(arrayOffsetParameter = "off", arrayLengthParameter = "len", trimToResult = true) byte[] b, int off, int len);
 * </pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface Out {

    /**
     * Copy only a part of the array starting at offset given by the {@code arrayOffsetParameter}
     * method parameter. By default, the whole array is copied. The {@code arrayOffsetParameter} can
     * be used to improve the performance and copy only a part of the array over the boundary.
     */
    String arrayOffsetParameter() default "";

    /**
     * Limits copying only to many of the elements given by the {@code arrayLengthParameter}
     * parameter. By default, the whole array is copied. The {@code arrayLengthParameter} can be
     * used to improve the performance and copy only a part of the array over the boundary.
     */
    String arrayLengthParameter() default "";

    /**
     * Limits copying only to method result number of elements. It can be used to further limit the
     * number of copied elements in addition to {@link #arrayLengthParameter}. When used, it's still
     * good to specify {@link #arrayLengthParameter} as an upper bound to limit allocated array
     * size.
     */
    boolean trimToResult() default false;
}
