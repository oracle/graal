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
package org.graalvm.tools.insight.test.heap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import org.graalvm.tools.insight.heap.HeapDump;
import org.graalvm.tools.insight.heap.HeapDump.Builder;
import org.graalvm.tools.insight.heap.HeapDump.ClassInstance;
import org.graalvm.tools.insight.heap.HeapDump.ObjectInstance;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HeapDumpTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void generateSampleHeapDump() throws Exception {
        File hprof = tmp.newFile();
        // @formatter:off // @replace regex='.*' replacement=''
        // @start region="org.graalvm.tools.insight.test.heap.HeapDumpTest#generateSampleHeapDump"
        Builder builder = HeapDump.newHeapBuilder(new FileOutputStream(hprof));
        builder.dumpHeap((heap) -> {
            final ClassInstance classActor = heap.newClass("cartoons.Actor").
                field("name", String.class).
                field("friend", Object.class).
                field("age", int.class).
                dumpClass();
            final ObjectInstance jerry = heap.newInstance(classActor).
                put("name", heap.dumpString("Jerry")).
                putInt("age", 47).
                // field 'friend' remains null
                dumpInstance();
            final ObjectInstance tom = heap.newInstance(classActor).
                put("name", heap.dumpString("Tom")).
                put("friend", jerry).
                putInt("age", 32).
                dumpInstance();
            final ClassInstance classMain = heap.newClass("cartoons.Main").
                field("tom", classActor).
                field("jerry", classActor).
                field("thread", java.lang.Thread.class).
                dumpClass();

            // @start region="org.graalvm.tools.insight.test.heap.HeapDumpTest#cyclic"
            HeapDump.InstanceBuilder mainBuilder = heap.newInstance(classMain);
            final ObjectInstance main = mainBuilder.id();
            mainBuilder.put("tom", tom).put("jerry", jerry);

            ObjectInstance cathingThread = heap.newThread("Catching Jerry").
                addStackFrame(classActor, "tom", "Actor.java", -1, jerry, tom, main).
                addStackFrame(classMain, "main", "Main.java", -1, main).
                dumpThread();

            mainBuilder.put("thread", cathingThread).dumpInstance();
            // @end region="org.graalvm.tools.insight.test.heap.HeapDumpTest#cyclic"
        });
        // @end region="org.graalvm.tools.insight.test.heap.HeapDumpTest#generateSampleHeapDump"
        // @formatter:on // @replace regex='.*' replacement=''
    }

    @Test
    public void errorOnWrongField() throws Exception {
        Builder builder = HeapDump.newHeapBuilder(new ByteArrayOutputStream());
        builder.dumpHeap((heap) -> {
            ClassInstance classPerson = heap.newClass("heapdemo.Counter").field("count", int.class).dumpClass();
            try {
                ObjectInstance zeroCounter = heap.newInstance(classPerson).putInt("value", 0).dumpInstance();
                fail("put should fail, but it yielded " + zeroCounter);
            } catch (IllegalArgumentException ex) {
                assertEquals("Unknown field 'value'", ex.getMessage());
            }
            try {
                ObjectInstance zeroCounter = heap.newInstance(classPerson).putDouble("count", 0.0).dumpInstance();
                fail("put should fail, but it yielded " + zeroCounter);
            } catch (IllegalArgumentException ex) {
                assertEquals("Wrong type for field 'count'", ex.getMessage());
            }
        });
    }
}
