/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jfr.events.ClassLoadingStatistics;
import com.oracle.svm.jfr.events.InitialEnvironmentVariable;
import com.oracle.svm.jfr.events.InitialSystemProperty;
import com.oracle.svm.jfr.events.JVMInformation;
import com.oracle.svm.jfr.events.JavaThreadStatistics;
import com.oracle.svm.jfr.events.OSInformation;
import com.oracle.svm.jfr.events.PhysicalMemory;

import jdk.jfr.FlightRecorder;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;

/**
 * Called during VM startup and teardown. Also triggers the JFR argument parsing.
 */
public class JfrManager {
    private static final String DEFAULT_JFC_NAME = "default";

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrManager() {
    }

    @Fold
    static JfrManager get() {
        return ImageSingletons.lookup(JfrManager.class);
    }

    void setup() {
        parseFlightRecorderLogging(SubstrateOptions.FlightRecorderLogging.getValue());
        if (SubstrateOptions.FlightRecorder.getValue()) {
            periodicEventSetup();
            initRecording();
        }
    }

    void teardown() {
        if (SubstrateOptions.FlightRecorder.getValue()) {
            // Everything should already have been torn down by JVM.destroyJFR(), which is called in
            // a shutdown hook.
            assert !SubstrateJVM.isInitialized();
        }
    }

    private static void parseFlightRecorderLogging(String option) {
        SubstrateJVM.getJfrLogging().parseConfiguration(option);
    }

    private static void periodicEventSetup() throws SecurityException {
        FlightRecorder.addPeriodicEvent(InitialSystemProperty.class, InitialSystemProperty::emitSystemProperties);
        FlightRecorder.addPeriodicEvent(InitialEnvironmentVariable.class, InitialEnvironmentVariable::emitEnvironmentVariables);
        FlightRecorder.addPeriodicEvent(JVMInformation.class, JVMInformation::emitJVMInformation);
        FlightRecorder.addPeriodicEvent(OSInformation.class, OSInformation::emitOSInformation);
        FlightRecorder.addPeriodicEvent(PhysicalMemory.class, PhysicalMemory::emitPhysicalMemory);
        FlightRecorder.addPeriodicEvent(JavaThreadStatistics.class, JavaThreadStatistics::emitJavaThreadStats);
        FlightRecorder.addPeriodicEvent(ClassLoadingStatistics.class, ClassLoadingStatistics::emitClassLoadingStats);
    }

    private static void initRecording() {
        Map<JfrStartArgument, String> args = parseStartFlightRecording();
        String name = args.get(JfrStartArgument.Name);
        String[] settings = parseSettings(args);
        Long delay = parseDuration(args, JfrStartArgument.Delay);
        Long duration = parseDuration(args, JfrStartArgument.Duration);
        Boolean disk = parseBoolean(args, JfrStartArgument.Disk);
        String path = args.get(JfrStartArgument.Filename);
        Long maxAge = parseDuration(args, JfrStartArgument.MaxAge);
        Long maxSize = parseMaxSize(args, JfrStartArgument.MaxSize);
        Boolean dumpOnExit = parseBoolean(args, JfrStartArgument.DumpOnExit);
        Boolean pathToGcRoots = parseBoolean(args, JfrStartArgument.PathToGCRoots);

        Target_jdk_jfr_internal_dcmd_DCmdStart dcmdStart = new Target_jdk_jfr_internal_dcmd_DCmdStart();
        try {
            String msg = dcmdStart.execute(name, settings, delay, duration, disk, path, maxAge, maxSize,
                            dumpOnExit, pathToGcRoots);
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.INFO, msg);
        } catch (Throwable e) {
            VMError.shouldNotReachHere(e);
        }
    }

    private static Map<JfrStartArgument, String> parseStartFlightRecording() {
        Map<JfrStartArgument, String> optionsMap = new HashMap<>();
        String text = SubstrateOptions.StartFlightRecording.getValue();
        if (!text.isEmpty()) {
            JfrStartArgument[] possibleArguments = JfrStartArgument.values();
            String[] options = text.split(",");
            for (String option : options) {
                String[] keyVal = option.split("=");
                JfrStartArgument arg = findArgument(possibleArguments, keyVal[0]);
                if (arg == null) {
                    throw UserError.abort("Unknown argument '" + keyVal[0] + "' in " + SubstrateOptions.StartFlightRecording.getName());
                }
                optionsMap.put(arg, keyVal[1]);
            }
        }
        return optionsMap;
    }

    private static String[] parseSettings(Map<JfrStartArgument, String> args) throws UserException {
        String settings = args.get(JfrStartArgument.Settings);
        if (settings == null) {
            return new String[]{DEFAULT_JFC_NAME};
        } else if (settings.equals("none")) {
            return new String[0];
        } else {
            return settings.split(",");
        }
    }

    private static Boolean parseBoolean(Map<JfrStartArgument, String> args, JfrStartArgument key) throws IllegalArgumentException {
        String value = args.get(key);
        if (value == null) {
            return null;
        } else if ("true".equalsIgnoreCase(value)) {
            return true;
        } else if ("false".equalsIgnoreCase(value)) {
            return false;
        } else {
            throw UserError.abort("Could not parse JFR argument '" + key.cmdLineKey + "=" + value + "'. Expected a boolean value.");
        }
    }

    private static Long parseDuration(Map<JfrStartArgument, String> args, JfrStartArgument key) {
        String value = args.get(key);
        if (value != null) {
            try {
                int idx = indexOfFirstNonDigitCharacter(value);
                long time;
                try {
                    time = Long.parseLong(value.substring(0, idx));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Expected a number.");
                }

                if (idx == value.length()) {
                    // only accept missing unit if the value is 0
                    if (time != 0) {
                        throw new IllegalArgumentException("Unit is required.");
                    }
                    return 0L;
                }

                String unit = value.substring(idx);
                if ("ns".equals(unit)) {
                    return Duration.ofNanos(time).toNanos();
                } else if ("us".equals(unit)) {
                    return Duration.ofNanos(time * 1000).toNanos();
                } else if ("ms".equals(unit)) {
                    return Duration.ofMillis(time).toNanos();
                } else if ("s".equals(unit)) {
                    return Duration.ofSeconds(time).toNanos();
                } else if ("m".equals(unit)) {
                    return Duration.ofMinutes(time).toNanos();
                } else if ("h".equals(unit)) {
                    return Duration.ofHours(time).toNanos();
                } else if ("d".equals(unit)) {
                    return Duration.ofDays(time).toNanos();
                }
                throw new IllegalArgumentException("Unit is invalid.");
            } catch (IllegalArgumentException e) {
                throw UserError.abort("Could not parse JFR argument '" + key.cmdLineKey + "=" + value + "'. " + e.getMessage());
            }
        }
        return null;
    }

    private static Long parseMaxSize(Map<JfrStartArgument, String> args, JfrStartArgument key) {
        final String value = args.get(key);
        if (value != null) {
            try {
                int idx = indexOfFirstNonDigitCharacter(value);
                long number;
                try {
                    number = Long.parseLong(value.substring(0, idx));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Expected a number.");
                }

                // Missing unit, number is plain bytes
                if (idx == value.length()) {
                    return number;
                }

                final char unit = value.substring(idx).charAt(0);
                switch (unit) {
                    case 'k':
                    case 'K':
                        return number * 1024;
                    case 'm':
                    case 'M':
                        return number * 1024 * 1024;
                    case 'g':
                    case 'G':
                        return number * 1024 * 1024 * 1024;
                    default:
                        // Unknown unit, number is treated as plain bytes
                        return number;
                }
            } catch (IllegalArgumentException e) {
                throw UserError.abort("Could not parse JFR argument '" + key.cmdLineKey + "=" + value + "'. " + e.getMessage());
            }
        }
        return null;
    }

    private static int indexOfFirstNonDigitCharacter(String durationText) {
        int idx = 0;
        while (idx < durationText.length() && Character.isDigit(durationText.charAt(idx))) {
            idx++;
        }
        return idx;
    }

    private static JfrStartArgument findArgument(JfrStartArgument[] possibleArguments, String value) {
        for (JfrStartArgument arg : possibleArguments) {
            if (arg.cmdLineKey.equals(value)) {
                return arg;
            }
        }
        return null;
    }

    private enum JfrStartArgument {
        Name("name"),
        Settings("settings"),
        Delay("delay"),
        Duration("duration"),
        Filename("filename"),
        Disk("disk"),
        MaxAge("maxage"),
        MaxSize("maxsize"),
        DumpOnExit("dumponexit"),
        PathToGCRoots("path-to-gc-roots");

        private final String cmdLineKey;

        JfrStartArgument(String key) {
            this.cmdLineKey = key;
        }
    }
}
