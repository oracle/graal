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
package com.oracle.svm.core.jfr;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jfr.events.EndChunkNativePeriodicEvents;
import com.oracle.svm.core.jfr.events.EveryChunkNativePeriodicEvents;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.UserError.UserException;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.OldObjectSample;
import jdk.jfr.internal.Options;
import jdk.jfr.internal.PrivateAccess;
import jdk.jfr.internal.Repository;
import jdk.jfr.internal.SecuritySupport;
import jdk.jfr.internal.jfc.JFC;

/**
 * Called during VM startup and teardown. Also triggers the JFR argument parsing.
 */
public class JfrManager {
    private static final String DEFAULT_JFC_NAME = "default";

    @Platforms(Platform.HOSTED_ONLY.class) //
    final boolean hostedEnabled;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrManager(boolean hostedEnabled) {
        this.hostedEnabled = hostedEnabled;
    }

    @Fold
    public static JfrManager get() {
        return ImageSingletons.lookup(JfrManager.class);
    }

    public static RuntimeSupport.Hook initializationHook() {
        /* Parse arguments early on so that we can tear down the isolate more easily if it fails. */
        return isFirstIsolate -> {
            parseFlightRecorderLogging();
            parseFlightRecorderOptions();
        };
    }

    public static RuntimeSupport.Hook startupHook() {
        return isFirstIsolate -> {
            periodicEventSetup();

            boolean startRecording = SubstrateOptions.FlightRecorder.getValue() || !SubstrateOptions.StartFlightRecording.getValue().isEmpty();
            if (startRecording) {
                initRecording();
            }
        };
    }

    private static void parseFlightRecorderOptions() {
        Map<JfrArgument, String> optionsArgs = parseJfrOptions(SubstrateOptions.FlightRecorderOptions, FlightRecorderOptionsArgument.values());
        Long globalBufferSize = parseMaxSize(optionsArgs, FlightRecorderOptionsArgument.GlobalBufferSize);
        Long maxChunkSize = parseMaxSize(optionsArgs, FlightRecorderOptionsArgument.MaxChunkSize);
        Long memorySize = parseMaxSize(optionsArgs, FlightRecorderOptionsArgument.MemorySize);
        Integer oldObjectQueueSize = parseInteger(optionsArgs, FlightRecorderOptionsArgument.OldObjectQueueSize);
        String repositoryPath = optionsArgs.get(FlightRecorderOptionsArgument.RepositoryPath);
        Integer stackDepth = parseInteger(optionsArgs, FlightRecorderOptionsArgument.StackDepth);
        Boolean preserveRepository = parseBoolean(optionsArgs, FlightRecorderOptionsArgument.PreserveRepository);
        Long threadBufferSize = parseMaxSize(optionsArgs, FlightRecorderOptionsArgument.ThreadBufferSize);

        if (globalBufferSize != null) {
            Options.setGlobalBufferSize(globalBufferSize);
        }

        if (maxChunkSize != null) {
            Options.setMaxChunkSize(maxChunkSize);
        }

        if (memorySize != null) {
            Options.setMemorySize(memorySize);
        }

        if (oldObjectQueueSize != null) {
            if (oldObjectQueueSize >= 0) {
                SubstrateJVM.getOldObjectProfiler().configure(oldObjectQueueSize);
            } else {
                throw argumentParsingFailed(FlightRecorderOptionsArgument.OldObjectQueueSize.getCmdLineKey() + " must be greater or equal 0.");
            }
        }

        if (repositoryPath != null) {
            try {
                SecuritySupport.SafePath repositorySafePath = new SecuritySupport.SafePath(repositoryPath);
                Repository.getRepository().setBasePath(repositorySafePath);
            } catch (Throwable e) {
                throw argumentParsingFailed("Could not use " + repositoryPath + " as repository. " + e.getMessage(), e);
            }
        }

        if (stackDepth != null) {
            Options.setStackDepth(stackDepth);
        }

        if (preserveRepository != null) {
            Options.setPreserveRepository(preserveRepository);
        }

        if (threadBufferSize != null) {
            Options.setThreadBufferSize(threadBufferSize);
        }
    }

    public static RuntimeSupport.Hook shutdownHook() {
        return isFirstIsolate -> {
            /*
             * Everything should already have been torn down by JVM.destroyJFR(), which is called in
             * a shutdown hook. So in this method we should only unregister periodic events.
             */
            FlightRecorder.removePeriodicEvent(EveryChunkNativePeriodicEvents::emit);
            FlightRecorder.removePeriodicEvent(EndChunkNativePeriodicEvents::emit);
        };
    }

    private static void parseFlightRecorderLogging() {
        String option = SubstrateOptions.FlightRecorderLogging.getValue();
        SubstrateJVM.getLogging().parseConfiguration(option);
    }

    private static void periodicEventSetup() throws SecurityException {
        // The callbacks that are registered below, are invoked regularly to emit periodic native
        // events such as OSInformation or JVMInformation.
        FlightRecorder.addPeriodicEvent(EveryChunkNativePeriodicEvents.class, EveryChunkNativePeriodicEvents::emit);
        FlightRecorder.addPeriodicEvent(EndChunkNativePeriodicEvents.class, EndChunkNativePeriodicEvents::emit);
    }

    private static void initRecording() {
        Map<JfrArgument, String> startArgs = parseJfrOptions(SubstrateOptions.StartFlightRecording, JfrStartArgument.values());
        String name = startArgs.get(JfrStartArgument.Name);
        String[] settings = parseSettings(startArgs);
        Long delay = parseDuration(startArgs, JfrStartArgument.Delay);
        Long duration = parseDuration(startArgs, JfrStartArgument.Duration);
        Boolean disk = parseBoolean(startArgs, JfrStartArgument.Disk);
        String path = startArgs.get(JfrStartArgument.Filename);
        Long maxAge = parseDuration(startArgs, JfrStartArgument.MaxAge);
        Long maxSize = parseMaxSize(startArgs, JfrStartArgument.MaxSize);
        Boolean dumpOnExit = parseBoolean(startArgs, JfrStartArgument.DumpOnExit);
        Boolean pathToGcRoots = parseBoolean(startArgs, JfrStartArgument.PathToGCRoots);

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
                throw argumentParsingFailed("Name of recording can't be numeric");
            } catch (NumberFormatException nfe) {
                // ok, can't be mixed up with name
            }
        }

        if (duration == null && Boolean.FALSE.equals(dumpOnExit) && path != null) {
            throw argumentParsingFailed("Filename can only be set for a time bound recording or if dumponexit=true. Set duration/dumponexit or omit filename.");
        }
        if (settings.length == 1 && settings[0].length() == 0) {
            throw argumentParsingFailed("No settings specified. Use settings=none to start without any settings");
        }
        Map<String, String> s = new HashMap<>();
        for (String configName : settings) {
            try {
                s.putAll(JFC.createKnown(configName).getSettings());
            } catch (FileNotFoundException e) {
                throw argumentParsingFailed("Could not find settings file'" + configName + "'", e);
            } catch (IOException | ParseException e) {
                throw argumentParsingFailed("Could not parse settings file '" + settings[0] + "'", e);
            }
        }

        OldObjectSample.updateSettingPathToGcRoots(s, pathToGcRoots);

        if (duration != null) {
            if (duration < 1000L * 1000L * 1000L) {
                // to avoid typo, duration below 1s makes no sense
                throw argumentParsingFailed("Could not start recording, duration must be at least 1 second.");
            }
        }

        if (delay != null) {
            if (delay < 1000L * 1000L * 1000) {
                // to avoid typo, delay shorter than 1s makes no sense.
                throw argumentParsingFailed("Could not start recording, delay must be at least 1 second.");
            }
        }

        Recording recording = new Recording();
        if (name != null) {
            recording.setName(name);
        }

        if (disk != null) {
            recording.setToDisk(disk);
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
                if (Files.isDirectory(p) && (JavaVersionUtil.JAVA_SPEC >= 23 || Boolean.TRUE.equals(dumpOnExit))) {
                    // Decide destination filename at dump time
                    // Purposely avoid generating filename in Recording#setDestination due to
                    // security concerns
                    JfrJdkCompatibility.setDumpDirectory(PrivateAccess.getInstance().getPlatformRecording(recording), new SecuritySupport.SafePath(p));
                } else {
                    safePath = resolvePath(recording, path);
                    recording.setDestination(safePath.toPath());
                }
            } catch (IOException | InvalidPathException e) {
                recording.close();
                throw new RuntimeException("Could not start recording, not able to write to file: " + path, e);
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

            msg.append("Recording ");
            msg.append(recording.getId());
            msg.append(" scheduled to start in ");
            msg.append(JfrJdkCompatibility.formatTimespan(dDelay, " "));
            msg.append(".");
        } else {
            recording.start();
            msg.append("Started recording ");
            msg.append(recording.getId());
            msg.append(".");
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
        return new SecuritySupport.SafePath(directory.toAbsolutePath().resolve(JfrJdkCompatibility.makeFilename(recording)).normalize());
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

    private static Map<JfrArgument, String> parseJfrOptions(RuntimeOptionKey<String> runtimeOptionKey, JfrArgument[] possibleArguments) {
        Map<JfrArgument, String> optionsMap = new HashMap<>();
        String userInput = runtimeOptionKey.getValue();
        if (!userInput.isEmpty()) {
            String[] options = userInput.split(",");
            for (String option : options) {
                String[] keyVal = option.split("=");
                JfrArgument arg = findArgument(possibleArguments, keyVal[0]);
                if (arg == null) {
                    throw argumentParsingFailed("Unknown argument '" + keyVal[0] + "' in " + runtimeOptionKey.getName());
                }
                optionsMap.put(arg, keyVal[1]);
            }
        }
        return optionsMap;
    }

    private static String[] parseSettings(Map<JfrArgument, String> args) throws UserException {
        String settings = args.get(JfrStartArgument.Settings);
        if (settings == null) {
            return new String[]{DEFAULT_JFC_NAME};
        } else if (settings.equals("none")) {
            return new String[0];
        } else {
            return settings.split(",");
        }
    }

    @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "null allowed as return value")
    private static Boolean parseBoolean(Map<JfrArgument, String> args, JfrArgument key) throws IllegalArgumentException {
        String value = args.get(key);
        if (value == null) {
            return null;
        } else if ("true".equalsIgnoreCase(value)) {
            return true;
        } else if ("false".equalsIgnoreCase(value)) {
            return false;
        } else {
            throw argumentParsingFailed("Could not parse JFR argument '" + key.getCmdLineKey() + "=" + value + "'. Expected a boolean value.");
        }
    }

    private static Long parseDuration(Map<JfrArgument, String> args, JfrStartArgument key) {
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
                return switch (unit) {
                    case "ns" -> Duration.ofNanos(time).toNanos();
                    case "us" -> Duration.ofNanos(time * 1000).toNanos();
                    case "ms" -> Duration.ofMillis(time).toNanos();
                    case "s" -> Duration.ofSeconds(time).toNanos();
                    case "m" -> Duration.ofMinutes(time).toNanos();
                    case "h" -> Duration.ofHours(time).toNanos();
                    case "d" -> Duration.ofDays(time).toNanos();
                    default -> throw new IllegalArgumentException("Unit is invalid.");
                };
            } catch (IllegalArgumentException e) {
                throw argumentParsingFailed("Could not parse JFR argument '" + key.cmdLineKey + "=" + value + "'. " + e.getMessage());
            }
        }
        return null;
    }

    private static Integer parseInteger(Map<JfrArgument, String> args, JfrArgument key) {
        String value = args.get(key);
        if (value != null) {
            try {
                return Integer.valueOf(value);
            } catch (Throwable e) {
                throw argumentParsingFailed("Could not parse JFR argument '" + key.getCmdLineKey() + "=" + value + "'. " + e.getMessage());
            }
        }
        return null;
    }

    private static Long parseMaxSize(Map<JfrArgument, String> args, JfrArgument key) {
        String value = args.get(key);
        if (value == null) {
            return null;
        }

        try {
            int idx = indexOfFirstNonDigitCharacter(value);
            long number;
            try {
                number = Long.parseLong(value.substring(0, idx));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected a positive number.");
            }

            // Missing unit, number is plain bytes
            if (idx == value.length()) {
                return number;
            }

            final char unit = value.substring(idx).charAt(0);
            return switch (unit) {
                case 'k', 'K' -> number * 1024;
                case 'm', 'M' -> number * 1024 * 1024;
                case 'g', 'G' -> number * 1024 * 1024 * 1024;
                default -> number; // Unknown unit, number is treated as plain bytes
            };
        } catch (IllegalArgumentException e) {
            throw argumentParsingFailed("Could not parse JFR argument '" + key.getCmdLineKey() + "=" + value + "'. " + e.getMessage());
        }
    }

    private static int indexOfFirstNonDigitCharacter(String durationText) {
        int idx = 0;
        while (idx < durationText.length() && Character.isDigit(durationText.charAt(idx))) {
            idx++;
        }
        return idx;
    }

    private static JfrArgument findArgument(JfrArgument[] possibleArguments, String value) {
        for (JfrArgument arg : possibleArguments) {
            if (arg.getCmdLineKey().equals(value)) {
                return arg;
            }
        }
        return null;
    }

    private static RuntimeException argumentParsingFailed(String message) {
        throw new JfrArgumentParsingFailed(message);
    }

    private static RuntimeException argumentParsingFailed(String message, Throwable cause) {
        throw new JfrArgumentParsingFailed(message, cause);
    }

    private interface JfrArgument {
        String getCmdLineKey();
    }

    private enum JfrStartArgument implements JfrArgument {
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

        @Override
        public String getCmdLineKey() {
            return cmdLineKey;
        }
    }

    private enum FlightRecorderOptionsArgument implements JfrArgument {
        GlobalBufferSize("globalbuffersize"),
        MaxChunkSize("maxchunksize"),
        MemorySize("memorysize"),
        OldObjectQueueSize("old-object-queue-size"),
        RepositoryPath("repository"),
        StackDepth("stackdepth"),
        ThreadBufferSize("threadbuffersize"),
        PreserveRepository("preserve-repository");

        private final String cmdLineKey;

        FlightRecorderOptionsArgument(String key) {
            this.cmdLineKey = key;
        }

        @Override
        public String getCmdLineKey() {
            return cmdLineKey;
        }
    }

    private static class JfrArgumentParsingFailed extends RuntimeException {
        @Serial private static final long serialVersionUID = -1050173145647068124L;

        JfrArgumentParsingFailed(String message, Throwable cause) {
            super(message, cause);
        }

        JfrArgumentParsingFailed(String message) {
            super(message);
        }
    }
}
