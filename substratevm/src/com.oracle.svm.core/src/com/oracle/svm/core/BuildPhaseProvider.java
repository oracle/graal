/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@Platforms(Platform.HOSTED_ONLY.class)
public final class BuildPhaseProvider {

    private boolean analysisFinished;
    private boolean hostedUniverseBuilt;
    private boolean compilationFinished;

    public static void init() {
        ImageSingletons.add(BuildPhaseProvider.class, new BuildPhaseProvider());
    }

    static BuildPhaseProvider singleton() {
        return ImageSingletons.lookup(BuildPhaseProvider.class);
    }

    BuildPhaseProvider() {
    }

    public static void markAnalysisFinished() {
        singleton().analysisFinished = true;
    }

    public static boolean isAnalysisFinished() {
        return singleton().analysisFinished;
    }

    public static void markHostedUniverseBuilt() {
        singleton().hostedUniverseBuilt = true;
    }

    public static boolean isHostedUniverseBuilt() {
        return singleton().hostedUniverseBuilt;
    }

    public static void markCompilationFinished() {
        singleton().compilationFinished = true;
    }

    public static boolean isCompilationFinished() {
        return singleton().compilationFinished;
    }
}
