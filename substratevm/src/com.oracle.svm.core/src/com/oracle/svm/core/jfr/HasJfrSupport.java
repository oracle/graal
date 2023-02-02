/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import java.util.function.BooleanSupplier;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Returns {@code true} if the Native Image is built with JFR support. This does not necessarily
 * mean that JFR is really enabled at runtime.
 */
public class HasJfrSupport implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return get();
    }

    @Fold
    public static boolean get() {
        return ImageSingletons.contains(JfrManager.class);
    }
}

/**
 * Returns {@code true} if the HotSpot JVM is recording and emitting JFR events while doing the
 * Native Image build.
 */
@Platforms(Platform.HOSTED_ONLY.class)
class JfrHostedEnabled implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return ImageSingletons.contains(JfrManager.class) && ImageSingletons.lookup(JfrManager.class).hostedEnabled;
    }
}
