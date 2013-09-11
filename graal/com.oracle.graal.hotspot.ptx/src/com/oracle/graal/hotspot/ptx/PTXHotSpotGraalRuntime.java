/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.ptx;

import com.oracle.graal.ptx.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * PTX specific implementation of {@link HotSpotGraalRuntime}.
 */
public class PTXHotSpotGraalRuntime extends HotSpotGraalRuntime {

    protected PTXHotSpotGraalRuntime() {
    }

    /**
     * Called from C++ code to retrieve the singleton instance, creating it first if necessary.
     */
    public static HotSpotGraalRuntime makeInstance() {
        HotSpotGraalRuntime graalRuntime = graalRuntime();
        if (graalRuntime == null) {
            HotSpotGraalRuntimeFactory factory = findFactory("PTX");
            if (factory != null) {
                graalRuntime = factory.createRuntime();
            } else {
                graalRuntime = new PTXHotSpotGraalRuntime();
            }
            graalRuntime.completeInitialization();
        }
        return graalRuntime;
    }

    protected Architecture createArchitecture() {
        return new PTX();
    }

    @Override
    protected TargetDescription createTarget() {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        return new TargetDescription(createArchitecture(), true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    @Override
    protected HotSpotBackend createBackend() {
        return new PTXHotSpotBackend(getRuntime(), getTarget());
    }

    @Override
    protected HotSpotRuntime createRuntime() {
        return new PTXHotSpotRuntime(config, this);
    }

    @Override
    protected Value[] getNativeABICallerSaveRegisters() {
        throw new InternalError("NYI");
    }
}
