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

import org.graalvm.jniutils.HSObject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Instruments the native bridge processor to marshall annotated method return type or method
 * parameter as a reference to a foreign object.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.PARAMETER})
public @interface ByReference {

    /**
     * The class to instantiate for a foreign handle.
     * <p>
     * For HotSpot to native calls.
     * <ul>
     * <li>If the bridged type is an interface, the class must be assignable to the
     * {@link NativeObject}.</li>
     * <li>If the bridged type is a class, the class must have a field of the {@link NativeObject}
     * type annotated with the {@link EndPointHandle}.</li>
     * <li>If the bridged has a custom dispatch, the class must be the dispatch class with a
     * {@link CustomDispatchFactory factory}.</li>
     * </ul>
     * </p>
     * <p>
     * For native to HotSpot calls.
     * <li>If the bridged type is an interface, the class must be assignable to the
     * {@link HSObject}.</li>
     * <li>If the bridged type is a class, the class must have a field of the {@link HSObject} type
     * annotated with the {@link EndPointHandle}.</li>
     * <li>If the bridged has a custom dispatch, the class must be a dispatch class with a
     * {@link CustomDispatchFactory factory}.</li>
     * </p>
     */
    Class<?> value();

    /**
     * For classes with a custom dispatch, when set to {@code true} the foreign object is translated
     * by a custom receiver accessor before it's passed to the target method.
     *
     * @see CustomReceiverAccessor
     * @see CustomDispatchAccessor
     */
    boolean useCustomReceiverAccessor() default false;
}
