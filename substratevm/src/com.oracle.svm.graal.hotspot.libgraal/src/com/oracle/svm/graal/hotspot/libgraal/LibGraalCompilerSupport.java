/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.ImageHeapMap;

/**
 * Holds data that is pre-computed during native image generation and accessed at run time during a
 * Graal compilation.
 */
public class LibGraalCompilerSupport {

    public final EconomicMap<Class<?>, Object> nodeClasses = ImageHeapMap.create();
    public final EconomicMap<Class<?>, Object> instructionClasses = ImageHeapMap.create();

    protected EconomicMap<Class<?>, Object> basePhaseStatistics = ImageHeapMap.create();
    protected EconomicMap<Class<?>, Object> lirPhaseStatistics = ImageHeapMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    static void registerStatistics(Class<?> phaseSubClass, EconomicMap<Class<?>, Object> cache, Object newStatistics) {
        assert !cache.containsKey(phaseSubClass);
        cache.put(phaseSubClass, newStatistics);
    }

    public static LibGraalCompilerSupport get() {
        return ImageSingletons.lookup(LibGraalCompilerSupport.class);
    }

    public EconomicMap<Class<?>, Object> getBasePhaseStatistics() {
        return basePhaseStatistics;
    }

    public EconomicMap<Class<?>, Object> getLirPhaseStatistics() {
        return lirPhaseStatistics;
    }
}
