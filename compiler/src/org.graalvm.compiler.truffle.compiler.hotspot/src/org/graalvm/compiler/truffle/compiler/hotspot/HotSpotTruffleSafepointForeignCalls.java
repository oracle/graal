/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;
import static org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleSafepointForeignCalls.Options.HandshakeFastpath;

import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.ForeignCallsPlugin;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Defines the foreign call to invoke the static no-args void method called on the slow path of a
 * Truffle safepoint as well as the stub needed to make the foreign call from Graal compiled code.
 */
@ServiceProvider(ForeignCallsPlugin.class)
public final class HotSpotTruffleSafepointForeignCalls implements ForeignCallsPlugin {

    public static class Options {
        @Option(help = "Controls emission of the fast path for ThreadLocalHandle.poll", type = OptionType.Expert)//
        public static final OptionKey<Boolean> HandshakeFastpath = new OptionKey<>(true);
    }

    static final HotSpotForeignCallDescriptor THREAD_LOCAL_HANDSHAKE_POLL = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "ThreadLocalHandshake.poll", void.class);

    @Override
    public void initialize(HotSpotProviders providers, OptionValues options, HotSpotForeignCallsProviderImpl foreignCalls) {
        long invokeJavaMethodAddress = foreignCalls.getRuntime().getVMConfig().invokeJavaMethodAddress;
        if (invokeJavaMethodAddress != 0) {
            ResolvedJavaMethod staticMethod;
            ResolvedJavaType handshakeType = TruffleCompilerRuntime.getRuntime().resolveType(providers.getMetaAccess(), "org.graalvm.compiler.truffle.runtime.hotspot.HotSpotThreadLocalHandshake");
            HotSpotSignature noArgsVoidSig = new HotSpotSignature(foreignCalls.getJVMCIRuntime(), "()V");
            if (HandshakeFastpath.getValue(options))
                staticMethod = handshakeType.findMethod("doHandshake", noArgsVoidSig);
            else {
                staticMethod = handshakeType.findMethod("doPoll", noArgsVoidSig);
            }
            foreignCalls.invokeJavaMethodStub(options, providers, THREAD_LOCAL_HANDSHAKE_POLL, invokeJavaMethodAddress, staticMethod);
        }
    }
}
