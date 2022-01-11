/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Red Hat Inc. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

public class LocalJFR implements JFR {

    @Override
    public Recording startRecording(String recordingName) throws Exception {
        return startRecording(new Recording(), recordingName);
    }

    @Override
    public Recording startRecording(String recordingName, String configName) throws Exception {
        Configuration c = Configuration.getConfiguration(configName);
        return startRecording(new Recording(c), recordingName);
    }

    private static Recording startRecording(Recording recording, String name) throws Exception {
        long id = recording.getId();

        Path destination = File.createTempFile(name + "-" + id, ".jfr").toPath();
        recording.setDestination(destination);

        recording.start();
        return recording;
    }

    @Override
    public void endRecording(Recording recording) {
        recording.stop();
        recording.close();
    }

    @Override
    public void cleanupRecording(Recording recording) throws IOException {
        String debugRecording = System.getenv("DEBUG_RECORDING");
        if (debugRecording != null && !"false".equals(debugRecording)) {
            System.out.println("Recording: " + recording.getDestination());
        } else {
            Files.deleteIfExists(recording.getDestination());
        }
    }
}
