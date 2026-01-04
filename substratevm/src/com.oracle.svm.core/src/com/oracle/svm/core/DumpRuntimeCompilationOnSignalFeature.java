/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform.WINDOWS_BASE;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.jdk.RuntimeSupport;

import jdk.internal.misc.Signal;

@AutomaticallyRegisteredFeature
public class DumpRuntimeCompilationOnSignalFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.DumpRuntimeCompilationOnSignal.getValue() && !Platform.includedIn(WINDOWS_BASE.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (RuntimeCompilation.isEnabled()) {
            RuntimeSupport.getRuntimeSupport().addStartupHook(new DumpRuntimeCompilationStartupHook());
        }
    }
}

final class DumpRuntimeCompilationStartupHook implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        if (isFirstIsolate) {
            DumpRuntimeCompilation.install();
        }
    }
}

class DumpRuntimeCompilation implements Signal.Handler {
    static void install() {
        Signal.handle(new Signal("USR2"), new DumpRuntimeCompilation());
    }

    @Override
    public void handle(Signal arg0) {
        DumpRuntimeCompilationSupport.dump();
    }
}
