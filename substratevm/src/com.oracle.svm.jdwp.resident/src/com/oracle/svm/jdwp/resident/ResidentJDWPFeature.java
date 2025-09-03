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
package com.oracle.svm.jdwp.resident;

import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.interpreter.InterpreterFeature;
import com.oracle.svm.interpreter.debug.DebuggerEventsFeature;
import com.oracle.svm.jdwp.bridge.JDWPNativeBridgeSupport;
import com.oracle.svm.jdwp.bridge.ResidentJDWPFeatureEnabled;
import com.oracle.svm.jdwp.bridge.jniutils.NativeBridgeSupport;

@Platforms(Platform.HOSTED_ONLY.class)
@AutomaticallyRegisteredFeature
final class ResidentJDWPFeature implements InternalFeature {

    @Override
    public String getDescription() {
        return "Support debugging native images via JDWP, using standard Java tooling";
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(NativeBridgeSupport.class, new JDWPNativeBridgeSupport());
        ImageSingletons.add(ResidentJDWPFeatureEnabled.class, new ResidentJDWPFeatureEnabled());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        RuntimeSupport.getRuntimeSupport().addStartupHook(new DebuggingOnDemandHook());
        ImageSingletons.add(ThreadStartDeathSupport.class, new ThreadStartDeathSupport());
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JDWPOptions.JDWP.getValue();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(InterpreterFeature.class, DebuggerEventsFeature.class);
    }
}
