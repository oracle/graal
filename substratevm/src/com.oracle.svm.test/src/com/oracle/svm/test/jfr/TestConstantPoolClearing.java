/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.List;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.junit.Test;

import com.oracle.svm.test.jfr.events.ClassEvent;
import com.oracle.svm.test.jfr.utils.JfrFileParser;
import com.oracle.svm.core.jfr.SubstrateJVM;

import jdk.jfr.consumer.RecordedEvent;

/**
 * This test ensures that constant pool epochData is properly cleared before beginning a new chunk
 * or recording. Upon a new chunk, a constant pool epochData starts with residual entries, while the
 * serialized data buffer does not, it will fail constant pool verification in {@link JfrFileParser}
 */
public class TestConstantPoolClearing extends AbstractJfrTest {
    private Recording recording;
    Path preTestFile;

    @Override
    public void startRecording(Configuration config) throws Throwable {
        long id = new Random().nextLong(0, Long.MAX_VALUE);
        preTestFile = File.createTempFile(getClass().getName() + "-" + id, ".jfr").toPath();
        startRecording(config, preTestFile);
    }

    private void startRecording(Configuration config, Path destination) throws IOException {
        recording = new Recording(config);
        recording.setDestination(destination);
        // Turn off flushing so we can control it precisely.
        Map<String, String> settings = new HashMap<>();
        settings.put("flush-interval", String.valueOf(Long.MAX_VALUE));
        recording.setSettings(settings);
        recording.enable("com.jfr.Class");
        recording.start();
    }

    @Override
    public void stopRecording() {
        recording.stop();
        recording.close();
    }

    @Override
    public String[] getTestedEvents() {
        return new String[]{"com.jfr.Class"};
    }

    @Override
    protected void validateEvents(List<RecordedEvent> events) throws Throwable {
        assertEquals(1, events.size());
    }

    @Test
    public void test() throws Exception {
        // Epoch 1 ---------------
        ClassEvent event = new ClassEvent();
        event.clazz = TestConstantPoolClearing.class;
        event.commit();
        // Force a flush. This clears the constant pool serialized data buffers but not the
        // deduplication maps.
        SubstrateJVM.get().flush();
        // No events should be committed between the previous flush and the below chunk rotation.
        recording.dump(preTestFile);

        // Epoch 2 ---------------
        // Force another chunk rotation and end recording.
        recording.stop();
        recording.close();
        // A new recording is started, now we can test if it has completely fresh constant pool
        // epochData.
        startRecording(Configuration.getConfiguration("default"), jfrFile);

        // Epoch 1 ---------------
        // Test for the case where the constant pool epochData starts with residual entries,
        // but the serialized data buffer does not.
        ClassEvent eventWithDanglingReference = new ClassEvent();
        eventWithDanglingReference.clazz = TestConstantPoolClearing.class;
        eventWithDanglingReference.commit();
    }

}
