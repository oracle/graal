/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot.libgraal;

import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.IsCancelled;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.IsLastTier;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callIsCancelled;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callIsLastTier;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HotSpotToSVMScope.env;

import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObject;

/**
 * Proxy for a {@code Supplier<Boolean>} object in the HotSpot heap.
 */
final class HSTruffleCompilationTask extends HSObject implements TruffleCompilationTask {

    HSTruffleCompilationTask(HotSpotToSVMScope scope, JObject handle) {
        super(scope, handle);
    }

    @SVMToHotSpot(IsCancelled)
    @Override
    public boolean isCancelled() {
        return callIsCancelled(env(), getHandle());
    }

    @SVMToHotSpot(IsLastTier)
    @Override
    public boolean isLastTier() {
        return callIsLastTier(env(), getHandle());
    }
}
