/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ServiceLoader;

/**
 * Classes annotated with &#064;Collect will be gathered at compile time into a collector class.
 *
 * <p>
 * The collector functionality is somehow akin to a compile-time {@link ServiceLoader}.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Collect {
    /**
     * Anchor class(es).
     *
     * <p>
     * One collector class will generated per anchor class, in the same package. The generated
     * collector provides a public static method
     * {@code <T> List<T> AnchorClassCollector.getInstances(Class<? extends T> componentType)} to
     * gather instances of all annotated classes. An empty constructor is required.
     * 
     * <h3>Example:</h3>
     *
     * <pre>
     * &#064;Collect(AnchorClass.class)
     * public class MyClass { ... }
     * </pre>
     * 
     * Will generate a class {@code AnchorClassCollector} in the same package as
     * {@code AnchorClass}. <br>
     * {@code <T> List<T> AnchorClassCollector.getInstances(Class<? extends T> componentType)} will
     * return list containing an instance of {@code MyClass}.
     */
    Class<?>[] value();
}
