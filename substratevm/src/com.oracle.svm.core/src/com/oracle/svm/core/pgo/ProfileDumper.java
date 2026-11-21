/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.pgo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.shared.util.TimeUtils;

import jdk.graal.compiler.options.Option;

/**
 * Writes the profile of a {@code --pgo-instrument} image when the application exits.
 * <p>
 * The teardown hook takes a snapshot of every enabled recorder (branches, switches, receiver and
 * monitor types, call counts and stack samples), writes all of them into one iprof file with
 * {@link IprofWriter} and, unless {@code -XX:-ProfilesDumpStatistics} is given, prints what the file
 * contains and what each recorder saw. It stops sampling in every thread and takes the snapshots
 * before it runs any instrumented code, so the file holds none of the dump's own work.
 * <p>
 * An instrumented image supports one isolate per process: the recorders keep their shared counters
 * and tables in global data of the process, while each isolate runs this hook when it tears down.
 * With several isolates, each dump would hold the counts of all of them.
 */
public final class ProfileDumper {

    public static final class Options {
        @Option(help = "File path for the profile produced by a --pgo-instrument image. %p is replaced by the process id and %t by the time of the dump in " +
                        "milliseconds, so that training runs with different process ids, or that end in different milliseconds, can write into one " +
                        "directory without overwriting each other. In containers every run may get the same process id, so use %t or a path per container there.")//
        public static final RuntimeOptionKey<String> ProfilesDumpFile = new RuntimeOptionKey<>("default.iprof");

        @Option(help = "Print what the profile dump wrote and what each recorder saw, one line per profile kind. Disable with -XX:-ProfilesDumpStatistics.")//
        public static final RuntimeOptionKey<Boolean> ProfilesDumpStatistics = new RuntimeOptionKey<>(true);
    }

    /**
     * Share of the stack samples, in percent, above which the dump warns that samples were dropped:
     * then the samples under-represent what the application ran after the tables filled up.
     */
    private static final int DROPPED_SAMPLES_WARNING_PERCENT = 1;

    private ProfileDumper() {
    }

    public static RuntimeSupport.Hook getTeardownHook() {
        return _ -> dump();
    }

    /** Replaces {@code %p} with the process id and {@code %t} with the current time in milliseconds. */
    static String expandDumpFilePattern(String pattern) {
        if (pattern.indexOf('%') < 0) {
            return pattern;
        }
        return pattern.replace("%p", Long.toString(ProcessHandle.current().pid())).replace("%t", Long.toString(TimeUtils.currentTimeMillis()));
    }

    private static void dump() {
        /*
         * The dump runs instrumented JDK code (collections, sorting, string building), which the
         * recorders count like application code. So before any of it runs, sampling stops in every
         * thread and every recorder takes its snapshot. Taking the call-count snapshot runs no
         * instrumented code; taking the type snapshot fills a HashMap, so it comes last.
         */
        StackSampleRecorder.stopSampling();
        if (BranchProfileRecorder.isEnabled()) {
            BranchProfileRecorder.prepareSnapshot();
        }
        if (CallCountProfileRecorder.isEnabled()) {
            CallCountProfileRecorder.prepareSnapshot();
        }
        if (TypeProfileRecorder.isEnabled()) {
            TypeProfileRecorder.prepareSnapshot();
        }

        String fileName = Options.ProfilesDumpFile.getValue();
        if (fileName == null || fileName.isEmpty()) {
            fileName = "default.iprof";
        }
        fileName = expandDumpFilePattern(fileName);
        try {
            List<BranchProfileKey> branchKeys = BranchProfileRecorder.isEnabled() ? BranchProfileRecorder.getKeys() : List.of();
            List<SwitchProfileKey> switchKeys = SwitchProfileRecorder.isEnabled() ? SwitchProfileRecorder.getKeys() : List.of();
            List<CallCountProfileKey> callCountKeys = CallCountProfileRecorder.isEnabled() ? CallCountProfileRecorder.getKeys() : List.of();
            List<StackSampleRecorder.DecodedSample> stackSamples = StackSampleRecorder.hasSamples() ? StackSampleRecorder.decodeSamples() : List.of();
            List<TypeProfileRecorder.DecodedReceiverProfile> receiverProfiles = List.of();
            /*
             * Null, not an empty map, while monitor profiling is off. The writer then leaves
             * monitorProfiles out of the file, whereas an empty map writes an empty section, which
             * says that monitors were recorded and none was entered. For receivers no such
             * difference exists: the writer leaves virtualInvokeProfiles out when the list is empty.
             */
            Map<String, Long> monitorProfiles = null;
            if (TypeProfileRecorder.isEnabled()) {
                TypeProfileRecorder.DecodedTypeProfiles profiles = TypeProfileRecorder.decodeProfiles();
                receiverProfiles = profiles.receiverProfiles();
                if (TypeProfileRecorder.monitorProfilesEnabled()) {
                    monitorProfiles = profiles.monitorProfiles();
                }
            }

            Path profilePath = Path.of(fileName);
            Path parent = profilePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            IprofWriter.DumpStatistics statistics = IprofWriter.write(profilePath, branchKeys, stackSamples, receiverProfiles, callCountKeys, switchKeys,
                            monitorProfiles);
            if (Options.ProfilesDumpStatistics.getValue()) {
                printStatistics(fileName, statistics, stackSamples.size());
            }
            printWarnings(statistics, stackSamples.size());
        } catch (Throwable exception) {
            /* The writer replaces the previous profile only with a complete one, so that one is still in place. */
            Log.log().string("[PGO] could not write profile '").string(fileName).string("': ").exception(exception).newline();
        }
    }

    /** Warns about what makes the profile less accurate, whether or not the statistics are printed. */
    private static void printWarnings(IprofWriter.DumpStatistics statistics, int decodedStacks) {
        if (statistics.skippedEntries() > 0) {
            Log.log().string("[PGO] warning: left out ").signed(statistics.skippedEntries()).string(" profile entries whose calling contexts hold a method with a malformed descriptor")
                            .newline();
        }
        if (StackSampleRecorder.hasSamples()) {
            StackSampleRecorder.Statistics sampling = StackSampleRecorder.statistics(decodedStacks);
            if (sampling.dropped() * 100 > sampling.samples() * DROPPED_SAMPLES_WARNING_PERCENT) {
                Log.log().string("[PGO] warning: dropped ").signed(sampling.dropped()).string(" of ").signed(sampling.samples()).string(" stack samples (")
                                .signed(sampling.dropped() * 100 / sampling.samples()).string("%) because the sample tables of their threads were full; ")
                                .string("the samples under-represent what ran after that").newline();
            }
        }
        if (BranchProfileRecorder.isEnabled() && BranchProfileThreadCounters.threadsWithoutBlock() > 0) {
            Log.log().string("[PGO] warning: ").signed(BranchProfileThreadCounters.threadsWithoutBlock())
                            .string(" threads counted branches in the shared counters because no native memory was left for their own; those counts can be too low").newline();
        }
    }

    /** Prints what the profile file holds, then what each enabled recorder saw while the application ran. */
    private static void printStatistics(String fileName, IprofWriter.DumpStatistics statistics, int decodedStacks) {
        Log.log().string("[PGO] wrote profile '").string(fileName).string("': conditional contexts=").signed(statistics.conditionalProfiles())
                        .string(", receiver contexts=").signed(statistics.receiverProfiles())
                        .string(", call-count contexts=").signed(statistics.callCountProfiles())
                        .string(", switch contexts=").signed(statistics.switchProfiles())
                        .string(", monitor types=").signed(statistics.monitorTypes())
                        .string(", monitor events=").signed(statistics.monitorEvents())
                        .string(", methods=").signed(statistics.methods())
                        .string(", types=").signed(statistics.types())
                        .string(", branch events=").signed(statistics.branchEvents()).newline();
        if (StackSampleRecorder.hasSamples()) {
            StackSampleRecorder.Statistics sampling = StackSampleRecorder.statistics(decodedStacks);
            Log.log().string("[PGO] stack samples=").signed(sampling.samples()).string(", threads=").signed(sampling.threads())
                            .string(", decoded stacks=").signed(sampling.decodedStacks()).string(", truncated=").signed(sampling.truncated())
                            .string(", dropped=").signed(sampling.dropped()).string(", unresolved addresses=").signed(sampling.unresolvedAddresses()).newline();
        }
        if (TypeProfileRecorder.isEnabled()) {
            TypeProfileRecorder.Statistics types = TypeProfileRecorder.statistics();
            Log.log().string("[PGO] dynamic type profiles: receiver sites=").signed(types.receiverSites())
                            .string(", receiver events=").signed(types.receiverEvents())
                            .string(", monitor events=").signed(types.monitorEvents())
                            .string(", total events=").signed(types.events())
                            .string(", dropped=").signed(types.dropped())
                            .string(", unknown receivers=").signed(types.unknownReceivers())
                            .string(", unknown monitors=").signed(types.unknownMonitors()).newline();
        }
        if (CallCountProfileRecorder.isEnabled()) {
            CallCountProfileRecorder.Statistics calls = CallCountProfileRecorder.statistics();
            Log.log().string("[PGO] call counts: call edges=").signed(calls.callEdges()).string(", events=").signed(calls.events())
                            .string(", attributed=").signed(calls.attributedEvents()).string(", keys=").signed(calls.keys()).string(", mapped keys=").signed(calls.mappedKeys())
                            .string(", dropped=").signed(calls.dropped()).newline();
        }
    }
}
