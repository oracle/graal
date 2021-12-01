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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jfr.events.EndChunkNativePeriodicEvents;
import com.oracle.svm.jfr.events.EveryChunkNativePeriodicEvents;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.OldObjectSample;
import jdk.jfr.internal.PrivateAccess;
import jdk.jfr.internal.SecuritySupport;
import jdk.jfr.internal.Utils;
import jdk.jfr.internal.jfc.JFC;

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

    Runnable startupHook() {
        return () -> {
            parseFlightRecorderLogging(SubstrateOptions.FlightRecorderLogging.getValue());
            if (SubstrateOptions.FlightRecorder.getValue()) {
                periodicEventSetup();
                initRecording();
            }
        };
    }

    Runnable shutdownHook() {
        return () -> {
            if (SubstrateOptions.FlightRecorder.getValue()) {
                // Everything should already have been torn down by JVM.destroyJFR(), which is
                // called in a shutdown hook. So in this method we should only unregister periodic
                // events.
                FlightRecorder.removePeriodicEvent(EveryChunkNativePeriodicEvents::emit);
                FlightRecorder.removePeriodicEvent(EndChunkNativePeriodicEvents::emit);
            }
        };
    }

    private static void parseFlightRecorderLogging(String option) {
        SubstrateJVM.getJfrLogging().parseConfiguration(option);
    }

    private static void periodicEventSetup() throws SecurityException {
        // The callbacks that are registered below, are invoked regularly to emit periodic native
        // events such as OSInformation or JVMInformation.
        FlightRecorder.addPeriodicEvent(EveryChunkNativePeriodicEvents.class, EveryChunkNativePeriodicEvents::emit);
        FlightRecorder.addPeriodicEvent(EndChunkNativePeriodicEvents.class, EndChunkNativePeriodicEvents::emit);
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

        try {
            if (Logger.shouldLog(LogTag.JFR_DCMD, LogLevel.DEBUG)) {
                Logger.log(LogTag.JFR_DCMD, LogLevel.DEBUG, "Executing DCmdStart: name=" + name +
                                ", settings=" + Arrays.asList(settings) +
                                ", delay=" + delay +
                                ", duration=" + duration +
                                ", disk=" + disk +
                                ", filename=" + path +
                                ", maxage=" + maxAge +
                                ", maxsize=" + maxSize +
                                ", dumponexit =" + dumpOnExit +
                                ", path-to-gc-roots=" + pathToGcRoots);
            }
            if (name != null) {
                try {
                    Integer.parseInt(name);
                    throw new Exception("Name of recording can't be numeric");
                } catch (NumberFormatException nfe) {
                    // ok, can't be mixed up with name
                }
            }

            if (duration == null && Boolean.FALSE.equals(dumpOnExit) && path != null) {
                throw new Exception("Filename can only be set for a time bound recording or if dumponexit=true. Set duration/dumponexit or omit filename.");
            }
            if (settings.length == 1 && settings[0].length() == 0) {
                throw new Exception("No settings specified. Use settings=none to start without any settings");
            }
            Map<String, String> s = new HashMap<>();
            for (String configName : settings) {
                try {
                    s.putAll(JFC.createKnown(configName).getSettings());
                } catch (FileNotFoundException e) {
                    throw new Exception("Could not find settings file'" + configName + "'", e);
                } catch (IOException | ParseException e) {
                    throw new Exception("Could not parse settings file '" + settings[0] + "'", e);
                }
            }

            OldObjectSample.updateSettingPathToGcRoots(s, pathToGcRoots);

            if (duration != null) {
                if (duration < 1000L * 1000L * 1000L) {
                    // to avoid typo, duration below 1s makes no sense
                    throw new Exception("Could not start recording, duration must be at least 1 second.");
                }
            }

            if (delay != null) {
                if (delay < 1000L * 1000L * 1000) {
                    // to avoid typo, delay shorter than 1s makes no sense.
                    throw new Exception("Could not start recording, delay must be at least 1 second.");
                }
            }

            Recording recording = new Recording();
            if (name != null) {
                recording.setName(name);
            }

            if (disk != null) {
                recording.setToDisk(disk.booleanValue());
            }
            recording.setSettings(s);
            SecuritySupport.SafePath safePath = null;

            if (path != null) {
                try {
                    if (dumpOnExit == null) {
                        // default to dumponexit=true if user specified filename
                        dumpOnExit = Boolean.TRUE;
                    }
                    Path p = Paths.get(path);
                    if (Files.isDirectory(p) && Boolean.TRUE.equals(dumpOnExit)) {
                        // Decide destination filename at dump time
                        // Purposely avoid generating filename in Recording#setDestination due to
                        // security concerns
                        PrivateAccess.getInstance().getPlatformRecording(recording).setDumpOnExitDirectory(new SecuritySupport.SafePath(p));
                    } else {
                        safePath = resolvePath(recording, path);
                        recording.setDestination(safePath.toPath());
                    }
                } catch (IOException | InvalidPathException e) {
                    recording.close();
                    throw new Exception("Could not start recording, not able to write to file: " + path, e);
                }
            }

            if (maxAge != null) {
                recording.setMaxAge(Duration.ofNanos(maxAge));
            }

            if (maxSize != null) {
                recording.setMaxSize(maxSize);
            }

            if (duration != null) {
                recording.setDuration(Duration.ofNanos(duration));
            }

            if (dumpOnExit != null) {
                recording.setDumpOnExit(dumpOnExit);
            }

            StringBuilder msg = new StringBuilder();

            if (delay != null) {
                Duration dDelay = Duration.ofNanos(delay);
                recording.scheduleStart(dDelay);

                msg.append("Recording " + recording.getId() + " scheduled to start in ");
                msg.append(Utils.formatTimespan(dDelay, " "));
                msg.append(".");
            } else {
                recording.start();
                msg.append("Started recording " + recording.getId() + ".");
            }

            if (recording.isToDisk() && duration == null && maxAge == null && maxSize == null) {
                msg.append(" No limit specified, using maxsize=250MB as default.");
                recording.setMaxSize(250 * 1024L * 1024L);
            }

            if (safePath != null && duration != null) {
                msg.append(" The result will be written to:");
                msg.append(System.getProperty("line.separator"));
                msg.append(getPath(safePath));
                msg.append(System.getProperty("line.separator"));
            }
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.INFO, msg.toString());
        } catch (Throwable e) {
            VMError.shouldNotReachHere(e);
        }
    }

    private static SecuritySupport.SafePath resolvePath(Recording recording, String filename) throws InvalidPathException {
        if (filename == null) {
            return makeGenerated(recording, Paths.get("."));
        }
        Path path = Paths.get(filename);
        if (Files.isDirectory(path)) {
            return makeGenerated(recording, path);
        }
        return new SecuritySupport.SafePath(path.toAbsolutePath().normalize());
    }

    private static SecuritySupport.SafePath makeGenerated(Recording recording, Path directory) {
        return new SecuritySupport.SafePath(directory.toAbsolutePath().resolve(Utils.makeFilename(recording)).normalize());
    }

    private static String getPath(SecuritySupport.SafePath path) {
        if (path == null) {
            return "N/A";
        }
        try {
            return getPath(SecuritySupport.getAbsolutePath(path).toPath());
        } catch (IOException ioe) {
            return getPath(path.toPath());
        }
    }

    private static String getPath(Path path) {
        try {
            return path.toAbsolutePath().toString();
        } catch (SecurityException e) {
            // fall back on filename
            return path.toString();
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
                    throw VMError.shouldNotReachHere("Unknown argument '" + keyVal[0] + "' in " + SubstrateOptions.StartFlightRecording.getName());
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
            throw VMError.shouldNotReachHere("Could not parse JFR argument '" + key.cmdLineKey + "=" + value + "'. Expected a boolean value.");
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
                throw VMError.shouldNotReachHere("Could not parse JFR argument '" + key.cmdLineKey + "=" + value + "'. " + e.getMessage());
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
                throw VMError.shouldNotReachHere("Could not parse JFR argument '" + key.cmdLineKey + "=" + value + "'. " + e.getMessage());
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
