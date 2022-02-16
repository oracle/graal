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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures how a method annotation is handled by the native bridge processor. By default, the
 * method annotation is used for marshaller lookup. The {@link AnnotationAction} can be used to
 * ignore the annotation or to copy it into a generated class.
 */
@Repeatable(AnnotationActionRepeated.class)
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AnnotationAction {

    Class<? extends Annotation> value();

    Action action() default Action.LOOKUP_MARSHALLER;

    /**
     * Action determining how the annotation is handled.
     */
    enum Action {
        /**
         * Native bridge processor ignores the configured annotation.
         */
        IGNORE,
        /**
         * Native bridge processor copies the configured annotation into the generated class.
         */
        COPY,
        /**
         * Native bridge processor uses the configured annotation for marshaller lookup.
         */
        LOOKUP_MARSHALLER
    }
}
