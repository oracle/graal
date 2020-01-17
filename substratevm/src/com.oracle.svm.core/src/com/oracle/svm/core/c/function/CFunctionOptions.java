/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c.function;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.c.function.CFunction;

/**
 * This annotation is used to override or extend the behavior of {@link CFunction}. Must only be
 * used on methods that are annotated with {@link CFunction}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CFunctionOptions {
    /**
     * Describes the thread state transition performed when the C function is invoked.
     */
    enum Transition {
        /**
         * The thread state is transitioned from Java to VM, and the Java parts of the stack are
         * made walkable. If the C code blocks or calls back to Java, it must do an explicit thread
         * state transition to native to prevent that safepoints (and therefore garbage collections)
         * of other threads are delayed.
         */
        TO_VM
    }

    /**
     * The Java-to-C thread transition code used when calling the C function. Overrides the
     * transition that is set via the {@link CFunction} annotation.
     */
    Transition transition();
}
