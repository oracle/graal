/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import static org.graalvm.libgraal.LibGraalScope.getIsolateThread;

import java.io.Closeable;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.libgraal.LibGraalObject;
import org.graalvm.libgraal.LibGraalScope;
import org.graalvm.util.OptionsEncoder;

final class IgvSupport extends LibGraalObject implements TruffleDebugContext {

    private static volatile Map<Object, Object> versionProperties;
    private static volatile String executionId;

    private final LibGraalHotSpotTruffleCompiler owner;
    private final LibGraalScope scope;
    private GraphOutput<?, ?> parentOutput;
    private IgvDumpChannel sharedChannel;

    private IgvSupport(LibGraalScope scope, LibGraalHotSpotTruffleCompiler owner, long handle) {
        super(handle);
        Objects.requireNonNull(owner, "Owner must be non null.");
        this.owner = owner;
        this.scope = scope;
    }

    @Override
    public <G, N, M> GraphOutput<G, M> buildOutput(
                    final GraphOutput.Builder<G, N, M> builder) throws IOException {
        GraphOutput<?, ?> parent = parentOutput;
        if (parent != null) {
            return builder.build(parent);
        }
        if (sharedChannel == null) {
            sharedChannel = new IgvDumpChannel(TruffleToLibGraalCalls.getDumpChannel(getIsolateThread(), getHandle()));
        }
        final GraphOutput<G, M> res = builder.attr(GraphOutput.ATTR_VM_ID, getExecutionID()).embedded(true).build(sharedChannel);
        parentOutput = res;
        return res;
    }

    @Override
    public boolean isDumpEnabled() {
        return TruffleToLibGraalCalls.isBasicDumpEnabled(getIsolateThread(), getHandle());
    }

    @Override
    public Map<Object, Object> getVersionProperties() {
        Map<Object, Object> res = versionProperties;
        if (res == null) {
            byte[] serializedProperties = TruffleToLibGraalCalls.getVersionProperties(getIsolateThread());
            res = Collections.unmodifiableMap(OptionsEncoder.decode(serializedProperties));
            versionProperties = res;
        }
        return res;
    }

    @Override
    public Closeable scope(String name) {
        return scope(name, null);
    }

    @Override
    public Closeable scope(String name, Object context) {
        CompilableTruffleAST compilable = context instanceof TruffleDebugJavaMethod ? ((TruffleDebugJavaMethod) context).getCompilable() : null;
        long compilationHandle;
        if (compilable == null) {
            compilationHandle = 0;
        } else {
            LibGraalTruffleCompilation compilation = owner.getActiveCompilation();
            assert compilation != null : compilable;
            compilationHandle = compilation.getHandle();
        }
        long scopeHandle = TruffleToLibGraalCalls.openDebugContextScope(getIsolateThread(), getHandle(), name, compilationHandle);
        return scopeHandle == 0 ? null : new Scope(scopeHandle);
    }

    @Override
    public void close() {
        try {
            TruffleToLibGraalCalls.closeDebugContext(getIsolateThread(), getHandle());
        } finally {
            scope.close();
        }
    }

    @Override
    public void closeDebugChannels() {
    }

    private static String getExecutionID() {
        String res = executionId;
        if (res == null) {
            res = TruffleToLibGraalCalls.getExecutionID(getIsolateThread());
            executionId = res;
        }
        return res;
    }

    static IgvSupport create(LibGraalHotSpotTruffleCompiler compiler, Map<String, Object> options, LibGraalTruffleCompilation compilation) {
        byte[] encodedOptions = OptionsEncoder.encode(options);
        LibGraalScope scope = new LibGraalScope();
        return new IgvSupport(scope, compiler,
                        TruffleToLibGraalCalls.openDebugContext(getIsolateThread(), LibGraalHotSpotTruffleCompiler.handle(), compilation == null ? 0 : compilation.getHandle(), encodedOptions));
    }

    private static final class IgvDumpChannel extends LibGraalObject implements WritableByteChannel {

        IgvDumpChannel(long handle) {
            super(handle);
        }

        /**
         * See {@code org.graalvm.compiler.serviceprovider.BufferUtil}.
         */
        static Buffer asBaseBuffer(Buffer obj) {
            return obj;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (src.hasArray()) {
                throw new IllegalArgumentException("Only direct ByteBuffer is supported.");
            }
            int capacity = src.capacity();
            int pos = src.position();
            int limit = src.limit();
            int written = TruffleToLibGraalCalls.dumpChannelWrite(getIsolateThread(), getHandle(), src, capacity, pos, limit);
            if (written > 0) {
                asBaseBuffer(src).position(pos + written);
            }
            return written;
        }

        @Override
        public boolean isOpen() {
            return TruffleToLibGraalCalls.isDumpChannelOpen(getIsolateThread(), getHandle());
        }

        @Override
        public void close() throws IOException {
            TruffleToLibGraalCalls.dumpChannelClose(getIsolateThread(), getHandle());
        }
    }

    private static final class Scope extends LibGraalObject implements Closeable {

        Scope(long handle) {
            super(handle);
        }

        @Override
        public void close() {
            TruffleToLibGraalCalls.closeDebugContextScope(getIsolateThread(), getHandle());
        }
    }
}
