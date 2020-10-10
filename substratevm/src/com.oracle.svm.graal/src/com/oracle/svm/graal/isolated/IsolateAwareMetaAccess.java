/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog.SubstrateSpeculation;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.graal.meta.SubstrateMetaAccess;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

/** Code for {@link SubstrateMetaAccess} that is specific to compilation in isolates. */
final class IsolateAwareMetaAccess extends SubstrateMetaAccess {
    @Override
    public JavaConstant encodeSpeculation(SpeculationLog.Speculation speculation) {
        if (!SubstrateOptions.shouldCompileInIsolates()) {
            return super.encodeSpeculation(speculation);
        }

        if (speculation == SpeculationLog.NO_SPECULATION) {
            return SubstrateObjectConstant.forObject(speculation);
        }
        ClientHandle<SpeculationLog.SpeculationReason> handle = ((IsolatedSpeculationReason) speculation.getReason()).getHandle();
        return new IsolatedObjectConstant(handle, false);
    }

    @Override
    public SpeculationLog.Speculation decodeSpeculation(JavaConstant constant, @SuppressWarnings("unused") SpeculationLog speculationLog) {
        if (!SubstrateOptions.shouldCompileInIsolates()) {
            return super.decodeSpeculation(constant, speculationLog);
        }

        if (constant instanceof DirectSubstrateObjectConstant) {
            SpeculationLog.Speculation speculation = (SpeculationLog.Speculation) KnownIntrinsics.convertUnknownValue(SubstrateObjectConstant.asObject(constant), Object.class);
            assert speculation == SpeculationLog.NO_SPECULATION;
            return speculation;
        }
        @SuppressWarnings("unchecked")
        ClientHandle<SpeculationLog.SpeculationReason> reasonHandle = (ClientHandle<SpeculationLog.SpeculationReason>) ((IsolatedObjectConstant) constant).getHandle();
        return new SubstrateSpeculation(new IsolatedSpeculationReason(reasonHandle));
    }
}
