/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.util.DirectAnnotationAccess;

/**
 * Annotation for types whose methods are synthetic methods for lambda invocations, and ignored for
 * certain user-visible stack traces such as exception stack traces. All methods in the annotated
 * type have the same level of visibility. This is a type-level mirror of the internal JDK
 * annotation {@code LambdaForm.Hidden}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LambdaFormHiddenMethod {

    @LambdaFormHiddenMethod()
    class Holder {

        /** Instance of the annotation, useful when the annotation is manually injected. */
        public static final LambdaFormHiddenMethod INSTANCE = DirectAnnotationAccess.getAnnotation(Holder.class, LambdaFormHiddenMethod.class);

        /**
         * Array that contains only the instance of the annotation, useful when the annotation is
         * manually injected.
         */
        public static final Annotation[] ARRAY = new Annotation[]{INSTANCE};
    }
}
