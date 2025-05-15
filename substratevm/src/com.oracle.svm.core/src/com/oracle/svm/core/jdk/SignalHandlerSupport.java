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
package com.oracle.svm.core.jdk;

import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.IsolateListenerSupport;
import com.oracle.svm.core.IsolateListenerSupport.IsolateListener;
import com.oracle.svm.core.IsolateListenerSupportFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

public interface SignalHandlerSupport extends IsolateListener {
    @Fold
    static SignalHandlerSupport singleton() {
        return ImageSingletons.lookup(SignalHandlerSupport.class);
    }

    long installJavaSignalHandler(int sig, long nativeH);

    void stopDispatcherThread();
}

class NoSignalHandlerSupport implements SignalHandlerSupport {
    @Override
    public long installJavaSignalHandler(int sig, long nativeH) {
        throw new IllegalStateException("Signal handling is not supported.");
    }

    @Override
    public void stopDispatcherThread() {
        throw VMError.shouldNotReachHere("Signal handling is not supported.");
    }
}

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
@AutomaticallyRegisteredFeature
class SignalHandlerFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(RuntimeSupportFeature.class, IsolateListenerSupportFeature.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (ImageSingletons.contains(SignalHandlerSupport.class)) {
            SignalHandlerSupport support = SignalHandlerSupport.singleton();
            RuntimeSupport.getRuntimeSupport().addTearDownHook(new StopDispatcherThread());
            IsolateListenerSupport.singleton().register(support);
        } else {
            /* Fallback for platforms where there is no signal handling implementation. */
            ImageSingletons.add(SignalHandlerSupport.class, new NoSignalHandlerSupport());
        }
    }
}

class StopDispatcherThread implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        SignalHandlerSupport.singleton().stopDispatcherThread();
    }
}
