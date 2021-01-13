/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.remote;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.jdk.jfr.JfrOptions;
import com.oracle.svm.core.jdk.jfr.logging.JfrLogger;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

import java.io.IOException;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

/*
 * Handlers the startup and teardown of JFR when run from either the command line or remote interface.
 * Implemented by standard JFR calls as user code might do.
 */

public class JfrAutoSessionManager {

    private static final SimpleDateFormat fmt = new SimpleDateFormat("yyyy_MM_DD_HH_mm_ss");
    private static final String DEFAULT_JFR_CONFIG = "default";

    private final Configuration jfrConfiguration;
    private String filename;
    private Recording recording;
    private static JfrAutoSessionManager instance;

    static void out(String msg) {
        JfrLogger.logInfo("JFR.AutoStart " + msg);
    }

    JfrAutoSessionManager() throws IOException, ParseException {
        this(DEFAULT_JFR_CONFIG);
    }

    JfrAutoSessionManager(String configName) throws IOException, ParseException {
        out("JfrAutoSessionManager(" + configName + ")");
        jfrConfiguration = Configuration.getConfiguration(configName);
    }

    /*
     *   JFR-TODO if these timers expire after the main program has ended, the process hangs.
     */
    void startSession(String fn, long delayMilliseconds, long durationMilliseconds) {
        if (delayMilliseconds > 0) {
            out("scheduling JFR start in " + delayMilliseconds + "ms.");
            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            startSession(fn, 0, durationMilliseconds);
                        }
                    },
                    delayMilliseconds
            );
        } else {
            out("starting JFR session(" + fn + ")");
            filename = fn;
            recording = new Recording(jfrConfiguration);
            recording.start();
            try {
                recording.setDestination(Paths.get(filename));
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (durationMilliseconds > 0) {
                out("scheduling JFR stop in " + durationMilliseconds + "ms.");
                new java.util.Timer().schedule(
                        new java.util.TimerTask() {
                            @Override
                            public void run() {
                                stopSession(true, false);
                            }
                        },
                        durationMilliseconds
                );
            }
        }
    }

    void stopSession(boolean write, boolean clear) {
        out("stopSession(" + write + ", " + clear + ") start");
        try {
            if (recording != null) {
                recording.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        out("stopSession(" + write + ", " + clear + ") stop");
    }

    public static void startupHook() {
        out("Jfr startupHook() - start");
        out(dumpJfrOptions(", "));
        assert instance == null;
        if (JfrOptions.getStartRecordingAutomatically()) {
            try {
                // JFR-TODO fix JfrOptions to parse configuration name instead of using default "default"
                instance = new JfrAutoSessionManager();
                String fn = (JfrOptions.getRecordingFileName() != null && JfrOptions.getRecordingFileName().length() > 0) ? JfrOptions.getRecordingFileName() : getDefaultJfrFilename();
                instance.startSession(fn, JfrOptions.getRecordingDelay(), JfrOptions.getDuration());
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        } else {
            out("Jfr startupHook() - no auto start");
        }
        out("Jfr startupHook() - end");
    }

    public static void shutdownHook() {
        out("Jfr shutdownHook() - start instance is " + (instance == null ? "(null)" : instance.filename));
        if (instance != null) {
            instance.stopSession(true, true);
        }
        out("Jfr shutdownHook() - end");
    }

    @SuppressWarnings("unused")
    private static String asString(Configuration cfg, String sep) {
        return "name=\"" + cfg.getName() + "\"" + sep +
                "label=\"" + cfg.getLabel() + "\"" + sep +
                "description=\"" + cfg.getDescription() + "\"" + sep +
                "settings=\"" + cfg.getSettings() + "\"";
    }

    public static String dumpJfrOptions(String sep) {
        return "filename=\"" + JfrOptions.getRecordingFileName() + "\"" + sep +
                "recordingName=\"" + JfrOptions.getRecordingName() + "\"" + sep +
                "recordingSettingsFile=\"" + JfrOptions.getRecordingSettingsFile() + "\"" + sep +
                "repositoryLocation=\"" + JfrOptions.getRepositoryLocation() + "\"" + sep +
                "dumpOnExit=\"" + JfrOptions.getDumpOnExit() + "\"" + sep +
                "persistToDisk=\"" + JfrOptions.isPersistedToDisk() + sep +
                "pathToGcRoots=\"" + JfrOptions.trackPathToGcRoots() + "\"" + sep +
                "sampleThreads=\"" + JfrOptions.isSampleThreadsEnabled() + "\"" + sep +
                "retransform=\"" + JfrOptions.isRetransformEnabled() + "\"" + sep +
                "sampleProtection=\"" + JfrOptions.isSampleProtectionEnabled() + "\"" + sep +
                "delay=\"" + JfrOptions.getRecordingDelay() + "\"" + sep +
                "duration=\"" + JfrOptions.getDuration() + "\"" + sep +
                "maxAge=\"" + JfrOptions.getMaxAge() + "\"" + sep +
                "maxChunkSize=\"" + JfrOptions.getMaxChunkSize() + "\"" + sep +
                "globalBufferSize=\"" + JfrOptions.getGlobalBufferSize() + "\"" + sep +
                "memorySize=\"" + JfrOptions.getMemorySize() + "\"" + sep +
                "threadBufferSize=\"" + JfrOptions.getThreadBufferSize() + "\"" + sep +
                "globalBufferCount=\"" + JfrOptions.getGlobalBufferCount() + "\"" + sep +
                "oldObjectQueueSize=\"" + JfrOptions.getObjectQueueSize() + "\"" + sep +
                "maxRecordingSize=\"" + JfrOptions.getMaxRecordingSize() + "\"" + sep +
                "stackDepth=\"" + JfrOptions.getStackDepth() + "\"" + sep +
                "logLevel=\"" + JfrOptions.getLogLevel() + "\"";
    }

    /* NOTE: this changes every time it is called */
    /* JFR-TODO - should this be the time of the start recording command? */
    public static String getDefaultJfrFilename() {
        String program = SubstrateOptions.Name.getValue().replaceAll("[:/\\\\]", "_");
        if (program.isEmpty()) {
            program = SubstrateOptions.Class.getValue().replaceAll("[$/]", "_");
        }
        long pid = ProcessHandle.current().pid();
        int id = 1; /* JFR-TODO: don't know how to calculate id; maybe go to filesystem? */
        return String.format("%s-pid-%d-id-%d-%s.jfr", program, pid, id, fmt.format(Date.from(Instant.now())));
    }
}
