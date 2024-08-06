/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.io.IOException;

import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.runtime.RuntimeProvider;
import org.junit.Test;

import jdk.vm.ci.aarch64.AArch64;

/**
 * Checks that the HotSpot flags related to intrinsics implemented only by Graal are off by default.
 * Failure of this test indicates the intrinsic may have been implemented in HotSpot and Graal needs
 * to respect the related HotSpot flags.
 */
public class GraalOnlyIntrinsicsTest extends SubprocessTest {

    public final HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
    public final GraalHotSpotVMConfig config = rt.getVMConfig();

    /**
     * Intrinsic for ArraysSupport.vectorizedMismatch is not yet implemented on AArch64 HotSpot. The
     * relevant flag UseVectorizedMismatchIntrinsic should default to false. If this assertion
     * fails, remove the overridden {@link InvocationPlugin#isGraalOnly} in the
     * ArraysSupport.vectorizedMismatch intrinsic plugin.
     */
    public void testUseVectorizedMismatchIntrinsic() {
        if (getArchitecture() instanceof AArch64) {
            assertFalse(config.useVectorizedMismatchIntrinsic);
        }
    }

    /**
     * Intrinsic for CharacterDataLatin1.isDigit is not yet implemented on x64/AArch64 HotSpot. The
     * relevant flag UseCharacterCompareIntrinsics should default to false. If this assertion fails,
     * remove the overridden {@link InvocationPlugin#isGraalOnly} in the CharacterDataLatin1.isDigit
     * intrinsic plugin.
     */
    public void testUseCharacterCompareIntrinsics() {
        assertFalse(config.useCharacterCompareIntrinsics);
    }

    @Test
    public void assertHotSpotFlags() throws IOException, InterruptedException {
        launchSubprocess(s -> !s.contains("UseVectorizedMismatchIntrinsic"), () -> testUseVectorizedMismatchIntrinsic());
        launchSubprocess(s -> !s.contains("UseCharacterCompareIntrinsics"), () -> testUseCharacterCompareIntrinsics());
    }
}
