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
package org.graalvm.compiler.truffle.compiler;

import java.io.IOException;
import java.util.Map;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.graphio.GraphOutput;

/**
 * Implementation of {@link TruffleDebugContext} in terms of {@link DebugContext}.
 */
public class TruffleDebugContextImpl implements TruffleDebugContext {
    public final DebugContext debugContext;

    public TruffleDebugContextImpl(final DebugContext debugContext) {
        this.debugContext = debugContext;
    }

    @Override
    public <G, N, M> GraphOutput<G, M> buildOutput(GraphOutput.Builder<G, N, M> builder) throws IOException {
        return debugContext.buildOutput(builder);
    }

    @Override
    public boolean isDumpEnabled() {
        return debugContext.isDumpEnabled(DebugContext.BASIC_LEVEL);
    }

    @Override
    public Map<Object, Object> getVersionProperties() {
        return DebugContext.addVersionProperties(null);
    }

    @Override
    public AutoCloseable scope(String name) {
        return debugContext.scope(name);
    }

    @Override
    public AutoCloseable scope(String name, Object context) {
        try {
            return debugContext.scope(name, context);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public void close() {
        debugContext.close();
    }

    @Override
    public void closeDebugChannels() {
        debugContext.closeDumpHandlers(false);
    }
}
