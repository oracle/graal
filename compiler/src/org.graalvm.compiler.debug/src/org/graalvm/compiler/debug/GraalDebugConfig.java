/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static org.graalvm.compiler.debug.DebugContext.BASIC_LEVEL;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.UniquePathUtilities;
import org.graalvm.util.EconomicMap;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.JavaMethod;

public class GraalDebugConfig implements DebugConfig {

    public static class Options {
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

        @Option(help = "Pattern for scope(s) in which counting is enabled (see DebugFilter and Debug.counter). " +
                       "An empty value enables all counters unconditionally.", type = OptionType.Debug)
        public static final OptionKey<String> Count = new OptionKey<>(null);
        @Option(help = "Pattern for scope(s) in which memory use tracking is enabled (see DebugFilter and Debug.counter). " +
                       "An empty value enables all memory use trackers unconditionally.", type = OptionType.Debug)
        public static final OptionKey<String> TrackMemUse = new OptionKey<>(null);
        @Option(help = "Pattern for scope(s) in which timing is enabled (see DebugFilter and Debug.timer). " +
                       "An empty value enables all timers unconditionally.", type = OptionType.Debug)
        public static final OptionKey<String> Time = new OptionKey<>(null);

        @Option(help = "Pattern for scope(s) in which verification is enabled (see DebugFilter and Debug.verify).", type = OptionType.Debug)
        public static final OptionKey<String> Verify = new OptionKey<>(Assertions.ENABLED ? "" : null);
        @Option(help = "Pattern for scope(s) in which dumping is enabled (see DebugFilter and Debug.dump)", type = OptionType.Debug)
        public static final OptionKey<String> Dump = new OptionKey<>(null);
        @Option(help = "Pattern for scope(s) in which logging is enabled (see DebugFilter and Debug.log)", type = OptionType.Debug)
        public static final OptionKey<String> Log = new OptionKey<>(null);

        @Option(help = "Pattern for filtering debug scope output based on method context (see MethodFilter)", type = OptionType.Debug)
        public static final OptionKey<String> MethodFilter = new OptionKey<>(null);
        @Option(help = "Only check MethodFilter against the root method in the context if true, otherwise check all methods", type = OptionType.Debug)
        public static final OptionKey<Boolean> MethodFilterRootOnly = new OptionKey<>(false);
        @Option(help = "Dump a before and after graph if the named phase changes the graph.%n" +
                       "The argument is substring matched against the simple name of the phase class", type = OptionType.Debug)
        public static final OptionKey<String> DumpOnPhaseChange = new OptionKey<>(null);

        @Option(help = "Listst the console at VM shutdown the metric names available to the Timers, Counters and MemUseTrackers option. " +
                       "Note that this only lists the metrics that were initialized during the VM execution and so " +
                       "will not include metrics for compiler code that is not executed.", type = OptionType.Debug)
        public static final OptionKey<Boolean> ListMetrics = new OptionKey<>(false);
        @Option(help = "File to which metrics are dumped per compilation. A CSV format is used if the file ends with .csv " +
                        "otherwise a more human readable format is used. The fields in the CSV format are: " +
                        "compilable, compilable_identity, compilation_nr, compilation_id, metric_name, metric_value", type = OptionType.Debug)
         public static final OptionKey<String> MetricsFile = new OptionKey<>(null);
        @Option(help = "File to which aggregated metrics are dumped at shutdown. A CSV format is used if the file ends with .csv " +
                        "otherwise a more human readable format is used. If not specified, metrics are dumped to the console.", type = OptionType.Debug)
        public static final OptionKey<String> AggregatedMetricsFile = new OptionKey<>(null);

        @Option(help = "Only report metrics for threads whose name matches the regular expression.", type = OptionType.Debug)
        public static final OptionKey<String> MetricsThreadFilter = new OptionKey<>(null);
        @Option(help = "Enable debug output for stub code generation and snippet preparation.", type = OptionType.Debug)
        public static final OptionKey<Boolean> DebugStubsAndSnippets = new OptionKey<>(false);
        @Option(help = "Send Graal compiler IR to dump handlers on error.", type = OptionType.Debug)
        public static final OptionKey<Boolean> DumpOnError = new OptionKey<>(false);
        @Option(help = "Intercept also bailout exceptions", type = OptionType.Debug)
        public static final OptionKey<Boolean> InterceptBailout = new OptionKey<>(false);
        @Option(help = "Enable more verbose log output when available", type = OptionType.Debug)
        public static final OptionKey<Boolean> LogVerbose = new OptionKey<>(false);

        @Option(help = "The directory where various Graal dump files are written.")
        public static final OptionKey<String> DumpPath = new OptionKey<>("dumps");
        @Option(help = "Print the name of each dump file path as it's created.")
        public static final OptionKey<Boolean> ShowDumpFiles = new OptionKey<>(Assertions.ENABLED);

        @Option(help = "Enable dumping to the C1Visualizer. Enabling this option implies PrintBackendCFG.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintCFG = new OptionKey<>(false);
        @Option(help = "Enable dumping LIR, register allocation and code generation info to the C1Visualizer.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintBackendCFG = new OptionKey<>(true);

        @Option(help = "Output probabilities for fixed nodes during binary graph dumping.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintGraphProbabilities = new OptionKey<>(false);
        @Option(help = "Enable dumping to the IdealGraphVisualizer.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintGraph = new OptionKey<>(true);
        @Option(help = "Dump graphs in binary format instead of XML format.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintBinaryGraphs = new OptionKey<>(true);
        @Option(help = "Print graphs to files instead of sending them over the network.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintGraphFile = new OptionKey<>(false);

        @Option(help = "Host part of the address to which graphs are dumped.", type = OptionType.Debug)
        public static final OptionKey<String> PrintGraphHost = new OptionKey<>("127.0.0.1");
        @Option(help = "Port part of the address to which graphs are dumped in XML format (ignored if PrintBinaryGraphs=true).", type = OptionType.Debug)
        public static final OptionKey<Integer> PrintXmlGraphPort = new OptionKey<>(4444);
        @Option(help = "Port part of the address to which graphs are dumped in binary format (ignored if PrintBinaryGraphs=false).", type = OptionType.Debug)
        public static final OptionKey<Integer> PrintBinaryGraphPort = new OptionKey<>(4445);
        @Option(help = "Schedule graphs as they are dumped.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintGraphWithSchedule = new OptionKey<>(false);

        @Option(help = "Enable dumping Truffle ASTs to the IdealGraphVisualizer.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintTruffleTrees = new OptionKey<>(true);

        @Option(help = "Treat any exceptions during dumping as fatal.", type = OptionType.Debug)
        public static final OptionKey<Boolean> DumpingErrorsAreFatal = new OptionKey<>(false);

        @Option(help = "Enable dumping canonical text from for graphs.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintCanonicalGraphStrings = new OptionKey<>(false);
        @Option(help = "Choose format used when dumping canonical text for graphs: " +
                "0 gives a scheduled graph (better for spotting changes involving the schedule)" +
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

        // These
        @Option(help = "Deprecated - use PrintGraphHost instead.", type = OptionType.Debug)
        static final OptionKey<String> PrintIdealGraphAddress = new DeprecatedOptionKey<>(PrintGraphHost);
        @Option(help = "Deprecated - use PrintGraphWithSchedule instead.", type = OptionType.Debug)
        static final OptionKey<Boolean> PrintIdealGraphSchedule = new DeprecatedOptionKey<>(PrintGraphWithSchedule);
        @Option(help = "Deprecated - use PrintGraph instead.", type = OptionType.Debug)
        static final OptionKey<Boolean> PrintIdealGraph = new DeprecatedOptionKey<>(PrintGraph);
        @Option(help = "Deprecated - use PrintGraphFile instead.", type = OptionType.Debug)
        static final OptionKey<Boolean> PrintIdealGraphFile = new DeprecatedOptionKey<>(PrintGraphFile);
        @Option(help = "Deprecated - use PrintXmlGraphPort instead.", type = OptionType.Debug)
        static final OptionKey<Integer> PrintIdealGraphPort = new DeprecatedOptionKey<>(PrintXmlGraphPort);
        // @formatter:on
    }

    static class DeprecatedOptionKey<T> extends OptionKey<T> {
        private final OptionKey<T> replacement;

        DeprecatedOptionKey(OptionKey<T> replacement) {
            super(replacement.getDefaultValue());
            this.replacement = replacement;
        }

        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, T oldValue, T newValue) {
            // Ideally we'd use TTY here but it may not yet be initialized.
            System.err.printf("Warning: the %s option is deprecated - use %s instead%n", getName(), replacement.getName());
            replacement.update(values, newValue);
        }
    }

    /**
     * Gets the directory in which {@link DebugDumpHandler}s can generate output. This will be the
     * directory specified by {@link Options#DumpPath} if it has been set otherwise it will be
     * derived from the default value of {@link Options#DumpPath} and
     * {@link UniquePathUtilities#getGlobalTimeStamp()}.
     *
     * This method will ensure the returned directory exists, printing a message to {@link TTY} if
     * it creates it.
     *
     * @return a path as described above whose directories are guaranteed to exist
     * @throws IOException if there was an error in {@link Files#createDirectories}
     */
    public static Path getDumpDirectory(OptionValues options) throws IOException {
        Path dumpDir;
        if (Options.DumpPath.hasBeenSet(options)) {
            dumpDir = Paths.get(Options.DumpPath.getValue(options));
        } else {
            dumpDir = Paths.get(Options.DumpPath.getValue(options), String.valueOf(UniquePathUtilities.getGlobalTimeStamp()));
        }
        if (!Files.exists(dumpDir)) {
            synchronized (GraalDebugConfig.class) {
                if (!Files.exists(dumpDir)) {
                    Files.createDirectories(dumpDir);
                    TTY.println("Dumping debug output in %s", dumpDir.toAbsolutePath().toString());
                }
            }
        }
        return dumpDir;
    }

    private final OptionValues options;

    private final DebugFilter countFilter;
    private final DebugFilter logFilter;
    private final DebugFilter trackMemUseFilter;
    private final DebugFilter timerFilter;
    private final DebugFilter dumpFilter;
    private final DebugFilter verifyFilter;
    private final MethodFilter[] methodFilter;
    private final List<DebugDumpHandler> dumpHandlers;
    private final List<DebugVerifyHandler> verifyHandlers;
    private final PrintStream output;

    public GraalDebugConfig(OptionValues options) {
        this(options, TTY.out, Collections.emptyList(), Collections.emptyList());
    }

    public GraalDebugConfig(OptionValues options, PrintStream output,
                    List<DebugDumpHandler> dumpHandlers,
                    List<DebugVerifyHandler> verifyHandlers) {
        this(options, Options.Log.getValue(options),
                        Options.Count.getValue(options),
                        Options.TrackMemUse.getValue(options),
                        Options.Time.getValue(options),
                        Options.Dump.getValue(options),
                        Options.Verify.getValue(options),
                        Options.MethodFilter.getValue(options),
                        output, dumpHandlers, verifyHandlers);
    }

    public GraalDebugConfig(OptionValues options,
                    String logFilter,
                    String countFilter,
                    String trackMemUseFilter,
                    String timerFilter,
                    String dumpFilter,
                    String verifyFilter,
                    String methodFilter,
                    PrintStream output,
                    List<DebugDumpHandler> dumpHandlers,
                    List<DebugVerifyHandler> verifyHandlers) {
        this.options = options;
        this.logFilter = DebugFilter.parse(logFilter);
        this.countFilter = DebugFilter.parse(countFilter);
        this.trackMemUseFilter = DebugFilter.parse(trackMemUseFilter);
        this.timerFilter = DebugFilter.parse(timerFilter);
        this.dumpFilter = DebugFilter.parse(dumpFilter);
        this.verifyFilter = DebugFilter.parse(verifyFilter);
        if (methodFilter == null || methodFilter.isEmpty()) {
            this.methodFilter = null;
        } else {
            this.methodFilter = org.graalvm.compiler.debug.MethodFilter.parse(methodFilter);
        }

        this.dumpHandlers = Collections.unmodifiableList(dumpHandlers);
        this.verifyHandlers = Collections.unmodifiableList(verifyHandlers);
        this.output = output;
    }

    @Override
    public OptionValues getOptions() {
        return options;
    }

    @Override
    public int getLogLevel(DebugContext.Scope scope) {
        return getLevel(scope, logFilter);
    }

    @Override
    public boolean isLogEnabledForMethod(DebugContext.Scope scope) {
        return isEnabledForMethod(scope, logFilter);
    }

    @Override
    public boolean isCountEnabled(DebugContext.Scope scope) {
        return isEnabled(scope, countFilter);
    }

    @Override
    public boolean isMemUseTrackingEnabled(DebugContext.Scope scope) {
        return isEnabled(scope, trackMemUseFilter);
    }

    @Override
    public int getDumpLevel(DebugContext.Scope scope) {
        return getLevel(scope, dumpFilter);
    }

    @Override
    public boolean isDumpEnabledForMethod(DebugContext.Scope scope) {
        return isEnabledForMethod(scope, dumpFilter);
    }

    @Override
    public boolean isVerifyEnabled(DebugContext.Scope scope) {
        return isEnabled(scope, verifyFilter);
    }

    @Override
    public boolean isVerifyEnabledForMethod(DebugContext.Scope scope) {
        return isEnabledForMethod(scope, verifyFilter);
    }

    @Override
    public boolean isTimeEnabled(DebugContext.Scope scope) {
        return isEnabled(scope, timerFilter);
    }

    @Override
    public PrintStream output() {
        return output;
    }

    private boolean isEnabled(DebugContext.Scope scope, DebugFilter filter) {
        return getLevel(scope, filter) > 0;
    }

    private int getLevel(DebugContext.Scope scope, DebugFilter filter) {
        int level;
        if (filter == null) {
            level = 0;
        } else {
            String currentScope = scope.getQualifiedName();
            level = filter.matchLevel(currentScope);
        }
        if (level >= 0 && !checkMethodFilter(scope)) {
            level = -1;
        }
        return level;
    }

    private boolean isEnabledForMethod(DebugContext.Scope scope, DebugFilter filter) {
        return filter != null && checkMethodFilter(scope);
    }

    /**
     * Extracts a {@link JavaMethod} from an opaque debug context.
     *
     * @return the {@link JavaMethod} represented by {@code context} or null
     */
    public static JavaMethod asJavaMethod(Object context) {
        if (context instanceof JavaMethodContext) {
            return ((JavaMethodContext) context).asJavaMethod();
        }
        if (context instanceof JavaMethod) {
            return (JavaMethod) context;
        }
        return null;
    }

    private boolean checkMethodFilter(DebugContext.Scope scope) {
        if (methodFilter == null) {
            return true;
        } else {
            JavaMethod lastMethod = null;
            Iterable<Object> context = scope.getCurrentContext();
            for (Object o : context) {
                if (methodFilter != null) {
                    JavaMethod method = asJavaMethod(o);
                    if (method != null) {
                        if (!Options.MethodFilterRootOnly.getValue(options)) {
                            if (org.graalvm.compiler.debug.MethodFilter.matches(methodFilter, method)) {
                                return true;
                            }
                        } else {
                            /*
                             * The context values operate as a stack so if we want MethodFilter to
                             * only apply to the root method we have to check only the last method
                             * seen.
                             */
                            lastMethod = method;
                        }
                    }
                }
            }
            if (lastMethod != null && org.graalvm.compiler.debug.MethodFilter.matches(methodFilter, lastMethod)) {
                return true;
            }
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Debug config:");
        add(sb, "Log", logFilter);
        add(sb, "Count", countFilter);
        add(sb, "Time", timerFilter);
        add(sb, "Dump", dumpFilter);
        add(sb, "MethodFilter", methodFilter);
        return sb.toString();
    }

    private static void add(StringBuilder sb, String name, Object filter) {
        if (filter != null) {
            sb.append(' ');
            sb.append(name);
            sb.append('=');
            if (filter instanceof Object[]) {
                sb.append(Arrays.toString((Object[]) filter));
            } else {
                sb.append(String.valueOf(filter));
            }
        }
    }

    @Override
    public RuntimeException interceptException(DebugContext debug, Throwable e) {
        if (e instanceof BailoutException && !Options.InterceptBailout.getValue(options)) {
            return null;
        }

        OptionValues interceptOptions = new OptionValues(options,
                        Options.Count, null,
                        Options.Time, null,
                        Options.TrackMemUse, null,
                        Options.Verify, null,
                        Options.Dump, ":" + BASIC_LEVEL,
                        Options.Log, ":" + BASIC_LEVEL);
        GraalDebugConfig config = new GraalDebugConfig(interceptOptions, output, dumpHandlers, verifyHandlers);
        ScopeImpl scope = debug.currentScope;
        scope.updateFlags(config);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(baos));
            debug.log("Exception raised in scope %s: %s", debug.getCurrentScopeName(), baos);
            Map<Object, Object> firstSeen = new IdentityHashMap<>();
            for (Object o : debug.context()) {
                // Only dump a context object once.
                if (!firstSeen.containsKey(o)) {
                    firstSeen.put(o, o);
                    if (Options.DumpOnError.getValue(options) || Options.Dump.getValue(options) != null) {
                        debug.dump(DebugContext.BASIC_LEVEL, o, "Exception: %s", e);
                    } else {
                        debug.log("Context obj %s", o);
                    }
                }
            }
        } finally {
            scope.updateFlags(this);
        }
        return null;
    }

    @Override
    public Collection<DebugDumpHandler> dumpHandlers() {
        return dumpHandlers;
    }

    @Override
    public Collection<DebugVerifyHandler> verifyHandlers() {
        return verifyHandlers;
    }
}
