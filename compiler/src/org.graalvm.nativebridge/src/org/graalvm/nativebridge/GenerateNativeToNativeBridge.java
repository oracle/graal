/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.c.function.CEntryPoint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BooleanSupplier;

/**
 * Generates a native to native bridge. For the annotated class, the processor generates a bridge
 * delegating calls to the object in a native image heap. Further information can be found in the
 * <a href=
 * "http://github.com/oracle/graal/blob/master/compiler/docs/NativeBridgeProcessor.md">native bridge
 * tutorial</a>.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface GenerateNativeToNativeBridge {
    /**
     * If the supplier returns {@code true}, the bridge entry points are added automatically when
     * building a shared library. This means the bridge entry points are root methods for
     * compilation, and everything reachable from them is compiled too. The provided class must have
     * a nullary constructor, which is used to instantiate the class. The
     * {@link BooleanSupplier#getAsBoolean()}} function is called on the newly instantiated
     * instance.
     */
    Class<? extends BooleanSupplier> include() default CEntryPoint.AlwaysIncluded.class;

    /**
     * The native bridge configuration. The returned class must have an accessible static
     * {@code getInstance()} method returning a {@link JNIConfig} instance. The returned
     * {@link JNIConfig} instance is used for marshallers' lookup.
     */
    Class<?> jniConfig();
}
