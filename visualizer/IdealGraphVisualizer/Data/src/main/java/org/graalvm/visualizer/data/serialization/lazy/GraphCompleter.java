/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data.serialization.lazy;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.BinarySource;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group.Feedback;
import jdk.graal.compiler.graphio.parsing.model.InputGraph.GraphData;

/**
 * Completes lazy graph
 */
final class GraphCompleter extends BaseCompleter<GraphData, LazyGraph> {
    public GraphCompleter(Env env, StreamEntry entry) {
        super(env, entry);
    }

    @Override
    protected GraphData hookData(GraphData data) {
        return data;
    }

    @Override
    protected GraphData createEmpty() {
        return new GraphData();
    }

    @Override
    protected GraphData load(ReadableByteChannel channel, int majorVersion, int minorVersion, Feedback feedback) throws IOException {
        GraphDocument doc = new GraphDocument();
        BinarySource bs = new BinarySource(channel, majorVersion, minorVersion, entry.getStart());
        GraphBuilder builder = new GraphBuilder(doc,
                toComplete, future(),
                entry, new ParseMonitorBridge(entry, feedback, bs));
        new BinaryReader(bs, builder).parse();
        return builder.data();
    }
}
