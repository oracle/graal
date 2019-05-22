/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.libgraal.LibGraalScope;
import org.graalvm.libgraal.OptionsEncoder;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.services.Services;

final class IgvSupport extends SVMObject implements TruffleDebugContext {

    private static final String SOURCE_PREFIX = "SOURCE=";
    private static volatile Map<Object, Object> versionProperties;

    private final SVMHotSpotTruffleCompiler owner;
    private final LibGraalScope scope;
    private GraphOutput<?, ?> parentOutput;
    private IgvDumpChannel sharedChannel;

    private IgvSupport(LibGraalScope scope, SVMHotSpotTruffleCompiler owner, long handle) {
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
            sharedChannel = new IgvDumpChannel(HotSpotToSVMCalls.getDumpChannel(getIsolateThread(), handle));
        }
        final GraphOutput<G, M> res = builder.embedded(true).build(sharedChannel);
        parentOutput = res;
        return res;
    }

    @Override
    public boolean isDumpEnabled() {
        return HotSpotToSVMCalls.isBasicDumpEnabled(getIsolateThread(), handle);
    }

    @Override
    public Map<Object, Object> getVersionProperties() {
        Map<Object, Object> res = versionProperties;
        if (res == null) {
            synchronized (IgvSupport.class) {
                res = versionProperties;
                if (res == null) {
                    res = new HashMap<>();
                    final Path releaseFile = findReleaseFile();
                    if (releaseFile != null) {
                        try {
                            for (String line : Files.readAllLines(releaseFile)) {
                                if (line.startsWith(SOURCE_PREFIX)) {
                                    for (String versionInfo : line.substring(SOURCE_PREFIX.length()).replace('"', ' ').split(" ")) {
                                        String[] idVersion = versionInfo.split(":");
                                        if (idVersion != null && idVersion.length == 2) {
                                            res.put("version." + idVersion[0], idVersion[1]);
                                        }
                                    }
                                    break;
                                }
                            }
                        } catch (IOException ioe) {
                            // cannot read release file
                        }
                    }
                    res = Collections.unmodifiableMap(res);
                    versionProperties = res;
                }
            }
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
        SVMTruffleCompilation compilation = compilable != null ? owner.findCompilation(compilable) : null;
        long compilationHandle = compilation != null ? compilation.handle : 0;
        long scopeHandle = HotSpotToSVMCalls.openDebugContextScope(getIsolateThread(), handle, name, compilationHandle);
        return scopeHandle == 0 ? null : new Scope(scopeHandle);
    }

    @Override
    public void close() {
        try {
            HotSpotToSVMCalls.closeDebugContext(getIsolateThread(), handle);
        } finally {
            scope.close();
        }
    }

    @Override
    public void closeDebugChannels() {
    }

    private static Path findReleaseFile() {
        final String home = Services.getSavedProperties().get("java.home");
        if (home == null) {
            return null;
        }
        final Path jreDir = Paths.get(home);
        if (jreDir == null) {
            return null;
        }
        Path releaseFile = jreDir.resolve("release");
        if (Files.exists(releaseFile)) {
            return releaseFile;
        }
        Path jdkDir = jreDir.getParent();
        if (jdkDir == null) {
            return null;
        }
        releaseFile = jdkDir.resolve("release");
        return Files.exists(releaseFile) ? releaseFile : null;
    }

    static IgvSupport create(SVMHotSpotTruffleCompiler compiler, Map<String, Object> options, SVMTruffleCompilation compilation) {
        byte[] encodedOptions = OptionsEncoder.encode(options);
        LibGraalScope scope = new LibGraalScope(HotSpotJVMCIRuntime.runtime());
        return new IgvSupport(scope, compiler, HotSpotToSVMCalls.openDebugContext(getIsolateThread(), compiler.handle, compilation == null ? 0 : compilation.handle, encodedOptions));
    }

    private static final class IgvDumpChannel extends SVMObject implements WritableByteChannel {

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
            int written = HotSpotToSVMCalls.dumpChannelWrite(getIsolateThread(), handle, src, capacity, pos, limit);
            if (written > 0) {
                asBaseBuffer(src).position(pos + written);
            }
            return written;
        }

        @Override
        public boolean isOpen() {
            return HotSpotToSVMCalls.isDumpChannelOpen(getIsolateThread(), handle);
        }

        @Override
        public void close() throws IOException {
            HotSpotToSVMCalls.dumpChannelClose(getIsolateThread(), handle);
        }
    }

    private static final class Scope extends SVMObject implements Closeable {

        Scope(long handle) {
            super(handle);
        }

        @Override
        public void close() {
            HotSpotToSVMCalls.closeDebugContextScope(getIsolateThread(), handle);
        }
    }
}
