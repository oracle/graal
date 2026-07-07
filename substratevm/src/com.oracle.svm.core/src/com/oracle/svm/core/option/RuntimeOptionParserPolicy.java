/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.jdk.Target_java_lang_runtime_SwitchBootstraps;
import com.oracle.svm.core.jdk.Target_jdk_internal_misc_PreviewFeatures;
import com.oracle.svm.core.log.FunctionPointerLogHandler;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.guest.staging.ArgsSupport;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.guest.staging.option.RuntimeBootModuleLayerOptions;
import com.oracle.svm.guest.staging.option.RuntimeOptionParser;
import com.oracle.svm.guest.staging.option.XOptions;
import com.oracle.svm.shared.option.CommonOptionParser.BooleanOptionFormat;
import com.oracle.svm.shared.option.CommonOptionParser.OptionParseResult;
import com.oracle.svm.shared.util.BasedOnJDKFile;

import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

/**
 * Transitional runtime startup shell for parsing options supported by Substrate VM. This code
 * remains in core only while its isolate, Java VM option, and {@link XOptions} dependencies are not
 * guest-safe. Generic runtime option parsing and state updates are owned by
 * {@link RuntimeOptionParser}.
 */
public final class RuntimeOptionParserPolicy {

    /**
     * The suggested prefix for all VM options available in an application based on Substrate VM.
     */
    private static final String NORMAL_OPTION_PREFIX = "-XX:";

    /**
     * The prefix for Graal style options available in an application based on Substrate VM.
     */
    private static final String GRAAL_OPTION_PREFIX = "-Djdk.graal.";

    /**
     * The legacy prefix for Graal style options available in an application based on Substrate VM.
     */
    private static final String LEGACY_GRAAL_OPTION_PREFIX = "-Dgraal.";

    /**
     * The prefix for XOptions available in an application based on Substrate VM.
     */
    static final String X_OPTION_PREFIX = RuntimeOptionParser.X_OPTION_PREFIX;

    private static final String PROPERTY_PREFIX = "-D";
    private static final String BOOT_CLASS_PATH_APPEND_PROPERTY = "jdk.boot.class.path.append";
    private static final String MODULE_PROPERTY_PREFIX = "jdk.module.";
    private static final String[] RESERVED_INTERNAL_MODULE_PROPERTY_SUFFIXES = {
                    "patch",
                    "limitmods",
                    "upgrade.path",
                    "illegal.native.access",
                    "addexports",
                    "addopens",
                    "addreads",
                    "path",
                    "addmods",
                    "enable.native.access"
    };

    private static final String ENABLE_PREVIEW_OPTION = "--enable-preview";
    private static final String FINALIZATION_OPTION_PREFIX = "--finalization=";
    private static final String ILLEGAL_NATIVE_ACCESS_OPTION_PREFIX = "--illegal-native-access=";
    private static final String SUN_MISC_UNSAFE_MEMORY_ACCESS_OPTION_PREFIX = "--sun-misc-unsafe-memory-access=";
    private static final String X_BOOTCLASSPATH_APPEND_OPTION_PREFIX = X_OPTION_PREFIX + "bootclasspath/a:";
    private static final String XLOG_OPTION_PREFIX = "-Xlog:";
    private static final String JAVA_AGENT_OPTION_PREFIX = "-javaagent:";
    private static final String AGENT_LIB_OPTION_PREFIX = "-agentlib:";
    private static final String AGENT_PATH_OPTION_PREFIX = "-agentpath:";
    private static final String LOG_FILE_OPTION = "LogFile";
    private static final String LOG_FILE_OPTION_PREFIX = NORMAL_OPTION_PREFIX + LOG_FILE_OPTION + "=";
    private static final String HOTSPOT_OPTION_COMPATIBILITY_NAME = "CREMA_HOTSPOT_OPTION_COMPATIBILITY";
    private static final String RESERVED_INTERNAL_MODULE_PROPERTY_WARNING = "Ignoring system property options whose names match '-Djdk.module.*', which is reserved for internal use.";

    private static final Set<String> SYSTEM_ASSERTION_OPTIONS = Set.of(
                    "-esa",
                    "-dsa",
                    "-enablesystemassertions",
                    "-disablesystemassertions");
    private static final Set<String> RECOGNIZED_BOOLEAN_HOTSPOT_COMPATIBILITY_OPTIONS = Set.of(
                    "UnlockExperimentalVMOptions",
                    "UnlockDiagnosticVMOptions",
                    "AbortVMOnCompilationFailure",
                    "IgnoreUnrecognizedVMOptions",
                    "TieredCompilation",
                    "UseCompressedOops",
                    "VerifyDependencies",
                    "DisplayVMOutput",
                    "LogVMOutput",
                    "PrintFlagsFinal");
    private static final Set<String> RECOGNIZED_VALUE_HOTSPOT_COMPATIBILITY_OPTIONS = Set.of(
                    "ReservedCodeCacheSize",
                    "TieredStopAtLevel",
                    "MaxRAMPercentage",
                    "DisableIntrinsic",
                    "CompileThresholdScaling");
    private static final Set<String> IGNORED_COMPATIBILITY_OPTIONS = Set.of(
                    "-ea",
                    "-esa",
                    "-Xcomp",
                    "-Xbatch",
                    "-Xint",
                    "-Xverify:all");
    private static final Set<String> EXACT_RECOGNIZED_X_OPTIONS = Set.of(
                    "-Xnoclassgc",
                    "-Xbatch",
                    "-Xrs",
                    "-Xprof",
                    "-Xint",
                    "-Xmixed",
                    "-Xcomp",
                    "-Xshare:dump",
                    "-Xshare:on",
                    "-Xshare:auto",
                    "-Xshare:off",
                    "-Xverify",
                    "-Xverify:all",
                    "-Xverify:remote",
                    "-Xverify:none",
                    "-Xdebug",
                    "-Xcheck:jni");

    /// Parse and consume all standard options and system properties supported by Substrate VM. The
    /// returned array contains all arguments that were not consumed, i.e., were not recognized as
    /// options.
    ///
    /// Note that the logic of whether to parse options must be in sync with
    /// [IsolateArgumentParser#shouldParseArguments].
    public static String[] parseAndConsumeAllOptions(String[] initialArgs, boolean ignoreUnrecognized) {
        boolean parseRuntimeOptions = SubstrateOptions.ParseRuntimeOptions.getValue() ||
                        RuntimeCompilation.isEnabled() && SubstrateOptions.SupportCompileInIsolates.getValue() && IsolateArgumentParser.isCompilationIsolate();
        if (!parseRuntimeOptions) {
            return initialArgs;
        }

        ParseContext context = new ParseContext();
        String[] args = parseJavaVMOptions(initialArgs, context);
        args = consumeCompatibilityOptions(args);
        args = parseRuntimeOptions(args, ignoreUnrecognized);
        if (!context.legacyJavaOptionMode) {
            rejectRecognizedUnimplementedJavaOptions(args);
        }
        configureLogFile(context.logFile);
        return args;
    }

    /** Parses runtime options for a Java main image and returns the application main arguments. */
    public static String[] parseAndConsumeJavaMainOptions(String[] initialArgs, boolean ignoreUnrecognized) {
        if (SubstrateOptions.LegacyJavaOptionMode.getValue()) {
            return parseAndConsumeAllOptions(initialArgs, ignoreUnrecognized);
        }

        int separatorIndex = ArgsSupport.firstEndOfOptionsMarkerIndex(initialArgs);
        if (separatorIndex == -1) {
            return parseAndConsumeAllOptions(initialArgs.clone(), ignoreUnrecognized);
        }

        String[] remainingArgs = parseAndConsumeAllOptions(Arrays.copyOf(initialArgs, separatorIndex), ignoreUnrecognized);
        if (!ignoreUnrecognized && remainingArgs.length != 0) {
            throw new IllegalArgumentException("Unrecognized option: " + remainingArgs[0]);
        }

        return Arrays.copyOfRange(initialArgs, separatorIndex + 1, initialArgs.length);
    }

    /// Configures the low level log file after all runtime options have been parsed.
    private static void configureLogFile(String logFile) {
        if (logFile != null) {
            RuntimeSupport.Hook closeLogFile = FunctionPointerLogHandler.configureLogFile(LOG_FILE_OPTION_PREFIX, logFile);
            RuntimeSupport.getRuntimeSupport().addTearDownHook(closeLogFile);
        }
    }

    /**
     * Parses {@code args} and sets/updates runtime option values for the elements matching a
     * runtime option.
     *
     * @param args arguments to be parsed
     * @return elements in {@code args} that do not match any runtime options
     * @throws IllegalArgumentException if an element in {@code args} is invalid. The parse error is
     *             described by {@link Throwable#getMessage()}.
     */
    private static String[] parseRuntimeOptions(String[] args, boolean ignoreUnrecognized) {
        RuntimeOptionParser parser = RuntimeOptionParser.singleton();
        int newIdx = 0;
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        for (int oldIdx = 0; oldIdx < args.length; oldIdx++) {
            String arg = args[oldIdx];
            if (arg.startsWith(NORMAL_OPTION_PREFIX)) {
                parseOptionAtRuntime(arg, NORMAL_OPTION_PREFIX, BooleanOptionFormat.PLUS_MINUS, values, ignoreUnrecognized);
            } else if (arg.startsWith(GRAAL_OPTION_PREFIX)) {
                parseOptionAtRuntime(arg, GRAAL_OPTION_PREFIX, BooleanOptionFormat.NAME_VALUE, values, ignoreUnrecognized);
            } else if (arg.startsWith(LEGACY_GRAAL_OPTION_PREFIX)) {
                parseOptionAtRuntime(arg, LEGACY_GRAAL_OPTION_PREFIX, BooleanOptionFormat.NAME_VALUE, values, ignoreUnrecognized);
            } else if (arg.startsWith(X_OPTION_PREFIX) && XOptions.parse(arg.substring(X_OPTION_PREFIX.length()), values)) {
                // option value was already parsed and added to the map
            } else {
                assert newIdx <= oldIdx;
                args[newIdx] = arg;
                newIdx += 1;
            }
        }

        parser.update(values);
        if (newIdx == args.length) {
            /* We can be allocation free and just return the original arguments. */
            return args;
        } else {
            return Arrays.copyOf(args, newIdx);
        }
    }

    /**
     * Parse one option at runtime and set its value.
     *
     * @param arg argument to be parsed
     * @param optionPrefix prefix for the runtime option
     * @throws IllegalArgumentException if {@code arg} is invalid. The parse error is described by
     *             {@link Throwable#getMessage()}.
     */
    public static void parseOptionAtRuntime(String arg, String optionPrefix, BooleanOptionFormat booleanOptionFormat, EconomicMap<OptionKey<?>, Object> values, boolean ignoreUnrecognized) {
        RuntimeOptionParser parser = RuntimeOptionParser.singleton();
        OptionParseResult parseResult = parser.parseOption(arg, optionPrefix, booleanOptionFormat, values);
        if (parseResult.printFlags() || parseResult.printFlagsWithExtraHelp()) {
            parser.printFlags(parseResult, optionPrefix, CoreLogSupport.logStream());
            System.exit(0);
        }
        if (!parseResult.isValid()) {
            if (parseResult.optionUnrecognized() && ignoreUnrecognized) {
                return;
            }
            throw new IllegalArgumentException(parseResult.getError());
        }

        // Print a warning if the option is deprecated.
        OptionKey<?> option = parseResult.getOptionKey();
        OptionDescriptor descriptor = option.getDescriptor();
        if (descriptor != null && descriptor.isDeprecated()) {
            Log log = CoreLogSupport.log();
            // Checkstyle: Allow raw info or warning printing - begin
            log.string("Warning: Option '").string(descriptor.getName()).string("' is deprecated and might be removed from future versions");
            // Checkstyle: Allow raw info or warning printing - end
            String deprecationMessage = descriptor.getDeprecationMessage();
            if (deprecationMessage != null && !deprecationMessage.isEmpty()) {
                log.string(": ").string(deprecationMessage);
            }
            log.newline();
        }
    }

    /// Parses the Java VM options that HotSpot handles in `arguments.cpp`. This parser phase is
    /// entry-point agnostic: it receives options from `CreateJavaVM`, `graal_create_isolate`, and
    /// native image `main` startup after any entry-point-specific preprocessing has completed. It
    /// consumes only implemented Java VM options and leaves every other option in the returned
    /// array so the caller can apply the appropriate unknown-option policy for the entry point.
    ///
    /// When options originate from the `java` launcher, the launcher performs normalization before
    /// the VM sees them. For example, the launcher forms:
    ///
    /// - `-p mod1.jar:mod2.jar`
    /// - `--module-path mod1.jar:mod2.jar`
    /// - `--module-path=mod1.jar:mod2.jar`
    ///
    /// all reach this parser phase as `--module-path=mod1.jar:mod2.jar`.
    ///
    /// Some documented `java --help` and `java -X` options are handled entirely by the launcher and
    /// do not reach VM-side option parsing. These include:
    ///
    /// - application-selection and path options: `-cp`, `-classpath`, `--class-path`, `-jar`, `-m`,
    /// `--module`, `--source`, `-d`, `--describe-module`
    /// - informational, validation, and dry-run options: `--list-modules`, `--dry-run`,
    /// `--validate-modules`, `--show-module-resolution`
    /// - help and version options: `-?`, `-h`, `-help`, `--help`, `-X`, `--help-extra`, `-version`,
    /// `--version`, `-showversion`, `--show-version`, `-fullversion`, `--full-version`
    /// - launcher-specific `-X` options: `-XshowSettings`, `-XshowSettings:<sub-option>`, `-Xdiag`
    /// - other launcher-only options: `-splash:<imagepath>`, `--disable-@files`
    ///
    /// See GR-75297 for work to implement the currently unimplemented options.
    ///
    /// @return the entries in `args` that were not consumed
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.2-b20/src/hotspot/share/runtime/arguments.cpp")
    private static String[] parseJavaVMOptions(String[] args, ParseContext context) {
        int newIdx = 0;
        for (int oldIdx = 0; oldIdx < args.length; oldIdx++) {
            String arg = args[oldIdx];
            if (arg.startsWith(LOG_FILE_OPTION_PREFIX)) {
                context.logFile = arg.substring(LOG_FILE_OPTION_PREFIX.length());
                if (context.logFile.isEmpty()) {
                    throw new IllegalArgumentException("The " + LOG_FILE_OPTION + " option requires a non-empty path");
                }
                continue;
            }
            if (parseProperty(arg, context) ||
                            (!context.legacyJavaOptionMode && (parseModuleOption(arg, context) ||
                                            parsePreviewOption(arg) ||
                                            parseXBootClasspathAppendOption(arg, context)))) {
                continue;
            }
            assert newIdx <= oldIdx;
            args[newIdx] = arg;
            newIdx++;
        }
        /*
         * Later runtime option parsing can execute non-trivial Java code via option value updates.
         * Initialize all system properties first so JDK code cannot cache stale values.
         */
        initializeProperties(context.properties);

        return newIdx == args.length ? args : Arrays.copyOf(args, newIdx);
    }

    /// Initializes system properties derived from recognized Java VM options.
    ///
    /// @param properties the VM option-derived system properties to initialize
    private static void initializeProperties(EconomicMap<String, String> properties) {
        MapCursor<String, String> cursor = properties.getEntries();
        while (cursor.advance()) {
            SystemPropertiesSupport.singleton().initializeProperty(cursor.getKey(), cursor.getValue());
        }
    }

    /// Parses a non-Graal `-Dkey` or `-Dkey=value` property and records it if valid.
    private static boolean parseProperty(String arg, ParseContext context) {
        if (!arg.startsWith(PROPERTY_PREFIX) || hasPrefix(arg, GRAAL_OPTION_PREFIX) || hasPrefix(arg, LEGACY_GRAAL_OPTION_PREFIX)) {
            return false;
        }
        if (!context.legacyJavaOptionMode && isReservedInternalModuleProperty(arg)) {
            if (!context.warnedInternalModuleProperty) {
                CoreLogSupport.log().string("Substrate VM warning: ").string(RESERVED_INTERNAL_MODULE_PROPERTY_WARNING).newline();
                context.warnedInternalModuleProperty = true;
            }
            return true;
        }
        String property = arg.substring(PROPERTY_PREFIX.length());
        int splitIndex = property.indexOf('=');
        if (splitIndex == -1) {
            context.properties.put(property, "");
            return true;
        }
        String key = property.substring(0, splitIndex);
        String value = property.substring(splitIndex + 1);
        context.properties.put(key, value);
        return true;
    }

    /// Returns `true` if `arg` names one of the reserved `jdk.module.*` properties.
    private static boolean isReservedInternalModuleProperty(String arg) {
        if (!arg.startsWith(PROPERTY_PREFIX + MODULE_PROPERTY_PREFIX)) {
            return false;
        }
        int splitIndex = arg.indexOf('=');
        int propertyNameEnd = splitIndex == -1 ? arg.length() : splitIndex;
        String propertyName = arg.substring(PROPERTY_PREFIX.length(), propertyNameEnd);
        String propertySuffix = propertyName.substring(MODULE_PROPERTY_PREFIX.length());
        for (String reservedSuffix : RESERVED_INTERNAL_MODULE_PROPERTY_SUFFIXES) {
            if (propertySuffix.equals(reservedSuffix) || propertySuffix.startsWith(reservedSuffix + ".")) {
                return true;
            }
        }
        return false;
    }

    /// Consumes compatibility options commonly passed by jtreg and the JDK test harness.
    private static String[] consumeCompatibilityOptions(String[] inArgs) {
        if (!Boolean.getBoolean(HOTSPOT_OPTION_COMPATIBILITY_NAME)) {
            return inArgs;
        }
        List<String> remainingArgs = new ArrayList<>();
        for (String arg : inArgs) {
            if (!parseCompatibilityOption(arg)) {
                remainingArgs.add(arg);
            }
        }
        return remainingArgs.toArray(new String[0]);
    }

    /// Parses HotSpot compatibility options commonly passed by jtreg and the JDK test harness.
    private static boolean parseCompatibilityOption(String arg) {
        if (parseCompatibilityBooleanOption(arg) || parseCompatibilityValueOption(arg)) {
            return true;
        }
        if (IGNORED_COMPATIBILITY_OPTIONS.contains(arg) ||
                        arg.startsWith(XLOG_OPTION_PREFIX) ||
                        arg.startsWith(JAVA_AGENT_OPTION_PREFIX) ||
                        arg.startsWith(AGENT_LIB_OPTION_PREFIX) ||
                        arg.startsWith(AGENT_PATH_OPTION_PREFIX)) {
            warnIgnoredCompatibilityOption(arg);
            return true;
        }
        return false;
    }

    /// Parses a HotSpot `-XX:+name` or `-XX:-name` compatibility option.
    private static boolean parseCompatibilityBooleanOption(String arg) {
        if (!arg.startsWith(NORMAL_OPTION_PREFIX) || arg.length() <= NORMAL_OPTION_PREFIX.length()) {
            return false;
        }
        char sign = arg.charAt(NORMAL_OPTION_PREFIX.length());
        if (sign != '+' && sign != '-') {
            return false;
        }
        String name = arg.substring(NORMAL_OPTION_PREFIX.length() + 1);
        if (!RECOGNIZED_BOOLEAN_HOTSPOT_COMPATIBILITY_OPTIONS.contains(name)) {
            return false;
        }
        warnIgnoredCompatibilityOption(arg);
        return true;
    }

    /// Parses a HotSpot `-XX:name=value` compatibility option.
    private static boolean parseCompatibilityValueOption(String arg) {
        if (!arg.startsWith(NORMAL_OPTION_PREFIX)) {
            return false;
        }
        int equalsIndex = arg.indexOf('=', NORMAL_OPTION_PREFIX.length());
        if (equalsIndex == -1) {
            return false;
        }
        String name = arg.substring(NORMAL_OPTION_PREFIX.length(), equalsIndex);
        if (!RECOGNIZED_VALUE_HOTSPOT_COMPATIBILITY_OPTIONS.contains(name)) {
            return false;
        }
        warnIgnoredCompatibilityOption(arg);
        return true;
    }

    /// Emits a HotSpot compatibility warning for options accepted without runtime effect.
    private static void warnIgnoredCompatibilityOption(String arg) {
        Log.log().string("Substrate VM warning: ignoring Java VM option ").string(arg).newline();
    }

    /// Parses module options that SVM applies to the runtime boot layer into the normalized
    /// `jdk.module.*` property scheme.
    private static boolean parseModuleOption(String arg, ParseContext context) {
        if (arg.startsWith(RuntimeBootModuleLayerOptions.MODULE_PATH_OPTION + "=")) {
            context.properties.put(RuntimeBootModuleLayerOptions.MODULE_PATH_PROPERTY, optionValue(arg));
            return true;
        }
        if (arg.startsWith(RuntimeBootModuleLayerOptions.UPGRADE_MODULE_PATH_OPTION + "=")) {
            context.properties.put(RuntimeBootModuleLayerOptions.UPGRADE_MODULE_PATH_PROPERTY, optionValue(arg));
            return true;
        }
        if (arg.startsWith(RuntimeBootModuleLayerOptions.ADD_MODULES_OPTION + "=")) {
            context.properties.put(RuntimeBootModuleLayerOptions.ADD_MODULES_PROPERTY_PREFIX + context.addModulesIndex++, optionValue(arg));
            return true;
        }
        if (arg.startsWith(RuntimeBootModuleLayerOptions.ADD_READS_OPTION + "=")) {
            context.properties.put(RuntimeBootModuleLayerOptions.ADD_READS_PROPERTY_PREFIX + context.addReadsIndex++, optionValue(arg));
            return true;
        }
        if (arg.startsWith(RuntimeBootModuleLayerOptions.ADD_EXPORTS_OPTION + "=")) {
            context.properties.put(RuntimeBootModuleLayerOptions.ADD_EXPORTS_PROPERTY_PREFIX + context.addExportsIndex++, optionValue(arg));
            return true;
        }
        if (arg.startsWith(RuntimeBootModuleLayerOptions.ADD_OPENS_OPTION + "=")) {
            context.properties.put(RuntimeBootModuleLayerOptions.ADD_OPENS_PROPERTY_PREFIX + context.addOpensIndex++, optionValue(arg));
            return true;
        }
        if (arg.startsWith(RuntimeBootModuleLayerOptions.ENABLE_NATIVE_ACCESS_OPTION + "=")) {
            context.properties.put(RuntimeBootModuleLayerOptions.ENABLE_NATIVE_ACCESS_PROPERTY_PREFIX + context.enableNativeAccessIndex++, optionValue(arg));
            return true;
        }
        return false;
    }

    /// Parses `--enable-preview` and enables the runtime preview-feature flag consulted by
    /// `jdk.internal.misc.PreviewFeatures`.
    private static boolean parsePreviewOption(String arg) {
        if (!arg.equals(ENABLE_PREVIEW_OPTION)) {
            return false;
        }
        Target_jdk_internal_misc_PreviewFeatures.ENABLED = true;
        Target_java_lang_runtime_SwitchBootstraps.previewEnabled = true;
        return true;
    }

    /// Extracts the substring after the first `=` in a launcher-normalized option.
    private static String optionValue(String arg) {
        return arg.substring(arg.indexOf('=') + 1);
    }

    /// Parses `-Xbootclasspath/a:` on the VM side of the launcher boundary.
    private static boolean parseXBootClasspathAppendOption(String arg, ParseContext context) {
        if (!arg.startsWith(X_BOOTCLASSPATH_APPEND_OPTION_PREFIX)) {
            return false;
        }

        // Buffer the property read by jdk.internal.loader.ClassLoaders.<clinit>.
        String value = arg.substring(X_BOOTCLASSPATH_APPEND_OPTION_PREFIX.length());
        if (!value.isEmpty()) {
            String currentValue = context.properties.get(BOOT_CLASS_PATH_APPEND_PROPERTY);
            String result = currentValue != null ? currentValue + File.pathSeparator + value : value;
            context.properties.put(BOOT_CLASS_PATH_APPEND_PROPERTY, result);
        }
        return true;
    }

    /// Rejects recognized Java VM options that remain in `args` because this parser does not
    /// implement their runtime behavior yet. Unknown options are left to the caller's
    /// entry-point-specific policy.
    private static void rejectRecognizedUnimplementedJavaOptions(String[] args) {
        for (String arg : args) {
            if (isRecognizedUnimplementedJavaOption(arg)) {
                throw unsupportedOption(arg);
            }
        }
    }

    /// Returns whether `arg` is a recognized but unimplemented VM option.
    private static boolean isRecognizedUnimplementedJavaOption(String arg) {
        if (arg.startsWith(FINALIZATION_OPTION_PREFIX)) {
            return true;
        }
        if (isEnableAssertionsOption(arg)) {
            return true;
        }
        if (isDisableAssertionsOption(arg)) {
            return true;
        }
        if (SYSTEM_ASSERTION_OPTIONS.contains(arg)) {
            return true;
        }
        if (arg.startsWith("-agentlib:")) {
            return true;
        }
        if (arg.startsWith("-agentpath:")) {
            return true;
        }
        if (arg.startsWith("-javaagent:")) {
            return true;
        }
        if (arg.equals("-verbose") || arg.startsWith("-verbose:")) {
            return true;
        }
        if (arg.startsWith(ILLEGAL_NATIVE_ACCESS_OPTION_PREFIX)) {
            return true;
        }
        if (arg.startsWith(SUN_MISC_UNSAFE_MEMORY_ACCESS_OPTION_PREFIX)) {
            return true;
        }
        if (arg.startsWith("--patch-module=")) {
            return true;
        }
        if (arg.startsWith("--limit-modules=")) {
            return true;
        }
        if (arg.equals("-Xlog") || arg.startsWith("-Xlog:")) {
            return true;
        }
        if (arg.startsWith("-Xloggc:")) {
            return true;
        }
        return EXACT_RECOGNIZED_X_OPTIONS.contains(arg) || isRecognizedXShareOption(arg) || isRecognizedXVerifyOption(arg);
    }

    /// Returns whether `arg` selects the enable-assertions family, including `:<target>` forms.
    private static boolean isEnableAssertionsOption(String arg) {
        return arg.equals("-ea") || arg.equals("-enableassertions") || arg.startsWith("-ea:") || arg.startsWith("-enableassertions:");
    }

    /// Returns whether `arg` selects the disable-assertions family, including `:<target>` forms.
    private static boolean isDisableAssertionsOption(String arg) {
        return arg.equals("-da") || arg.equals("-disableassertions") || arg.startsWith("-da:") || arg.startsWith("-disableassertions:");
    }

    /// Returns whether `arg` is a recognized `-Xshare` mode.
    private static boolean isRecognizedXShareOption(String arg) {
        return arg.equals("-Xshare:dump") || arg.equals("-Xshare:on") || arg.equals("-Xshare:auto") || arg.equals("-Xshare:off");
    }

    /// Returns whether `arg` is a recognized `-Xverify` mode.
    private static boolean isRecognizedXVerifyOption(String arg) {
        return arg.equals("-Xverify") || arg.equals("-Xverify:all") || arg.equals("-Xverify:remote") || arg.equals("-Xverify:none");
    }

    /// Returns whether `arg` starts with `prefix`, treating a null prefix as absent.
    private static boolean hasPrefix(String arg, String prefix) {
        return prefix != null && arg.startsWith(prefix);
    }

    /// Creates the exception used for Java VM options that are recognized but not yet implemented.
    private static IllegalArgumentException unsupportedOption(String arg) {
        return new IllegalArgumentException("The option '" + arg + "' is not supported by Native Image");
    }

    private static final class ParseContext {
        /// Whether to preserve the Java option handling behavior that existed before GR-74762.
        final boolean legacyJavaOptionMode = SubstrateOptions.LegacyJavaOptionMode.getValue();

        /// Collects system properties to initialize after recognized options are parsed.
        final EconomicMap<String, String> properties = EconomicMap.create();

        /// Next numbered-property slot for decoded `--add-modules` options.
        int addModulesIndex;

        /// Next numbered-property slot for decoded `--add-reads` options.
        int addReadsIndex;

        /// Next numbered-property slot for decoded `--add-exports` options.
        int addExportsIndex;

        /// Next numbered-property slot for decoded `--add-opens` options.
        int addOpensIndex;

        /// Next numbered-property slot for decoded `--enable-native-access` options.
        int enableNativeAccessIndex;

        /// Whether parsing already warned about an ignored reserved internal module property.
        boolean warnedInternalModuleProperty;

        /// Value parsed for `-XX:LogFile=<path>`.
        String logFile;
    }

    private RuntimeOptionParserPolicy() {
    }
}
