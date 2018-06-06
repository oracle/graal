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
package org.graalvm.nativeimage.c.struct;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

/**
 * A {@link CField} also annotated with this annotation gets a unique {@link LocationIdentity}
 * assigned, i.e., reads and writes do not interfere with reads and writes to any other field and
 * are optimized without regarding other fields. The user has to ensure that the field is not
 * accessed using any other location identity, e.g., it is not valid to access a field using methods
 * in {@link Pointer}.
 * <p>
 * It is not possible to influence which {@link LocationIdentity} is used, or to query the
 * {@link LocationIdentity}. If you need a field access with a particular {@link LocationIdentity},
 * do not use this annotation. Instead, add a {@link LocationIdentity} as the last parameter to the
 * accessor methods.
 * 
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface UniqueLocationIdentity {
}
