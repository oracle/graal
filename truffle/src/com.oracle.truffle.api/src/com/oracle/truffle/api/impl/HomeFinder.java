/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import java.nio.file.Path;
import java.util.Map;

public abstract class HomeFinder {

    private static HomeFinder nativeImageHomeFinder;   // effectively final after native image
                                                       // compilation

    public abstract Path getHomeFolder();

    public abstract String getVersion();

    public abstract Map<String, Path> getLanguageHomes();

    public abstract Map<String, Path> getToolHomes();

    public static HomeFinder getInstance() {
        if (TruffleOptions.AOT) {
            return nativeImageHomeFinder;
        }
        return Truffle.getRuntime().getCapability(HomeFinder.class);
    }

    /**
     * Looks up the {@link HomeFinder} instance.
     *
     * NOTE: this method is called reflectively by downstream projects.
     *
     */
    @SuppressWarnings("unused")
    private static void initializeNativeImageState() {
        assert TruffleOptions.AOT : "Only supported during image generation";
        nativeImageHomeFinder = Truffle.getRuntime().getCapability(HomeFinder.class);
    }

    /**
     * Cleans the {@link HomeFinder} instance.
     *
     * NOTE: this method is called reflectively by downstream projects.
     *
     */
    @SuppressWarnings("unused")
    private static void resetNativeImageState() {
        assert TruffleOptions.AOT : "Only supported during image generation";
        nativeImageHomeFinder = null;
    }
}
