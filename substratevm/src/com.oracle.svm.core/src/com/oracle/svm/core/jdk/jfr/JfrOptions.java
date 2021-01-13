/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;

import com.oracle.svm.core.jdk.jfr.logging.JfrLogger;
import com.oracle.svm.core.option.RuntimeOptionKey;

// Translated from JfrOptionSet.cpp
public class JfrOptions {

    // Taken from GlobalDefinitions.hpp
    private static final int K = 1024;
    private static final int M = K * K;
    private static final int G = M * K;

    private static final int MIN_STACK_DEPTH = 1;
    private static final int MAX_STACK_DEPTH = 2048;

    // Taken from JfrMemorySizer.cpp
    private static final int MAX_ADJUSTED_GLOBAL_BUFFER_SIZE = 1 * M;
    private static final int MIN_ADJUSTED_GLOBAL_BUFFER_SIZE_CUTOFF = 512 * K;
    private static final int MIN_GLOBAL_BUFFER_SIZE = 64 * K;
    // implies at least 2 * MIN_GLOBAL_BUFFER SIZE
    private static final int MIN_BUFFER_COUNT = 2;
    // MAX global buffer count open ended
    private static final int DEFAULT_BUFFER_COUNT = 20;
    // MAX thread local buffer size == size of a single global buffer (runtime determined)
    // DEFAULT thread local buffer size = 2 * os page size (runtime determined)
    private static final int MIN_THREAD_BUFFER_SIZE = 4 * K;
    private static final int MIN_MEMORY_SIZE = 1 * M;

    private static final int DEFAULT_MAX_CHUNK_SIZE = 12 * 1024 * 1024;
    private static final int MIN_MAX_CHUNKSIZE = 1024 * 1024;

    private static final String DELIMITER = ",";
    private static final String ARGUMENT_DELIMITER = "=";
    private static final String CHUNK_SIZE_ARG = "maxchunksize";
    private static final String GLOBAL_BUFFER_SIZE_ARG = "globalbuffersize";
    private static final String MEMORY_SIZE_ARG = "memorysize";
    private static final String RETRANSFORM = "retransform";
    private static final String STACK_DEPTH = "stackdepth";
    private static final String SAMPLE_PROTECTION = "sampleprotection";
    private static final String SAMPLE_THREADS = "samplethreads";
    private static final String OLD_OBJECT_QUEUE_SIZE = "old-object-queue-size";
    private static final String NUM_GLOBAL_BUFFERS = "numglobalbuffers";
    private static final String THREAD_BUFFER_SIZE = "threadbuffersize";
    private static final String REPOSITORY = "repository";
    // Arguments from JfrDcmds.cpp
    private static final String DUMPONEXIT = "dumponexit";
    private static final String FILENAME = "filename";
    private static final String NAME = "name";
    private static final String SETTINGS = "settings";
    private static final String DELAY = "delay";
    private static final String DURATION = "duration";
    private static final String DISK = "disk";
    private static final String MAXAGE = "maxage";
    private static final String MAXSIZE = "maxsize";
    private static final String GCROOTS = "path-to-gc-roots";

    // Thread Buffer Size, Memory Size, Global Buffer size, Max Chunk Size, Max Recording size are memory arguments
    private static int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
    private static int globalBufferSize = 512 * K;
    private static int memorySize = 10 * M;
    private static int threadBufferSize = 8 * K;
    private static int globalBufferCount = 20;
    private static int oldObjectQueueSize = 256;
    private static int maxRecordingSize = 0; // 0 is a special value indicating no limit
    private static int stackDepth = 64;

    // Delay, Duration, Max Age are time arguments
    private static boolean startRecordingAutomatically = false;
    private static long delay = 0;
    private static long duration = 0;
    private static long maxAge = 0; // 0 is a special value indicating no limit

    private static boolean sampleThreads = true;
    private static boolean retransform = true;
    private static boolean sampleProtection = false; // Defaults to false if ASSERT is defined, otherwise defaults to true
    private static boolean dumpOnExit = false;
    private static boolean persistToDisk = false;
    private static boolean pathToGcRoots = false;

    private static String repositoryLocation = "";
    private static String filename = "";
    private static String recordingName = "";
    private static String recordingSettingsFile = "";

    private static int logLevel = Integer.MAX_VALUE;

    private static final ArrayList<String> startFlightRecordingOptionsArray = new ArrayList<>();

    static {
        // has a side effect call to jdk.jfr.internal.JVM
        setMaxChunkSize(DEFAULT_MAX_CHUNK_SIZE);
    }

    @Option(help = "Enable flight recorder logging with options")
    public static final RuntimeOptionKey<String> FlightRecorderLogging = new RuntimeOptionKey<String>("") {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
            parseFlightRecorderLoggingOption(newValue);
        }
    };

    @Option(help = "Start a flight recording with the given parameters")
    public static final RuntimeOptionKey<String> StartFlightRecordingOption = new RuntimeOptionKey<String>("") {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
            // Substrate parses it into option=value,option2=value,.... We can pass it on to our own parsing methods from here
            parseStartFlightRecordingOption(newValue);
            startRecordingAutomatically = true;
            // Do the sanity checks to make sure we have valid options
            if (!adjustMemoryOptions()) {
                throw new IllegalArgumentException("Failed to validate memory arguments");
            }
        }
    };

    @Option(help = "Pass an option to flight recorder")
    public static final RuntimeOptionKey<String> FlightRecorderOption = new RuntimeOptionKey<String>(""){
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
            parseStartFlightRecordingOption(newValue);
            // Do the sanity checks to make sure we have valid options
            if (!adjustMemoryOptions()) {
                throw new IllegalArgumentException("Failed to validate memory arguments");
            }
        }
    };

    public static boolean parseFlightRecorderLoggingOption(String value) {
        switch (value) {
            case "":
            case "error":
                logLevel = JfrLogger.Level.ERROR.id;
                break;
            case "warning":
                logLevel = JfrLogger.Level.WARNING.id;
                break;
            case "info":
                logLevel = JfrLogger.Level.INFO.id;
                break;
            case "debug":
                logLevel = JfrLogger.Level.DEBUG.id;
                break;
            case "trace":
                logLevel = JfrLogger.Level.TRACE.id;
                break;
        }
        return true;
    }

    public static boolean parseStartFlightRecordingOption(String args) {
        if (args.equals("")) {
            // -XX:StartFlightRecording without any delimiter and values
            // No need to do anything here, just return and use defaults.
            return true;
        }
        // This argument has the form -XX:StartFlightRecording=arg1=value,arg2=value,...
        List<String> splitOptions = Arrays.asList(args.split(DELIMITER));
        startFlightRecordingOptionsArray.addAll(splitOptions);
        try {
            for (String opt : splitOptions) {
                String arg = opt.split(ARGUMENT_DELIMITER)[0];
                String val = opt.split(ARGUMENT_DELIMITER)[1];
                switch (arg) {
                    case REPOSITORY:
                        repositoryLocation = val;
                        break;
                    case DUMPONEXIT:
                        dumpOnExit = Boolean.parseBoolean(val);
                        break;
                    case THREAD_BUFFER_SIZE:
                        threadBufferSize = parseMemoryOption(val);
                        break;
                    case CHUNK_SIZE_ARG:
                        maxChunkSize = parseMemoryOption(val);
                        break;
                    case GLOBAL_BUFFER_SIZE_ARG:
                        globalBufferSize = parseMemoryOption(val);
                        break;
                    case MEMORY_SIZE_ARG:
                        memorySize = parseMemoryOption(val);
                        break;
                    case RETRANSFORM:
                        retransform = Boolean.parseBoolean(val);
                        break;
                    case STACK_DEPTH:
                        if (verifyStackArguments(Integer.parseInt(val))) {
                            stackDepth = Integer.parseInt(val);
                        } else {
                            throw new RuntimeException("Invalid Stack Depth specified. Stack depth must be between 1 and 2048.");
                        }
                        break;
                    case SAMPLE_PROTECTION:
                        sampleProtection = Boolean.parseBoolean(val);
                        break;
                    case SAMPLE_THREADS:
                        sampleThreads = Boolean.parseBoolean(val);
                        break;
                    case OLD_OBJECT_QUEUE_SIZE:
                        oldObjectQueueSize = Integer.parseInt(val);
                        break;
                    case NUM_GLOBAL_BUFFERS:
                        globalBufferCount = Integer.parseInt(val);
                        break;
                    case DISK:
                        persistToDisk = Boolean.parseBoolean(val);
                        break;
                    case FILENAME:
                        filename = val;
                        break;
                    case GCROOTS:
                        pathToGcRoots = Boolean.parseBoolean(val);
                        break;
                    case NAME:
                        recordingName = val;
                        break;
                    case SETTINGS:
                        recordingSettingsFile = val;
                        break;
                    case DELAY:
                        delay = parseTimeOption(val);
                        break;
                    case DURATION:
                        duration = parseTimeOption(val);
                        break;
                    case MAXAGE:
                        maxAge = parseTimeOption(val);
                        break;
                    case MAXSIZE:
                        maxRecordingSize = parseMemoryOption(val);
                        break;
                }
            }
        } catch (Exception e) {
            throw e;
        }
        return true;
    }

    private static long parseTimeOption(String arg) {
        String adjusted = arg.toLowerCase();
        if (adjusted.endsWith("ms")) {
            return Long.parseLong(adjusted.split("ms")[0]);
        } else if (adjusted.endsWith("s")) {
            return Long.parseLong(adjusted.split("s")[0]) * 1000;
        } else if (adjusted.endsWith("m")) {
            return Long.parseLong(adjusted.split("m")[0]) * 1000 * 60;
        } else if (adjusted.endsWith("h")) {
            return Long.parseLong(adjusted.split("h")[0]) * 1000 * 360;
        } else if (adjusted.endsWith("d")) {
            return Long.parseLong(adjusted.split("d")[0]) * 1000 * 60 * 60 * 24;
        } else {
            throw new IllegalArgumentException("Invalid time specified for " + arg + " specify time lengths with ms, s, m, h, or d");
        }
    }

    private static int parseMemoryOption(String arg) {
        String adjusted = arg.toLowerCase();
        if (adjusted.contains("k")) {
            return Integer.parseInt(adjusted.split("k")[0]) * K;
        } else if (arg.contains("m")) {
            return Integer.parseInt(adjusted.split("m")[0]) * M;
        } else if (arg.contains("g")) {
            return Integer.parseInt(adjusted.split("g")[0]) * G;
        } else {
            throw new RuntimeException("Invalid memory format specified for " + arg + " specify memory sizes with M, K, or G");
        }
    }

    // TODO: public static boolean configure() {
    // Configure sets up and executes a dcmd with all of the currently specified values, we'll leave it alone for now

    public static ArrayList<String> startFlightRecordingOptions() {
        return startFlightRecordingOptionsArray;
    }

    public static boolean initialize() {
        // Reference the Runtime Options so that they will be available at runtime
        JfrOptions.StartFlightRecordingOption.getValue();
        JfrOptions.FlightRecorderOption.getValue();
        JfrOptions.FlightRecorderLogging.getValue();
        // This is where we would register the jfr options with the dcmd parser, we don't need to do that here
        // We can still run the sanity checks on the memory options though.
        return adjustMemoryOptions();
    }

    // In the jfr sources this function adds all of the dcmd jfr options (repository, thread buffer size,
    // memory size, global buffer size, number of global buffers, max chunk size, stackdepth, sample threads,
    // retransform, old object queue size, and sample protection)
    // If we don't want to support, or can't support dcmd control of jfr in substrate then we can skip this
    // TODO: Implement this if it makes sense to do so
    //  private static void registerParserOptions()

    // In the jfr sources this function relies on the Dcmd parser for the heavy lifting, the bulk of it
    // is memory wrangling and string manipulation to grab and log the pending exception, this makes our job
    // here a bit easier. It's not needed unless we can/plan to support dcmd in substrate.
    // TODO: Implement this if it makes sense to do so
    // private static void parseFlightRecorderOptionsInternal()

    public static int getMaxChunkSize() {
        return maxChunkSize;
    }

    public static void setMaxChunkSize(int chunkSize) {
        if (chunkSize < MIN_MAX_CHUNKSIZE) {
            throw new IllegalArgumentException("Max chunk size must be at least " + MIN_MAX_CHUNKSIZE);
        }
        Target_jdk_jfr_internal_JVM.getJVM().setFileNotification(chunkSize);
        maxChunkSize = chunkSize;
    }

    public static boolean getStartRecordingAutomatically() {
        return startRecordingAutomatically;
    }

    public static int getGlobalBufferSize() {
        return globalBufferSize;
    }

    public static void setGlobalBufferSize(int bufferSize) {
        globalBufferSize = bufferSize;
    }

    public static int getMemorySize() {
        return memorySize;
    }

    public static void setMemorySize(int memorySize) {
        JfrOptions.memorySize = memorySize;
    }

    public static int getThreadBufferSize() {
        return threadBufferSize;
    }

    public static void setThreadBufferSize(int bufferSize) {
        threadBufferSize = bufferSize;
    }

    public static int getGlobalBufferCount() {
        return globalBufferCount;
    }

    public static void setGlobalBufferCount(int bufferCount) {
        globalBufferCount = bufferCount;
    }

    public static long getObjectQueueSize() {
        return oldObjectQueueSize;
    }

    public static void setObjectQueueSize(int queueSize) {
        oldObjectQueueSize = queueSize;
    }

    public static int getStackDepth() {
        return stackDepth;
    }

    public static void setStackDepth(int depth) {
        if (depth < MIN_STACK_DEPTH) {
            stackDepth = MIN_STACK_DEPTH;
        } else if (depth > MAX_STACK_DEPTH) {
            stackDepth = MAX_STACK_DEPTH;
        } else {
            stackDepth = depth;
        }
    }

    public static boolean isSampleThreadsEnabled() {
        return sampleThreads;
    }

    public static void setSampleThreads(boolean sampling) {
        sampleThreads = sampling;
    }

    public static boolean isRetransformEnabled() {
        return retransform;
    }

    public static void setRetransform(boolean retransformEnabled) {
        retransform = retransformEnabled;
    }

    public static boolean isSampleProtectionEnabled() {
        return sampleProtection;
    }

    // This is wrapped in an #ifdef ASSERT in the jdk sources
    public static void setSampleProtection(boolean sampleProtection) {
        JfrOptions.sampleProtection = sampleProtection;
    }

    public static boolean allowRetransforms() {
        //TODO: Do we have a way of checking if JVMTI is present/enabled?
        // #if INCLUDE_JVMTI
        // return true
        // #else
        // return false
        return false;
    }

    public static boolean allowEventRetransforms() {
        // return allow_retransforms() && (DumpSharedSpaces || can_retransform());
        return false;
    }

    public static boolean isPersistedToDisk() {
        return persistToDisk;
    }

    public static boolean trackPathToGcRoots() {
        return pathToGcRoots;
    }

    public static String getRecordingFileName() {
        return filename;
    }

    public static String getRecordingName() {
        return recordingName;
    }

    public static String getRecordingSettingsFile() {
        return recordingSettingsFile;
    }

    public static String getRepositoryLocation() {
        return repositoryLocation;
    }

    public static long getRecordingDelay() {
        return delay;
    }

    public static long getDuration() {
        return duration;
    }

    public static long getMaxAge() {
        return maxAge;
    }

    public static long getMaxRecordingSize() {
        return maxRecordingSize;
    }

    public static boolean getDumpOnExit() {
        return dumpOnExit;
    }

    public static boolean compressedIntegers() {
        return true;
    }

    public static int getLogLevel() {
        return logLevel;
    }

     /* Starting with the initial set of memory values from the user,
      * sanitize, enforce min/max rules and adjust to a set of consistent options.
      *
      * Adjusted memory sizes will be page aligned.
      */
    static boolean adjustMemoryOptions() {
        if (!ensureValidMinimumSizes()) {
            return false;
        }
        if (!validMemoryRelations()) {
            return false;
        }
        if (!verifyStackArguments(stackDepth)) {
            return false;
        }

        /* TODO: Revisit once JfrMemorySizer is rewritten
        if (!JfrMemorySizer::adjust_options(&options)) {
            if (!check_for_ambiguity(_dcmd_memorysize, _dcmd_globalbuffersize, _dcmd_numglobalbuffers)) {
                return false;
            }
        }*/
        if (!checkForAmbiguity()) {
            return false;
        }

        return true;
    }

    private static boolean verifyStackArguments(int stackDepth) {
        if (stackDepth < MIN_STACK_DEPTH) {
            return false;
        } else if (stackDepth > MAX_STACK_DEPTH) {
            return false;
        } else {
            return true;
        }
    }

    private static boolean ensureValidMinimumSizes() {
        // ensure valid minimum memory sizes
        if (!(memorySize >= MIN_MEMORY_SIZE)) {
            return false;
        }
        if (!(globalBufferSize >= MIN_GLOBAL_BUFFER_SIZE)) {
            return false;
        }
        if (!(globalBufferCount >= MIN_BUFFER_COUNT)) {
            return false;
        }
        if (!(threadBufferSize >= MIN_THREAD_BUFFER_SIZE)) {
            return false;
        }
        return true;
    }

    private static boolean validMemoryRelations() {
        if (!(memorySize >= globalBufferSize)) {
            return false;
        }
        if (!(globalBufferSize >= threadBufferSize)) {
            return false;
        }
        if (!(globalBufferSize * globalBufferCount >= MIN_MEMORY_SIZE)) {
            return false;
        }
        return true;
    }

    private static boolean checkForAmbiguity() {
        long calcSize = globalBufferSize * globalBufferCount;
        if (calcSize != memorySize) {
            // ambiguous
            JfrLogger.logError("Value specified for number of global buffers, global buffer size, and" +
            "memory size are causing ambiguity when trying to determine how much memory to use. " +
            "global buffer count * global buffer size do not equal memory size. Try to remove one of the " +
            "involved options or make sure they are unambiguous.");
            return false;
        }
        return true;
    }

}
