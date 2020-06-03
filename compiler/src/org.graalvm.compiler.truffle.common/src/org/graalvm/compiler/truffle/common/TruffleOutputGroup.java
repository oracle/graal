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
package org.graalvm.compiler.truffle.common;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.graalvm.graphio.GraphOutput;

/**
 * Helper class that adds a "Truffle::method_name" group around all graph dumps of a single Truffle
 * compilation, and makes sure the group is properly closed at the end of the compilation.
 */
public final class TruffleOutputGroup implements Closeable {

    /**
     * An unique id used to pair "Truffle" groups coming from different connections.
     */
    public static final String GROUP_ID = "truffle.compilation.id";

    private final GraphOutput<Void, ?> output;

    private TruffleOutputGroup(TruffleDebugContext debug, CompilableTruffleAST compilable, Map<Object, Object> properties) {
        String name = "Truffle::" + compilable.getName();
        GraphOutput<Void, ?> out = null;
        try {
            out = debug.buildOutput(GraphOutput.newBuilder(VoidGraphStructure.INSTANCE).protocolVersion(7, 0));
            Map<Object, Object> effectiveProperties;
            if (properties != null) {
                effectiveProperties = new HashMap<>(properties);
                effectiveProperties.putAll(debug.getVersionProperties());
            } else {
                effectiveProperties = debug.getVersionProperties();
            }
            out.beginGroup(null, name, name, null, 0, effectiveProperties);
        } catch (Throwable e) {
            if (out != null) {
                out.close();
                out = null;
            }
        }
        this.output = out;
    }

    @Override
    public void close() throws IOException {
        try {
            output.endGroup();
        } finally {
            output.close();
        }
    }

    /**
     * Opens a new "Truffle::method_name" group.
     *
     * @param debug the {@link TruffleDebugContext} used for dumping
     * @param compilable the compiled AST
     * @param properties additional group properties or {@code null}
     * @return the opened {@link TruffleOutputGroup}
     */
    public static TruffleOutputGroup open(TruffleDebugContext debug, CompilableTruffleAST compilable, Map<Object, Object> properties) {
        if (debug != null && debug.isDumpEnabled()) {
            return new TruffleOutputGroup(debug, compilable, properties);
        }
        return null;
    }
}
