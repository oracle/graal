/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.sdk.staging.hosted.layeredimage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Denotes a field which requires a {@link LayeredFieldValueTransformer} when building layered
 * images. Note this annotation is only relevant for layered builds and it is legal for a field to
 * have both this annotation and {@code UnknownObjectField}/{@code UnknownPrimitiveField}. For
 * layered builds, this annotation will take priority and the Unknown field annotation will be
 * ignored.
 * <p>
 * See {@link LayeredFieldValueTransformer} for an explanation of the transformation behavior.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Platforms(Platform.HOSTED_ONLY.class)
public @interface LayeredFieldValue {

    Class<? extends LayeredFieldValueTransformer<?>> transformer();
}
