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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.openide.util.RequestProcessor.Task;

import jdk.graal.compiler.graphio.parsing.Builder;
import jdk.graal.compiler.graphio.parsing.ParseMonitor;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;

/**
 * @author sdedic
 */
public class BinaryReaderTest extends BinaryDataTestBase {

    public BinaryReaderTest(String name) {
        super(name);
    }

    class GroupCountingBuilder extends LazyModelBuilder {
        volatile int groupStart;
        volatile int groupEnd;
        volatile int groupContents;

        volatile int graphStart;
        volatile int graphEnd;
        volatile int graphContents;
        volatile boolean endCalled;

        public GroupCountingBuilder(GraphDocument rootDocument, Executor modelExecutor, ParseMonitor monitor) {
            super(rootDocument, monitor);
        }

        @Override
        public void end() {
            super.end();
            endCalled = true;
        }

        @Override
        public void endGroup() {
            groupEnd++;
            super.endGroup();
        }

        @Override
        public void startGroupContent() {
            groupContents++;
            super.startGroupContent();
        }

        @Override
        public Group startGroup() {
            groupStart++;
            return super.startGroup();
        }

        @Override
        public InputGraph endGraph() {
            graphEnd++;
            return super.endGraph();
        }

        @Override
        public InputGraph startGraph(int dumpId, String format, Object[] args) {
            graphStart++;
            return super.startGraph(dumpId, format, args);
        }

        @Override
        public void startGraphContents(InputGraph g) {
            graphContents++;
            super.startGraphContents(g);
        }
    }

    GroupCountingBuilder countingBuilder;

    private boolean countGroups;

    protected Builder createScanningTestBuilder() {
        if (!countGroups) {
            return super.createScanningTestBuilder();
        }
        return countingBuilder = new GroupCountingBuilder(checkDocument, this::run, null);
    }

    /**
     * Checks that groups are closed on EOF
     */
    public void testDanglingGroups() throws Exception {
        countGroups = true;
        loadData("bigv-1.0.bgv");

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        // freeze in an inner group
        file.freezeAt = 85000;
        Task t = PARALLEL_LOAD.post(() -> {
            try {
                // will freeze at 85000
                reader.parse();
            } catch (IOException ex) {
                thrown.set(ex);
            }
        });
        // wait for reader
        file.frozen.acquire();
        assertTrue(countingBuilder.graphStart > countingBuilder.groupEnd);
        file.eof = true;
        // release reader and let it get the EOF
        file.condition.release();

        t.waitFinished();
        // no exception propagated from the reader
        assertNull(thrown.get());
        assertSame(countingBuilder.graphStart, countingBuilder.graphEnd);
        assertSame(countingBuilder.groupStart, countingBuilder.groupEnd);
    }

    public void testGroupsAreClosed() throws Exception {
        countGroups = true;
        loadData("bigv-1.0.bgv");

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        // freeze in an inner group
        file.freezeAt = 85000;
        Task t = PARALLEL_LOAD.post(() -> {
            try {
                // will freeze at 85000
                reader.parse();
            } catch (IOException ex) {
                thrown.set(ex);
            }
        });
        // wait for reader
        file.frozen.acquire();
        assertTrue(countingBuilder.graphStart > countingBuilder.groupEnd);
        file.throwException = new IOException("Interrupted");
        // release reader and let it get the EOF
        file.condition.release();

        t.waitFinished();
        // no exception propagated from the reader
        assertNotNull(thrown.get());
        assertSame(countingBuilder.groupStart, countingBuilder.groupEnd);
    }

    public void testGraphsAreClosed() throws Exception {
        countGroups = true;
        loadData("bigv-1.0.bgv");

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        // freeze in an inner group
        file.freezeAt = 85000;
        Task t = PARALLEL_LOAD.post(() -> {
            try {
                // will freeze at 85000
                reader.parse();
            } catch (IOException ex) {
                thrown.set(ex);
            }
        });
        // wait for reader
        file.frozen.acquire();
        assertTrue(countingBuilder.graphStart > countingBuilder.groupEnd);
        file.throwException = new IOException("Interrupted");
        // release reader and let it get the EOF
        file.condition.release();

        t.waitFinished();
        // no exception propagated from the reader
        assertNotNull(thrown.get());
        assertSame(countingBuilder.graphStart, countingBuilder.graphEnd);
    }
}
