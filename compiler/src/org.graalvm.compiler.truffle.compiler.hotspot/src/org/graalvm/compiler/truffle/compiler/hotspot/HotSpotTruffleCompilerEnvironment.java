/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot;

import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerEnvironment;

public class HotSpotTruffleCompilerEnvironment extends TruffleCompilerEnvironment {

    public HotSpotTruffleCompilerEnvironment(TruffleCompilerRuntime runtime) {
        super(runtime, createTruffleTypes(runtime));
    }

    private static HotSpotKnownTruffleTypes createTruffleTypes(TruffleCompilerRuntime runtime) {
        OptionValues options = runtime.getGraalOptions(OptionValues.class);
        HotSpotGraalRuntimeProvider hotspotGraalRuntime = (HotSpotGraalRuntimeProvider) HotSpotTruffleCompilerImpl.getCompiler(options).getGraalRuntime();
        HotSpotBackend backend = hotspotGraalRuntime.getHostBackend();
        return new HotSpotKnownTruffleTypes(runtime, backend.getProviders().getMetaAccess(), backend.getConstantReflection());
    }

    @Override
    public HotSpotKnownTruffleTypes types() {
        return (HotSpotKnownTruffleTypes) super.types();
    }

    public static HotSpotTruffleCompilerEnvironment get() {
        return (HotSpotTruffleCompilerEnvironment) TruffleCompilerEnvironment.get();
    }

}
