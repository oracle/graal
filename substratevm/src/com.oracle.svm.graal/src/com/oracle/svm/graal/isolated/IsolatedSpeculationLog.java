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

import java.nio.ByteBuffer;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog.SubstrateSpeculation;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

public final class IsolatedSpeculationLog extends IsolatedObjectProxy<SpeculationLog> implements SpeculationLog {
    public IsolatedSpeculationLog(ClientHandle<SpeculationLog> logHandle) {
        super(logHandle);
    }

    @Override
    public void collectFailedSpeculations() {
        collectFailedSpeculations0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public boolean maySpeculate(SpeculationReason reason) {
        SpeculationReasonEncoding encoding = reason.encode(IsolatedSpeculationReasonEncoding::new);
        byte[] bytes = ((IsolatedSpeculationReasonEncoding) encoding).getByteArray();
        try (PinnedObject pinnedBytes = PinnedObject.create(bytes)) {
            return maySpeculate0(IsolatedCompileContext.get().getClient(), handle, pinnedBytes.addressOfArrayElement(0), bytes.length);
        }
    }

    @Override
    public Speculation speculate(SpeculationReason reason) {
        SpeculationReasonEncoding encoding = reason.encode(IsolatedSpeculationReasonEncoding::new);
        byte[] bytes = ((IsolatedSpeculationReasonEncoding) encoding).getByteArray();
        ClientHandle<SpeculationReason> reasonHandle;
        try (PinnedObject pinnedBytes = PinnedObject.create(bytes)) {
            reasonHandle = speculate0(IsolatedCompileContext.get().getClient(), handle, pinnedBytes.addressOfArrayElement(0), bytes.length);
        }
        return new SubstrateSpeculation(new IsolatedSpeculationReason(reasonHandle));
    }

    @Override
    public boolean hasSpeculations() {
        return hasSpeculations0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public Speculation lookupSpeculation(JavaConstant constant) {
        throw VMError.shouldNotReachHere("not required");
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void collectFailedSpeculations0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SpeculationLog> logHandle) {
        IsolatedCompileClient.get().unhand(logHandle).collectFailedSpeculations();
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static boolean hasSpeculations0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SpeculationLog> logHandle) {
        return IsolatedCompileClient.get().unhand(logHandle).hasSpeculations();
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static boolean maySpeculate0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SpeculationLog> logHandle, PointerBase arrayData, int length) {
        byte[] bytes = new byte[length];
        ByteBuffer.wrap(bytes).put(CTypeConversion.asByteBuffer(arrayData, length));
        SpeculationLog log = IsolatedCompileClient.get().unhand(logHandle);
        return log.maySpeculate(new EncodedSpeculationReason(bytes));
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<SpeculationReason> speculate0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SpeculationLog> logHandle, PointerBase arrayData, int length) {
        byte[] bytes = new byte[length];
        ByteBuffer.wrap(bytes).put(CTypeConversion.asByteBuffer(arrayData, length));
        SpeculationLog log = IsolatedCompileClient.get().unhand(logHandle);
        EncodedSpeculationReason encodedReason = new EncodedSpeculationReason(bytes);
        Speculation speculation = log.speculate(encodedReason);
        /*
         * We can't pass the Speculation instance to the compilation isolate in a useful way, but we
         * only need a handle to the SpeculationReason so we can create Speculation objects that are
         * equal to that one in both the compiler isolate and in the client isolate.
         */
        SpeculationReason reason = speculation.getReason();
        assert speculation.equals(new SubstrateSpeculation(reason));
        return IsolatedCompileClient.get().hand(reason);
    }
}
