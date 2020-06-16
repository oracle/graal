/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.channels.spi.SelectorProvider;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

@TargetClass(className = "java.nio.channels.spi.SelectorProvider", onlyWith = JDK14OrEarlier.class)
final class Target_java_nio_channels_spi_SelectorProvider {

    @Alias//
    static SelectorProvider provider;

    @Substitute
    static SelectorProvider provider() {
        VMError.guarantee(provider != null, "java.nio.channels.spi.SelectorProvider.provider must be initialized during image generation");
        return provider;
    }

    static {
        /*
         * Calling the method during image generation triggers initialization. This ensures that we
         * have a correctly initialized provider available at run time. It also means that the
         * system property and service loader configuration that allow influencing the
         * SelectorProvider implementation are accessed during image generation, i.e., it is not
         * possible to overwrite the implementation class at run time anymore by changing the system
         * property at run time.
         */
        SelectorProvider result = java.nio.channels.spi.SelectorProvider.provider();
        assert result != null;
    }
}

@TargetClass(className = "java.nio.channels.spi.SelectorProvider", innerClass = "Holder", onlyWith = JDK15OrLater.class)
final class Target_java_nio_channels_spi_SelectorProvider_Holder {

    @Alias//
    static SelectorProvider INSTANCE;

    @Substitute
    static SelectorProvider provider() {
        VMError.guarantee(INSTANCE != null, "java.nio.channels.spi.SelectorProvider.Holder.INSTANCE must be initialized during image generation");
        return INSTANCE;
    }

    static {
        /*
         * Calling the method during image generation triggers initialization. This ensures that we
         * have a correctly initialized provider available at run time. It also means that the
         * system property and service loader configuration that allow influencing the
         * SelectorProvider implementation are accessed during image generation, i.e., it is not
         * possible to overwrite the implementation class at run time anymore by changing the system
         * property at run time.
         */
        SelectorProvider result = java.nio.channels.spi.SelectorProvider.provider();
        assert result != null;
    }
}

/** Dummy class to have a class with the file's name. */
public final class SunNioSubstitutions {
}
