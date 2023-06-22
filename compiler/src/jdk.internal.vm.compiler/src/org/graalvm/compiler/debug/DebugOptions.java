/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import static org.graalvm.compiler.debug.PathUtilities.createDirectories;
import static org.graalvm.compiler.debug.PathUtilities.exists;
import static org.graalvm.compiler.debug.PathUtilities.getAbsolutePath;
import static org.graalvm.compiler.debug.PathUtilities.getPath;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.EnumMultiOptionKey;
import org.graalvm.compiler.options.EnumOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionStability;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;

/**
 * Options that configure a {@link DebugContext} and related functionality.
 */
public class DebugOptions {

    /**
     * Values for the {@link DebugOptions#PrintGraph} option denoting where graphs dumped as a
     * result of the {@link DebugOptions#Dump} option are sent.
     */
    public enum PrintGraphTarget {
        /**
         * Dump graphs to files.
         */
        File,

        /**
         * Dump graphs to the network. The network destination is specified by the
         * {@link DebugOptions#PrintGraphHost} and {@link DebugOptions#PrintGraphPort} options. If a
         * network connection cannot be opened, dumping falls back to {@link #File} dumping.
         */
        Network,

        /**
         * Do not dump graphs.
         */
        Disable;
    }

    /**
     * Values for the {@link DebugOptions#OptimizationLog} option denoting where the structured
     * optimization is printed.
     */
    public enum OptimizationLogTarget {
        /**
         * Print logs to JSON files in a directory.
         */
        Directory,
        /**
         * Print logs as JSON to the standard output.
         */
        Stdout,
        /**
         * Dump the optimization tree as an IGV graph.
         */
        Dump
    }

    // @formatter:off
    @Option(help = "Comma separated names of timers that are enabled irrespective of the value for Time option. " +
                   "An empty value enables all timers unconditionally.", type = OptionType.Debug)
    public static final OptionKey<String> Timers = new OptionKey<>(null);
    @Option(help = "Comma separated names of counters that are enabled irrespective of the value for Count option. " +
                   "An empty value enables all counters unconditionally.", type = OptionType.Debug)
    public static final OptionKey<String> Counters = new OptionKey<>(null);
    @Option(help = "Comma separated names of memory usage trackers that are enabled irrespective of the value for TrackMemUse option. " +
                   "An empty value enables all memory usage trackers unconditionally.", type = OptionType.Debug)
    public static final OptionKey<String> MemUseTrackers = new OptionKey<>(null);

    @Option(help = "Pattern for specifying scopes in which counters are enabled. " +
                   "See the Dump option for the pattern syntax. " +
                   "An empty value enables all counters unconditionally.", type = OptionType.Debug)
    public static final OptionKey<String> Count = new OptionKey<>(null);
    @Option(help = "Pattern for specifying scopes in which memory use tracking is enabled. " +
                   "See the Dump option for the pattern syntax. " +
                   "An empty value enables all memory use trackers unconditionally.", type = OptionType.Debug)
    public static final OptionKey<String> TrackMemUse = new OptionKey<>(null);
    @Option(help = "Pattern for specifying scopes in which timing is enabled. " +
                   "See the Dump option for the pattern syntax. " +
                   "An empty value enables all timers unconditionally.", type = OptionType.Debug)
    public static final OptionKey<String> Time = new OptionKey<>(null);

    @Option(help = "Pattern for specifying scopes in which logging is enabled. " +
                   "See the Dump option for the pattern syntax.", type = OptionType.Debug)
    public static final OptionKey<String> Verify = new OptionKey<>(null);
    @Option(help = "file:doc-files/DumpHelp.txt", type = OptionType.Debug, stability = OptionStability.STABLE)
    public static final OptionKey<String> Dump = new OptionKey<>(null);
    @Option(help = "Pattern for specifying scopes in which logging is enabled. " +
                   "See the Dump option for the pattern syntax.", type = OptionType.Debug)
    public static final OptionKey<String> Log = new OptionKey<>(null);
    @Option(help = "file:doc-files/MethodFilterHelp.txt", stability = OptionStability.STABLE)
    public static final OptionKey<String> MethodFilter = new OptionKey<>(null);
    @Option(help = "Only check MethodFilter against the root method in the context if true, otherwise check all methods", type = OptionType.Debug)
    public static final OptionKey<Boolean> MethodFilterRootOnly = new OptionKey<>(false);
    @Option(help = "Dump a before and after graph if the named phase changes the graph.%n" +
                   "The argument is substring matched against the simple name of the phase class", type = OptionType.Debug)
    public static final OptionKey<String> DumpOnPhaseChange = new OptionKey<>(null);

    @Option(help = "Lists on the console at VM shutdown the metric names available to the Timers, Counters and MemUseTrackers options. " +
                   "Note that this only lists the metrics that were initialized during the VM execution and so " +
                   "will not include metrics for compiler code that is not executed.", type = OptionType.Debug)
    public static final OptionKey<Boolean> ListMetrics = new OptionKey<>(false);
    @Option(help = "file:doc-files/MetricsFileHelp.txt", type = OptionType.Debug)
     public static final OptionKey<String> MetricsFile = new OptionKey<>(null);
    @Option(help = "File to which aggregated metrics are dumped at shutdown. A CSV format is used if the file ends with .csv " +
                   "otherwise a more human readable format is used. If not specified, metrics are dumped to the console.", type = OptionType.Debug)
    public static final OptionKey<String> AggregatedMetricsFile = new OptionKey<>(null);

    @Option(help = "Enable debug output for stub code generation and snippet preparation.", type = OptionType.Debug)
    public static final OptionKey<Boolean> DebugStubsAndSnippets = new OptionKey<>(false);
    @Option(help = "Send compiler IR to dump handlers on error.", type = OptionType.Debug)
    public static final OptionKey<Boolean> DumpOnError = new OptionKey<>(false);
    @Option(help = "Specify the dump level if CompilationFailureAction#Diagnose is used." +
                    "See CompilationFailureAction for details. file:doc-files/CompilationFailureActionHelp.txt", type = OptionType.Debug)
    public static final OptionKey<Integer> DiagnoseDumpLevel = new OptionKey<>(DebugContext.VERBOSE_LEVEL);
    @Option(help = "Disable intercepting exceptions in debug scopes.", type = OptionType.Debug)
    public static final OptionKey<Boolean> DisableIntercept = new OptionKey<>(false);
    @Option(help = "Intercept also bailout exceptions", type = OptionType.Debug)
    public static final OptionKey<Boolean> InterceptBailout = new OptionKey<>(false);
    @Option(help = "Enable more verbose log output when available", type = OptionType.Debug)
    public static final OptionKey<Boolean> LogVerbose = new OptionKey<>(false);

    @Option(help = "The directory where various Graal dump files are written.")
    public static final OptionKey<String> DumpPath = new OptionKey<>("graal_dumps");
    @Option(help = "Print the name of each dump file path as it's created.")
    public static final OptionKey<Boolean> ShowDumpFiles = new OptionKey<>(false);

    @Option(help = "Enable dumping scheduled HIR, LIR, register allocation and code generation info to the C1Visualizer.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintBackendCFG = new OptionKey<>(false);
    @Option(help = "Enable dumping CFG built during initial BciBlockMapping", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintBlockMapping = new OptionKey<>(false);

    @Option(help ="Enables dumping of basic blocks relative PC and frequencies in the dump directory.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintBBInfo = new OptionKey<>(false);

    @Option(help = "file:doc-files/PrintGraphHelp.txt", type = OptionType.Debug)
    public static final EnumOptionKey<PrintGraphTarget> PrintGraph = new EnumOptionKey<>(PrintGraphTarget.File);

    @Option(help = "Dump a graph even if it has not changed since it was last dumped.  " +
            "Change detection is based on adding and deleting nodes or changing inputs.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintUnmodifiedGraphs = new OptionKey<>(true);

    @Option(help = "Setting to true sets PrintGraph=file, setting to false sets PrintGraph=network", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintGraphFile = new OptionKey<>(true) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            PrintGraphTarget v = PrintGraph.getValueOrDefault(values);
            if (newValue.booleanValue()) {
                if (v != PrintGraphTarget.File) {
                    PrintGraph.update(values, PrintGraphTarget.File);
                }
            } else {
                if (v != PrintGraphTarget.Network) {
                    PrintGraph.update(values, PrintGraphTarget.Network);
                }
            }
        }
    };

    @Option(help = "Host part of the address to which graphs are dumped.", type = OptionType.Debug)
    public static final OptionKey<String> PrintGraphHost = new OptionKey<>("127.0.0.1");
    @Option(help = "Port part of the address to which graphs are dumped in binary format.", type = OptionType.Debug)
    public static final OptionKey<Integer> PrintGraphPort = new OptionKey<>(4445);
    @Option(help = "Schedule graphs as they are dumped.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintGraphWithSchedule = new OptionKey<>(false);

    @Option(help = "Treat any exceptions during dumping as fatal.", type = OptionType.Debug)
    public static final OptionKey<Boolean> DumpingErrorsAreFatal = new OptionKey<>(false);

    @Option(help = "Enable dumping canonical text from for graphs.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintCanonicalGraphStrings = new OptionKey<>(false);
    @Option(help = "Choose format used when dumping canonical text for graphs: " +
            "0 gives a scheduled graph (better for spotting changes involving the schedule) " +
            "while 1 gives a CFG containing expressions rooted at fixed nodes (better for spotting small structure differences)", type = OptionType.Debug)
    public static final OptionKey<Integer> PrintCanonicalGraphStringFlavor = new OptionKey<>(0);
    @Option(help = "Exclude virtual nodes when dumping canonical text for graphs.", type = OptionType.Debug)
    public static final OptionKey<Boolean> CanonicalGraphStringsExcludeVirtuals = new OptionKey<>(true);
    @Option(help = "Exclude virtual nodes when dumping canonical text for graphs.", type = OptionType.Debug)
    public static final OptionKey<Boolean> CanonicalGraphStringsCheckConstants = new OptionKey<>(false);
    @Option(help = "Attempts to remove object identity hashes when dumping canonical text for graphs.", type = OptionType.Debug)
    public static final OptionKey<Boolean> CanonicalGraphStringsRemoveIdentities = new OptionKey<>(true);

    @Option(help = "Clear the debug metrics after bootstrap.", type = OptionType.Debug)
    public static final OptionKey<Boolean> ClearMetricsAfterBootstrap = new OptionKey<>(false);
    @Option(help = "Do not compile anything on bootstrap but just initialize the compiler.", type = OptionType.Debug)
    public static final OptionKey<Boolean> BootstrapInitializeOnly = new OptionKey<>(false);

    @Option(help = "file:doc-files/OptimizationLogHelp.txt", type = OptionType.Debug)
    public static final EnumMultiOptionKey<OptimizationLogTarget> OptimizationLog = new EnumMultiOptionKey<>(OptimizationLogTarget.class, null);
    @Option(help = "Path to the directory where the optimization log is saved if OptimizationLog is set to Directory. " +
            "Directories are created if they do no exist.", type = OptionType.Debug)
    public static final OptionKey<String> OptimizationLogPath = new OptionKey<>(null);
    // @formatter:on

    /**
     * The format of the message printed on the console by {@link #getDumpDirectory} when
     * {@link DebugOptions#ShowDumpFiles} is true. The {@code %s} placeholder is replaced with the
     * value returned by {@link #getDumpDirectory}.
     */
    private static final String DUMP_DIRECTORY_MESSAGE_FORMAT = "Dumping debug output in '%s'";

    /**
     * Gets the directory in which {@link DebugDumpHandler}s can generate output. This will be the
     * directory specified by {@link #DumpPath} if it has been set otherwise it will be derived from
     * the default value of {@link #DumpPath} and {@link GraalServices#getGlobalTimeStamp()}.
     *
     * This method will ensure the returned directory exists, printing a message to {@link TTY} if
     * it creates it.
     *
     * @return a path as described above whose directories are guaranteed to exist
     * @throws IOException if there was an error when creating a directory
     */
    public static String getDumpDirectory(OptionValues options) throws IOException {
        String dumpDir = getDumpDirectoryName(options);
        if (!exists(dumpDir)) {
            synchronized (DebugConfigImpl.class) {
                if (!exists(dumpDir)) {
                    createDirectories(dumpDir);
                    if (ShowDumpFiles.getValue(options)) {
                        TTY.println(DUMP_DIRECTORY_MESSAGE_FORMAT, dumpDir);
                    }
                }
            }
        }
        return dumpDir;
    }

    /**
     * Returns the {@link #getDumpDirectory} without attempting to create it.
     */
    public static String getDumpDirectoryName(OptionValues options) {
        String dumpDir;
        if (DumpPath.hasBeenSet(options)) {
            dumpDir = getPath(DumpPath.getValue(options));
        } else {
            Date date = new Date(GraalServices.getGlobalTimeStamp());
            SimpleDateFormat formatter = new SimpleDateFormat("YYYY.MM.dd.HH.mm.ss.SSS");
            dumpDir = getPath(DumpPath.getValue(options), formatter.format(date));
        }
        dumpDir = getAbsolutePath(dumpDir);
        return dumpDir;
    }
}
