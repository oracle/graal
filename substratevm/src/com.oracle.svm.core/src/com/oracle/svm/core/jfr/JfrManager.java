/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import static com.oracle.svm.core.jfr.JfrArgumentParser.JfrArgumentParsingFailed;
import static com.oracle.svm.core.jfr.JfrArgumentParser.parseBoolean;
import static com.oracle.svm.core.jfr.JfrArgumentParser.parseInteger;
import static com.oracle.svm.core.jfr.JfrArgumentParser.parseJfrOptions;
import static com.oracle.svm.core.jfr.JfrArgumentParser.parseMaxSize;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jfr.JfrArgumentParser.FlightRecorderOptionsArgument;
import com.oracle.svm.core.jfr.JfrArgumentParser.JfrArgument;
import com.oracle.svm.core.jfr.events.EndChunkNativePeriodicEvents;
import com.oracle.svm.core.jfr.events.EveryChunkNativePeriodicEvents;
import com.oracle.svm.core.util.BasedOnJDKFile;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.jfr.FlightRecorder;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.Options;
import jdk.jfr.internal.Repository;

/**
 * Called during VM startup and teardown. Also triggers the JFR argument parsing.
 */
public class JfrManager {

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

    private static void parseFlightRecorderOptions() throws JfrArgumentParsingFailed {
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
                throw new JfrArgumentParsingFailed(FlightRecorderOptionsArgument.OldObjectQueueSize.getCmdLineKey() + " must be greater or equal 0.");
            }
        }

        if (repositoryPath != null) {
            try {
                setRepositoryBasePath(repositoryPath);
            } catch (Throwable e) {
                throw new JfrArgumentParsingFailed("Could not use " + repositoryPath + " as repository. " + e.getMessage(), e);
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

    private static void setRepositoryBasePath(String repositoryPath) throws IOException {
        Path path = Paths.get(repositoryPath);
        SubstrateUtil.cast(Repository.getRepository(), Target_jdk_jfr_internal_Repository.class).setBasePath(path);
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

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/jfr/dcmd/jfrDcmds.cpp#L219-L247")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/jfr/dcmd/jfrDcmds.cpp#L146-L180")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/jfr/dcmd/jfrDcmds.cpp#L130-L144")
    private static void initRecording() {
        Target_jdk_jfr_internal_dcmd_DCmdStart cmd = new Target_jdk_jfr_internal_dcmd_DCmdStart();
        String[] result = SubstrateUtil.cast(cmd, Target_jdk_jfr_internal_dcmd_AbstractDCmd.class).execute("internal", SubstrateOptions.StartFlightRecording.getValue(), ',');
        Logger.log(LogTag.JFR_SYSTEM, LogLevel.INFO, String.join(System.lineSeparator(), result));
    }
}
