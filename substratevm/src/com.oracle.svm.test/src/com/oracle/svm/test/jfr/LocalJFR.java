/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Red Hat, Inc.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of the Red Hat GraalVM Testing Suite (the suite).
 *
 * The suite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <https://www.gnu.org/licenses/>.
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
            System.out.println("Recording: " + recording);
        } else {
            Files.deleteIfExists(recording.getDestination());
        }
    }
}
