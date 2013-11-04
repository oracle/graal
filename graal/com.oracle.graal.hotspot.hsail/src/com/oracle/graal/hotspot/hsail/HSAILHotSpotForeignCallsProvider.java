/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.hsail;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;

public class HSAILHotSpotForeignCallsProvider extends HotSpotForeignCallsProviderImpl {

    public HSAILHotSpotForeignCallsProvider(HotSpotGraalRuntime runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache) {
        super(runtime, metaAccess, codeCache);
    }

    @Override
    public HotSpotForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
        // we don't really support foreign calls yet, but we do want to generate dummy code for them
        // so we lazily create dummy linkages here.
        if (foreignCalls.get(descriptor) == null) {
            return register(new HotSpotForeignCallLinkage(descriptor, 0x12345678, null, null, null, null, false, new LocationIdentity[0]));
        } else {
            return super.lookupForeignCall(descriptor);
        }
    }

    @Override
    public boolean isReexecutable(ForeignCallDescriptor descriptor) {
        return lookupForeignCall(descriptor).isReexecutable();
    }

    @Override
    public LocationIdentity[] getKilledLocations(ForeignCallDescriptor descriptor) {
        return lookupForeignCall(descriptor).getKilledLocations();
    }

    @Override
    public boolean canDeoptimize(ForeignCallDescriptor descriptor) {
        return lookupForeignCall(descriptor).canDeoptimize();
    }

    public Value[] getNativeABICallerSaveRegisters() {
        // TODO is this correct?
        return new Value[0];
    }
}
