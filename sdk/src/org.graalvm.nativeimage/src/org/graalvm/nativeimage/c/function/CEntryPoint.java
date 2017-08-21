/*
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c.function;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.word.WordBase;

/**
 * Annotates a method that is a VM entry point. Such a method must be declared <i>static</i>, and is
 * compiled so that it conforms to the native ABI and can be called as a C function.
 * <p>
 * An execution context must be passed as a parameter and can be either an {@link IsolateThread}
 * that is specific to the current thread, or an {@link Isolate} for an isolate in which the current
 * thread is attached. These pointers can be obtained via the methods of {@link CEntryPointContext}.
 * Specifying more than one parameter of these types is not allowed.
 * <p>
 * Exceptions cannot be thrown to the caller and must be explicitly caught in the entry point
 * method. Any uncaught exception causes the termination of the process after it is printed.
 * <p>
 * No object types are permitted for parameters or return types; only primitive Java values,
 * {@link WordBase word} values, and enum values are allowed. Enum values are automatically
 * converted from integer constants to Java enum object constants. The enum class must have a
 * {@link CEnum} annotation. When enum values are passed as parameters, the enum class must have a
 * method with a {@link CEnumLookup} annotation. For enum return types, the enum class must have a
 * method that is annotated with {@link CEnumValue}.
 * <p>
 * The used calling convention is different to that of regular Java methods, so a method annotated
 * with {@link CEntryPoint} must not be called from any Java method.
 *
 * @see CEntryPointContext
 * @see CEntryPointLiteral
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CEntryPoint {

    /**
     * The symbol name to use for this entry point.
     */
    String name() default "";

    /**
     * Method documentation to be displayed in the the header file.
     */
    String[] documentation() default "";
}
