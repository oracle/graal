/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateControlFlowIntegrity;
import com.oracle.svm.core.SubstrateControlFlowIntegrityFeature;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;

@AutomaticallyRegisteredFeature
@Platforms(Platform.AMD64.class)
public class AMD64ControlFlowIntegrityFeature extends SubstrateControlFlowIntegrityFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        SubstrateControlFlowIntegrity.CFIOptions cfi = SubstrateControlFlowIntegrity.Options.CFI.getValue();
        /*
         * Branch-target validation temporarily spills a scratch register below the stack pointer.
         * The System V ABI used by Linux and macOS provides a safe red zone for this spill, but the
         * Windows x64 ABI does not.
         */
        boolean supportedOperatingSystem = Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class);
        SubstrateControlFlowIntegrity.validateConfiguration(cfi, supportedOperatingSystem, SubstrateControlFlowIntegrity.CFIOptions.SW, SubstrateControlFlowIntegrity.CFIOptions.SW_NONATIVE);
        ImageSingletons.add(SubstrateControlFlowIntegrity.class, new AMD64ControlFlowIntegrity());
    }
}

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
class AMD64ControlFlowIntegrity extends SubstrateControlFlowIntegrity {
    @Override
    public CFIOptions getCFIMode() {
        return Options.CFI.getValue();
    }

    @Override
    public Register getCFITargetRegister() {
        if (SubstrateControlFlowIntegrity.useSoftwareCFI()) {
            return AMD64.r11;
        }
        return super.getCFITargetRegister();
    }
}
