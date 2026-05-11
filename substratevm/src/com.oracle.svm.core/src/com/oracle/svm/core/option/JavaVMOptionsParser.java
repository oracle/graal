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
package com.oracle.svm.core.option;

import static com.oracle.svm.core.option.RuntimeOptionParser.GRAAL_OPTION_PREFIX;
import static com.oracle.svm.core.option.RuntimeOptionParser.LEGACY_GRAAL_OPTION_PREFIX;

import java.io.File;
import java.util.Arrays;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.jdk.RuntimeBootModuleLayerSupport;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.jdk.Target_java_lang_runtime_SwitchBootstraps;
import com.oracle.svm.core.jdk.Target_jdk_internal_misc_PreviewFeatures;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.shared.util.BasedOnJDKFile;

/// Parses the Java VM options that HotSpot handles in `arguments.cpp`. This parser is
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
/// all reach this parser as `--module-path=mod1.jar:mod2.jar`.
///
/// Some documented `java --help` and `java -X` options are handled entirely by the launcher and
/// do not reach VM-side option parsing. These include:
///
/// - application-selection and path options: `-cp`, `-classpath`, `--class-path`, `-jar`, `-m`,
///   `--module`, `--source`, `-d`, `--describe-module`
/// - informational, validation, and dry-run options: `--list-modules`, `--dry-run`,
///   `--validate-modules`, `--show-module-resolution`
/// - help and version options: `-?`, `-h`, `-help`, `--help`, `-X`, `--help-extra`, `-version`,
///   `--version`, `-showversion`, `--show-version`, `-fullversion`, `--full-version`
/// - launcher-specific `-X` options: `-XshowSettings`, `-XshowSettings:<sub-option>`, `-Xdiag`
/// - other launcher-only options: `-splash:<imagepath>`, `--disable-@files`
///
/// See GR-75297 for work to implement the currently unimplemented options.
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+24/src/hotspot/share/runtime/arguments.cpp")
public final class JavaVMOptionsParser {
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
    private static final String X_BOOTCLASSPATH_APPEND_OPTION_PREFIX = RuntimeOptionParser.X_OPTION_PREFIX + "bootclasspath/a:";
    private static final String RESERVED_INTERNAL_MODULE_PROPERTY_WARNING = "Ignoring system property options whose names match '-Djdk.module.*', which is reserved for internal use.";

    private static final Set<String> SYSTEM_ASSERTION_OPTIONS = Set.of(
                    "-esa",
                    "-dsa",
                    "-enablesystemassertions",
                    "-disablesystemassertions");
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

    /// Whether to preserve the Java option handling behavior that existed before GR-74762.
    private final boolean legacyJavaOptionMode;

    /// Collects system properties to initialize after recognized options are parsed.
    private final EconomicMap<String, String> properties = EconomicMap.create();

    /// Next numbered-property slot for decoded `--add-modules` options.
    private int addModulesIndex;

    /// Next numbered-property slot for decoded `--add-reads` options.
    private int addReadsIndex;

    /// Next numbered-property slot for decoded `--add-exports` options.
    private int addExportsIndex;

    /// Next numbered-property slot for decoded `--add-opens` options.
    private int addOpensIndex;

    /// Next numbered-property slot for decoded `--enable-native-access` options.
    private int enableNativeAccessIndex;

    /// Whether parsing already warned about an ignored reserved internal module property.
    private boolean warnedInternalModuleProperty;

    /// Creates a parser for one runtime option parsing pipeline.
    JavaVMOptionsParser() {
        this.legacyJavaOptionMode = SubstrateOptions.LegacyJavaOptionMode.getValue();
    }

    /// Parses implemented Java VM options and removes them from the returned list.
    ///
    /// In legacy mode, parsing is limited to the regular `-Dkey` and `-Dkey=value` properties.
    /// HotSpot-style
    /// memory sizing `-X` options are handled later by [RuntimeOptionParser] in command-line order
    /// with their `-XX:` aliases.
    ///
    /// @return the values from `args` unhandled by this method which includes:
    /// - application arguments
    /// - unknown options
    /// - runtime options handled by [RuntimeOptionParser]
    /// - recognized Java VM options that this parser does not implement yet unless not
    /// in legacy mode, in which case an error is thrown
    String[] parse(String[] args) {
        int newIdx = 0;
        for (int oldIdx = 0; oldIdx < args.length; oldIdx++) {
            String arg = args[oldIdx];
            if (parseProperty(arg) ||
                            (!legacyJavaOptionMode && (parseModuleOption(arg) ||
                                            parsePreviewOption(arg) ||
                                            parseXBootClasspathAppendOption(arg)))) {
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
        initializeProperties(properties);

        return newIdx == args.length ? args : Arrays.copyOf(args, newIdx);
    }

    /// Rejects recognized Java VM options that remain in `args` because this parser does not
    /// implement their runtime behavior yet. Unknown options are left to the caller's
    /// entry-point-specific policy.
    static void rejectRecognizedUnimplementedOptions(String[] args) {
        for (String arg : args) {
            if (isRecognizedUnimplementedOption(arg)) {
                throw unsupportedOption(arg);
            }
        }
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
    private boolean parseProperty(String arg) {
        if (!arg.startsWith(PROPERTY_PREFIX) || hasPrefix(arg, GRAAL_OPTION_PREFIX) || hasPrefix(arg, LEGACY_GRAAL_OPTION_PREFIX)) {
            return false;
        }
        if (!legacyJavaOptionMode && isReservedInternalModuleProperty(arg)) {
            if (!warnedInternalModuleProperty) {
                Log.log().string("Substrate VM warning: ").string(RESERVED_INTERNAL_MODULE_PROPERTY_WARNING).newline();
                warnedInternalModuleProperty = true;
            }
            return true;
        }
        String property = arg.substring(PROPERTY_PREFIX.length());
        int splitIndex = property.indexOf('=');
        if (splitIndex == -1) {
            properties.put(property, "");
            return true;
        }
        String key = property.substring(0, splitIndex);
        String value = property.substring(splitIndex + 1);
        properties.put(key, value);
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

    /// Parses module options that SVM applies to the runtime boot layer into the normalized
    /// `jdk.module.*` property scheme.
    private boolean parseModuleOption(String arg) {
        if (arg.startsWith(RuntimeBootModuleLayerSupport.MODULE_PATH_OPTION + "=")) {
            properties.put(RuntimeBootModuleLayerSupport.MODULE_PATH_PROPERTY, optionValue(arg));
            return true;
        }
        if (arg.startsWith(RuntimeBootModuleLayerSupport.UPGRADE_MODULE_PATH_OPTION + "=")) {
            properties.put(RuntimeBootModuleLayerSupport.UPGRADE_MODULE_PATH_PROPERTY, optionValue(arg));
            return true;
        }
        if (arg.startsWith(RuntimeBootModuleLayerSupport.ADD_MODULES_OPTION + "=")) {
            properties.put(RuntimeBootModuleLayerSupport.ADD_MODULES_PROPERTY_PREFIX + addModulesIndex++, optionValue(arg));
            return true;
        }
        if (arg.startsWith(RuntimeBootModuleLayerSupport.ADD_READS_OPTION + "=")) {
            properties.put(RuntimeBootModuleLayerSupport.ADD_READS_PROPERTY_PREFIX + addReadsIndex++, optionValue(arg));
            return true;
        }
        if (arg.startsWith(RuntimeBootModuleLayerSupport.ADD_EXPORTS_OPTION + "=")) {
            properties.put(RuntimeBootModuleLayerSupport.ADD_EXPORTS_PROPERTY_PREFIX + addExportsIndex++, optionValue(arg));
            return true;
        }
        if (arg.startsWith(RuntimeBootModuleLayerSupport.ADD_OPENS_OPTION + "=")) {
            properties.put(RuntimeBootModuleLayerSupport.ADD_OPENS_PROPERTY_PREFIX + addOpensIndex++, optionValue(arg));
            return true;
        }
        if (arg.startsWith(RuntimeBootModuleLayerSupport.ENABLE_NATIVE_ACCESS_OPTION + "=")) {
            properties.put(RuntimeBootModuleLayerSupport.ENABLE_NATIVE_ACCESS_PROPERTY_PREFIX + enableNativeAccessIndex++, optionValue(arg));
            return true;
        }
        return false;
    }

    /// Parses `--enable-preview` and enables the runtime preview-feature flag consulted by
    /// `jdk.internal.misc.PreviewFeatures`.
    private boolean parsePreviewOption(String arg) {
        assert !legacyJavaOptionMode;
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
    private boolean parseXBootClasspathAppendOption(String arg) {
        if (!arg.startsWith(X_BOOTCLASSPATH_APPEND_OPTION_PREFIX)) {
            return false;
        }

        // Buffer the property read by jdk.internal.loader.ClassLoaders.<clinit>.
        String value = arg.substring(X_BOOTCLASSPATH_APPEND_OPTION_PREFIX.length());
        if (!value.isEmpty()) {
            String currentValue = properties.get(BOOT_CLASS_PATH_APPEND_PROPERTY);
            String result = currentValue != null ? currentValue + File.pathSeparator + value : value;
            properties.put(BOOT_CLASS_PATH_APPEND_PROPERTY, result);
        }
        return true;
    }

    /// Returns whether `arg` is a recognized but unimplemented VM option.
    private static boolean isRecognizedUnimplementedOption(String arg) {
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

}
