/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.shared;

import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageSingletons;

/**
 * Provides hosted build phase state to code that can execute in the guest context.
 */
public interface BuildPhaseProvider {

    boolean featureRegistrationFinished();

    boolean setupFinished();

    boolean analysisStarted();

    boolean analysisFinished();

    boolean hostedUniverseBuilt();

    boolean readyForCompilation();

    boolean compileQueueFinished();

    boolean compilationFinished();

    boolean heapLayoutFinished();

    private static BuildPhaseProvider singleton() {
        return ImageSingletons.lookup(BuildPhaseProvider.class);
    }

    static boolean isFeatureRegistrationFinished() {
        return ImageSingletons.contains(BuildPhaseProvider.class) && singleton().featureRegistrationFinished();
    }

    static boolean isSetupFinished() {
        return ImageSingletons.contains(BuildPhaseProvider.class) && singleton().setupFinished();
    }

    static boolean isAnalysisStarted() {
        return ImageSingletons.contains(BuildPhaseProvider.class) && singleton().analysisStarted();
    }

    static boolean isAnalysisFinished() {
        return ImageSingletons.contains(BuildPhaseProvider.class) && singleton().analysisFinished();
    }

    static boolean isHostedUniverseBuilt() {
        return ImageSingletons.contains(BuildPhaseProvider.class) && singleton().hostedUniverseBuilt();
    }

    static boolean isReadyForCompilation() {
        return ImageSingletons.contains(BuildPhaseProvider.class) && singleton().readyForCompilation();
    }

    static boolean isCompileQueueFinished() {
        return ImageSingletons.contains(BuildPhaseProvider.class) && singleton().compileQueueFinished();
    }

    static boolean isCompilationFinished() {
        return ImageSingletons.contains(BuildPhaseProvider.class) && singleton().compilationFinished();
    }

    static boolean isHeapLayoutFinished() {
        return ImageSingletons.contains(BuildPhaseProvider.class) && singleton().heapLayoutFinished();
    }

    class AfterAnalysis implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return BuildPhaseProvider.isAnalysisFinished();
        }
    }

    class AfterHostedUniverse implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return BuildPhaseProvider.isHostedUniverseBuilt();
        }
    }

    class ReadyForCompilation implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return BuildPhaseProvider.isReadyForCompilation();
        }
    }

    class CompileQueueFinished implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return BuildPhaseProvider.isCompileQueueFinished();
        }
    }

    class AfterCompilation implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return BuildPhaseProvider.isCompilationFinished();
        }
    }

    class AfterHeapLayout implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return BuildPhaseProvider.isHeapLayoutFinished();
        }
    }
}
