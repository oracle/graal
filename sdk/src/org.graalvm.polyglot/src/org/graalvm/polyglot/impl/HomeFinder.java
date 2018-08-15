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
package org.graalvm.polyglot.impl;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

public abstract class HomeFinder {

    private static final boolean IS_AOT = Boolean.getBoolean("com.oracle.graalvm.isaot") || Boolean.getBoolean("com.oracle.truffle.aot");
    private static HomeFinder nativeImageHomeFinder;   // effectively final after native image
                                                       // compilation

    public abstract Path getHomeFolder();

    public abstract String getVersion();

    public abstract Map<String, Path> getLanguageHomes();

    public abstract Map<String, Path> getToolHomes();

    public static HomeFinder getInstance(ClassLoader classLoader) {
        if (IS_AOT) {
            return nativeImageHomeFinder;
        }
        ServiceLoader<HomeFinder> finders = classLoader == null ? ServiceLoader.load(HomeFinder.class) : ServiceLoader.load(HomeFinder.class, classLoader);
        Iterator<HomeFinder> it = finders.iterator();
        return it.hasNext() ? it.next() : null;
    }

    /**
     * Looks up the {@link HomeFinder} instance.
     *
     * NOTE: this method is called reflectively by downstream projects.
     *
     */
    @SuppressWarnings("unused")
    private static void initializeNativeImageState() {
        assert IS_AOT : "Only supported during image generation";
        Iterator<HomeFinder> homeFinders = ServiceLoader.load(HomeFinder.class).iterator();
        nativeImageHomeFinder = homeFinders.next(); // todo hasNext
    }

    /**
     * Cleans the {@link HomeFinder} instance.
     *
     * NOTE: this method is called reflectively by downstream projects.
     *
     */
    @SuppressWarnings("unused")
    private static void resetNativeImageState() {
        assert IS_AOT : "Only supported during image generation";
        nativeImageHomeFinder = null;
    }
}
