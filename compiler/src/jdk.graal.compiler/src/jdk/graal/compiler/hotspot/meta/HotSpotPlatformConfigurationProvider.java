/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.meta;

import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;

public class HotSpotPlatformConfigurationProvider implements PlatformConfigurationProvider {
    private final BarrierSet barrierSet;

    private final boolean canVirtualizeLargeByteArrayAccess;

    private final boolean useLightweightLocking;

    public HotSpotPlatformConfigurationProvider(GraalHotSpotVMConfig config, BarrierSet barrierSet) {
        this.barrierSet = barrierSet;
        this.canVirtualizeLargeByteArrayAccess = config.deoptimizationSupportLargeAccessByteArrayVirtualization;
        this.useLightweightLocking = HotSpotReplacementsUtil.useLightweightLocking(config);
    }

    @Override
    public boolean canVirtualizeLargeByteArrayAccess() {
        return canVirtualizeLargeByteArrayAccess;
    }

    @Override
    public boolean requiresStrictLockOrder() {
        return useLightweightLocking;
    }

    @Override
    public boolean areLocksSideEffectFree() {
        // Starting in 21 the JVM tracks lock entry and exit in JavaThread::_held_monitor_count.
        // This means it's not safe to pick up locks during PEA and materialize them at an
        // arbitrary point since the FrameState might be before those locks were acquired so they
        // will never be unlocked. Additionally lightweight locking maintains an explicit lock stack
        // which will also be out of sync after a deopt.
        return false;
    }

    @Override
    public BarrierSet getBarrierSet() {
        return barrierSet;
    }
}
