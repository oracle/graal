/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.dcmd;

import com.oracle.svm.core.dcmd.AbstractDcmd;
import com.oracle.svm.core.dcmd.DcmdOption;
import com.oracle.svm.core.dcmd.DcmdParseException;
import com.oracle.svm.core.jfr.JfrArgumentParser.JfrArgument;
import com.oracle.svm.core.jfr.JfrArgumentParser.JfrArgumentParsingFailed;
import com.oracle.svm.core.jfr.JfrManager;
import com.oracle.svm.core.util.BasedOnJDKFile;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.internal.PlatformRecorder;
import jdk.jfr.internal.PlatformRecording;
import jdk.jfr.internal.SecuritySupport;
import jdk.jfr.internal.WriteableUserPath;
import jdk.jfr.internal.PrivateAccess;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;

import static com.oracle.svm.core.jfr.JfrArgumentParser.DumpArgument;
import static com.oracle.svm.core.jfr.JfrArgumentParser.parseBoolean;
import static com.oracle.svm.core.jfr.JfrArgumentParser.parseDuration;
import static com.oracle.svm.core.jfr.JfrArgumentParser.parseJfrOptions;
import static com.oracle.svm.core.jfr.JfrArgumentParser.parseMaxSize;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class JfrDumpDcmd extends AbstractDcmd {

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/jdk.jfr/share/classes/jdk/jfr/internal/dcmd/DCmdDump.java#L197-L277") //
    public JfrDumpDcmd() {
        this.options = new DcmdOption[]{
                        new DcmdOption("filename", "Name of the file to which the flight recording data is\n" +
                                        "                   dumped. If no filename is given, a filename is generated from the PID\n" +
                                        "                   and the current date. The filename may also be a directory in which\n" +
                                        "                   case, the filename is generated from the PID and the current date in\n" +
                                        "                   the specified directory.", false, null),
                        new DcmdOption("name", "Name of the recording. If no name is given, data from all\n" +
                                        "                   recordings is dumped.", false, null),
                        new DcmdOption("maxage", "Length of time for dumping the flight recording data to a\n" +
                                        "                   file. (INTEGER followed by 's' for seconds 'm' for minutes or 'h' for\n" +
                                        "                   hours)", false, null),
                        new DcmdOption("maxsize", "Maximum size for the amount of data to dump from a flight\n" +
                                        "                   recording in bytes if one of the following suffixes is not used:\n" +
                                        "                   'm' or 'M' for megabytes OR 'g' or 'G' for gigabytes.", false, null),
                        new DcmdOption("path-to-gc-roots", " Flag for saving the path to garbage collection (GC) roots\n" +
                                        "                   at the time the recording data is dumped. The path information is\n" +
                                        "                   useful for finding memory leaks but collecting it can cause the\n" +
                                        "                   application to pause for a short period of time. Turn on this flag\n" +
                                        "                   only when you have an application that you suspect has a memory\n" +
                                        "                   leak. (BOOLEAN)", false, null),
                        new DcmdOption("begin", "Specify the time from which recording data will be included\n" +
                                        "                  in the dump file. The format is specified as local time.\n" +
                                        "                  (STRING)", false, null),
                        new DcmdOption("end", "Specify the time to which recording data will be included\n" +
                                        "                  in the dump file. The format is specified as local time.\n" +
                                        "                  (STRING)", false, null)
        };
        this.examples = new String[]{
                        "$ jcmd <pid> JFR.dump",
                        "$ jcmd <pid> JFR.dump filename=recording.jfr",
                        "$ jcmd <pid> JFR.dump name=1 filename=/recordings/recording.jfr",
                        "$ jcmd <pid> JFR.dump maxage=1h maxsize=50M",
                        "$ jcmd <pid> JFR.dump begin=-1h",
                        "$ jcmd <pid> JFR.dump begin=-15m end=-5m",
                        "$ jcmd <pid> JFR.dump begin=13:15 end=21:30:00"
        };
        this.name = "JFR.dump";
        this.description = "Copies contents of a JFR recording to file. Either the name or the recording id must be specified.";
        this.impact = "low";
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/jdk.jfr/share/classes/jdk/jfr/internal/dcmd/DCmdDump.java#L55-L118") //
    @Override
    public String parseAndExecute(String[] arguments) throws DcmdParseException {
        String recordingName;
        String filename;
        Long maxAge;
        Long maxSize;
        Boolean pathToGcRoots;
        String begin;
        String end;
        try {
            Map<JfrArgument, String> dumpArgs = parseJfrOptions(Arrays.copyOfRange(arguments, 1, arguments.length), DumpArgument.values());
            recordingName = dumpArgs.get(DumpArgument.Name);
            filename = dumpArgs.get(DumpArgument.Filename);
            maxAge = parseDuration(dumpArgs, DumpArgument.MaxAge);
            maxSize = parseMaxSize(dumpArgs, DumpArgument.MaxSize);
            pathToGcRoots = parseBoolean(dumpArgs, DumpArgument.PathToGCRoots);
            begin = dumpArgs.get(DumpArgument.Begin);
            end = dumpArgs.get(DumpArgument.End);
        } catch (JfrArgumentParsingFailed e) {
            throw new DcmdParseException(e.getMessage());
        }

        if (FlightRecorder.getFlightRecorder().getRecordings().isEmpty()) {
            throw new DcmdParseException("No recordings to dump from. Use JFR.start to start a recording.");
        }

        if (maxAge != null) {
            if (maxAge < 0) {
                throw new DcmdParseException("Dump failed, maxage can't be negative.");
            }
            if (maxAge == 0) {
                maxAge = Long.MAX_VALUE / 2; // a high value that won't overflow
            }
        }

        if (maxSize != null) {
            if (maxSize < 0) {
                throw new DcmdParseException("Dump failed, maxsize can't be negative.");
            }
            if (maxSize == 0) {
                maxSize = Long.MAX_VALUE / 2; // a high value that won't overflow
            }
        }

        Instant beginTime = parseTime(begin, "begin");
        Instant endTime = parseTime(end, "end");

        if (beginTime != null && endTime != null) {
            if (endTime.isBefore(beginTime)) {
                throw new DcmdParseException("Dump failed, begin must precede end.");
            }
        }

        Duration duration;
        if (maxAge != null) {
            duration = Duration.ofNanos(maxAge);
            beginTime = Instant.now().minus(duration);
        }

        Recording recording = null;
        if (recordingName != null) {
            for (Recording rec : FlightRecorder.getFlightRecorder().getRecordings()) {
                if (rec.getName().equals(recordingName)) {
                    recording = rec;
                    break;
                }
            }
            if (recording == null) {
                throw new DcmdParseException("Could not find specified recording with name: " + recordingName);
            }
        }
        PlatformRecorder recorder = PrivateAccess.getInstance().getPlatformRecorder();
        try {
            synchronized (recorder) {
                dump(recorder, recording, filename, maxSize, pathToGcRoots, beginTime, endTime);
            }
        } catch (IOException | InvalidPathException e) {
            throw new DcmdParseException("Dump failed. Could not copy recording data.");
        }
        return "Dump created.";
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/jdk.jfr/share/classes/jdk/jfr/internal/dcmd/DCmdDump.java#L120-L141") //
    /* Mostly identical to Hotspot. */
    private static void dump(PlatformRecorder recorder, Recording recording, String filename, Long maxSize, Boolean pathToGcRoots, Instant beginTime, Instant endTime)
                    throws DcmdParseException, IOException {
        try (PlatformRecording r = newSnapShot(recorder, recording, pathToGcRoots)) {
            r.filter(beginTime, endTime, maxSize);
            if (r.getChunks().isEmpty()) {
                throw new DcmdParseException("Dump failed. No data found in the specified interval.");
            }
            /*
             * If a filename exists, use it. If a filename doesn't exist, use the destination set
             * earlier. If destination doesn't exist, generate a filename
             */
            WriteableUserPath wup = null;
            if (recording != null) {
                PlatformRecording pRecording = PrivateAccess.getInstance().getPlatformRecording(recording);
                wup = pRecording.getDestination();
            }
            if (filename != null || wup == null) {
                SecuritySupport.SafePath safe = JfrManager.resolvePath(recording, filename);
                wup = new WriteableUserPath(safe.toPath());
            }
            r.dumpStopped(wup);
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/jdk.jfr/share/classes/jdk/jfr/internal/dcmd/DCmdDump.java#L143-L182") //
    /* Mostly identical to Hotspot. */
    private static Instant parseTime(String time, String parameter) throws DcmdParseException {
        if (time == null) {
            return null;
        }
        try {
            return Instant.parse(time);
        } catch (DateTimeParseException dtp) {
            // fall through
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(time);
            return ZonedDateTime.of(ldt, ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException dtp) {
            // fall through
        }
        try {
            LocalTime lt = LocalTime.parse(time);
            LocalDate ld = LocalDate.now();
            Instant instant = ZonedDateTime.of(ld, lt, ZoneId.systemDefault()).toInstant();
            Instant now = Instant.now();
            if (instant.isAfter(now) && !instant.isBefore(now.plusSeconds(3600))) {
                // User must have meant previous day
                ld = ld.minusDays(1);
            }
            return ZonedDateTime.of(ld, lt, ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException dtp) {
            // fall through
        }

        if (time.startsWith("-")) {
            try {
                long durationNanos = parseTimespan(time.substring(1));
                Duration duration = Duration.ofNanos(durationNanos);
                return Instant.now().minus(duration);
            } catch (NumberFormatException nfe) {
                // fall through
            }
        }
        throw new DcmdParseException("Dump failed, not a valid time: " + parameter);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/jdk.jfr/share/classes/jdk/jfr/internal/dcmd/DCmdDump.java#L184-L194") //
    /* Mostly identical to Hotspot. */
    private static PlatformRecording newSnapShot(PlatformRecorder recorder, Recording recording, Boolean pathToGcRoots) throws IOException {
        if (recording == null) {
            // Operate on all recordings
            PlatformRecording snapshot = recorder.newTemporaryRecording();
            recorder.fillWithRecordedData(snapshot, pathToGcRoots);
            return snapshot;
        }

        PlatformRecording pr = PrivateAccess.getInstance().getPlatformRecording(recording);
        return pr.newSnapshotClone("Dumped by user", pathToGcRoots);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/jdk.jfr/share/classes/jdk/jfr/internal/util/ValueParser.java#L44-L74") //
    /* Mostly identical to Hotspot. */
    private static long parseTimespan(String s) {
        if (s.endsWith("ns")) {
            return Long.parseLong(s.substring(0, s.length() - 2).trim());
        }
        if (s.endsWith("us")) {
            return MICROSECONDS.toNanos(Long.parseLong(s.substring(0, s.length() - 2).trim()));
        }
        if (s.endsWith("ms")) {
            return MILLISECONDS.toNanos(Long.parseLong(s.substring(0, s.length() - 2).trim()));
        }
        if (s.endsWith("s")) {
            return SECONDS.toNanos(Long.parseLong(s.substring(0, s.length() - 1).trim()));
        }
        if (s.endsWith("m")) {
            return MINUTES.toNanos(Long.parseLong(s.substring(0, s.length() - 1).trim()));
        }
        if (s.endsWith("h")) {
            return HOURS.toNanos(Long.parseLong(s.substring(0, s.length() - 1).trim()));
        }
        if (s.endsWith("d")) {
            return DAYS.toNanos(Long.parseLong(s.substring(0, s.length() - 1).trim()));
        }

        try {
            Long.parseLong(s);
        } catch (NumberFormatException nfe) {
            throw new NumberFormatException("'" + s + "' is not a valid timespan. Should be numeric value followed by a unit, i.e. 20 ms. Valid units are ns, us, s, m, h and d.");
        }
        // Only accept values with units
        throw new NumberFormatException("Timespan + '" + s + "' is missing unit. Valid units are ns, us, s, m, h and d.");
    }
}
