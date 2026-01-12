/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.attach;

import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SigQuitFeature;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jdk.RuntimeSupportFeature;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;

/**
 * The attach API mechanism uses platform-specific implementation (see {@link AttachApiSupport}) and
 * a {@code SIGQUIT/SIGBREAK} signal handler (see {@link SigQuitFeature}).
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = Independent.class)
public class AttachApiFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild() && VMInspectionOptions.hasJCmdSupport();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(RuntimeSupportFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        RuntimeSupport.getRuntimeSupport().addShutdownHook(new AttachApiTeardownHook());
    }
}

final class AttachApiTeardownHook implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        AttachApiSupport.singleton().shutdown(true);
    }
}
