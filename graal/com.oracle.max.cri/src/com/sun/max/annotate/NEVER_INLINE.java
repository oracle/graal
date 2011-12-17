/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.annotate;

import java.lang.annotation.*;

/**
 * Every thus annotated method is never to be inlined by the compiler.
 *
 * This annotation exists primarily for annotating methods that <b>must never</b> be inlined
 * for semantic reasons. Typically, this is to ensure that a separate activation frame is
 * always used for a call to the method.
 *
 * This annotation can also be applied to a class in which is equivalent to applying
 * it to all the methods in the class <b>except</b> for those explicitly annotated with
 * {@link INLINE}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface NEVER_INLINE {

    /**
     * Documents the reason why the annotated code must notbe inlined.
     */
    String value() default "";
}
