/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libjavavm;

import static com.oracle.truffle.espresso.libjavavm.jniapi.JNIErrors.JNI_ERR;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.polyglot.Context;
import org.graalvm.word.Pointer;

import com.oracle.truffle.espresso.libjavavm.arghelper.ArgumentsHandler;
import com.oracle.truffle.espresso.libjavavm.jniapi.JNIErrors;
import com.oracle.truffle.espresso.libjavavm.jniapi.JNIJavaVMInitArgs;
import com.oracle.truffle.espresso.libjavavm.jniapi.JNIJavaVMOption;

public final class Arguments {
    private static final PrintStream STDERR = System.err;

    public static final String JAVA_PROPS = "java.Properties.";

    private static final String AGENT_LIB = "java.AgentLib.";
    private static final String AGENT_PATH = "java.AgentPath.";
    private static final String JAVA_AGENT = "java.JavaAgent";

    /*
     * HotSpot comment:
     * 
     * the -Djava.class.path and the -Dsun.java.command options are omitted from jvm_args string as
     * each have their own PerfData string constant object.
     */
    private static final List<String> ignoredJvmArgs = Arrays.asList(
                    "-Djava.class.path",
                    "-Dsun.java.command",
                    "-Dsun.java.launcher");

    private Arguments() {
    }

    private static final Set<String> IGNORED_XX_OPTIONS = Set.of(
                    "ReservedCodeCacheSize",
                    // `TieredStopAtLevel=0` is handled separately, other values are ignored
                    "TieredStopAtLevel",
                    "MaxMetaspaceSize",
                    "HeapDumpOnOutOfMemoryError");

    private static final Map<String, String> MAPPED_XX_OPTIONS = Map.of(
                    "TieredCompilation", "engine.MultiTier");

    public static int setupContext(Context.Builder builder, JNIJavaVMInitArgs args) {
        Pointer p = (Pointer) args.getOptions();
        int count = args.getNOptions();
        String classpath = null;
        String bootClasspathPrepend = null;
        String bootClasspathAppend = null;

        ArgumentsHandler handler = new ArgumentsHandler(builder, IGNORED_XX_OPTIONS, MAPPED_XX_OPTIONS, args);
        List<String> jvmArgs = new ArrayList<>();

        boolean ignoreUnrecognized = false;
        boolean autoAdjustHeapSize = true;
        List<String> xOptions = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            JNIJavaVMOption option = (JNIJavaVMOption) p.add(i * SizeOf.get(JNIJavaVMOption.class));
            CCharPointer str = option.getOptionString();
            try {
                if (str.isNonNull()) {
                    String optionString = CTypeConversion.toJavaString(option.getOptionString());
                    buildJvmArg(jvmArgs, optionString);
                    if (optionString.startsWith("-Xbootclasspath:")) {
                        bootClasspathPrepend = null;
                        bootClasspathAppend = null;
                        builder.option("java.BootClasspath", optionString.substring("-Xbootclasspath:".length()));
                    } else if (optionString.startsWith("-Xbootclasspath/a:")) {
                        bootClasspathAppend = appendPath(bootClasspathAppend, optionString.substring("-Xbootclasspath/a:".length()));
                    } else if (optionString.startsWith("-Xbootclasspath/p:")) {
                        bootClasspathPrepend = prependPath(optionString.substring("-Xbootclasspath/p:".length()), bootClasspathPrepend);
                    } else if (optionString.startsWith("-Xverify:")) {
                        String mode = optionString.substring("-Xverify:".length());
                        builder.option("java.Verify", mode);
                    } else if (optionString.startsWith("-Xrunjdwp:")) {
                        String value = optionString.substring("-Xrunjdwp:".length());
                        builder.option("java.JDWPOptions", value);
                    } else if (optionString.startsWith("-agentlib:jdwp=")) {
                        String value = optionString.substring("-agentlib:jdwp=".length());
                        builder.option("java.JDWPOptions", value);
                    } else if (optionString.startsWith("-javaagent:")) {
                        String value = optionString.substring("-javaagent:".length());
                        builder.option(JAVA_AGENT, value);
                        handler.addModules("java.instrument");
                    } else if (optionString.startsWith("-agentlib:")) {
                        String[] split = splitEquals(optionString.substring("-agentlib:".length()));
                        builder.option(AGENT_LIB + split[0], split[1]);
                    } else if (optionString.startsWith("-agentpath:")) {
                        String[] split = splitEquals(optionString.substring("-agentpath:".length()));
                        builder.option(AGENT_PATH + split[0], split[1]);
                    } else if (optionString.startsWith("-D")) {
                        String key = optionString.substring("-D".length());
                        int splitAt = key.indexOf("=");
                        String value = "";
                        if (splitAt >= 0) {
                            value = key.substring(splitAt + 1);
                            key = key.substring(0, splitAt);
                        }
                        if (handler.isModulesOption(key)) {
                            warn("Ignoring system property -D" + key + " that is reserved for internal use.");
                            continue;
                        }
                        switch (key) {
                            case "espresso.library.path":
                                builder.option("java.EspressoLibraryPath", value);
                                break;
                            case "java.library.path":
                                builder.option("java.JavaLibraryPath", value);
                                break;
                            case "java.class.path":
                                classpath = value;
                                break;
                            case "java.ext.dirs":
                                builder.option("java.ExtDirs", value);
                                break;
                            case "sun.boot.class.path":
                                builder.option("java.BootClasspath", value);
                                break;
                            case "sun.boot.library.path":
                                builder.option("java.BootLibraryPath", value);
                                break;
                        }
                        builder.option(JAVA_PROPS + key, value);
                    } else if (optionString.equals("-ea") || optionString.equals("-enableassertions")) {
                        builder.option("java.EnableAssertions", "true");
                    } else if (optionString.equals("-esa") || optionString.equals("-enablesystemassertions")) {
                        builder.option("java.EnableSystemAssertions", "true");
                    } else if (optionString.startsWith("--add-reads=")) {
                        handler.addReads(optionString.substring("--add-reads=".length()));
                    } else if (optionString.startsWith("--add-exports=")) {
                        handler.addExports(optionString.substring("--add-exports=".length()));
                    } else if (optionString.startsWith("--add-opens=")) {
                        handler.addOpens(optionString.substring("--add-opens=".length()));
                    } else if (optionString.startsWith("--add-modules=")) {
                        handler.addModules(optionString.substring("--add-modules=".length()));
                    } else if (optionString.startsWith("--enable-native-access=")) {
                        handler.enableNativeAccess(optionString.substring("--enable-native-access=".length()));
                    } else if (optionString.startsWith("--module-path=")) {
                        builder.option(JAVA_PROPS + "jdk.module.path", optionString.substring("--module-path=".length()));
                    } else if (optionString.startsWith("--upgrade-module-path=")) {
                        builder.option(JAVA_PROPS + "jdk.module.upgrade.path", optionString.substring("--upgrade-module-path=".length()));
                    } else if (optionString.startsWith("--limit-modules=")) {
                        builder.option(JAVA_PROPS + "jdk.module.limitmods", optionString.substring("--limit-modules=".length()));
                    } else if (optionString.equals("--enable-preview")) {
                        builder.option("java.EnablePreview", "true");
                    } else if (optionString.equals("-XX:-AutoAdjustHeapSize")) {
                        autoAdjustHeapSize = false;
                    } else if (optionString.equals("-XX:+AutoAdjustHeapSize")) {
                        autoAdjustHeapSize = true;
                    } else if (isXOption(optionString)) {
                        xOptions.add(optionString);
                    } else if (optionString.equals("-XX:+IgnoreUnrecognizedVMOptions")) {
                        ignoreUnrecognized = true;
                    } else if (optionString.equals("-XX:-IgnoreUnrecognizedVMOptions")) {
                        ignoreUnrecognized = false;
                    } else if (optionString.equals("-XX:+UnlockExperimentalVMOptions") ||
                                    optionString.equals("-XX:+UnlockDiagnosticVMOptions")) {
                        // approximate UnlockDiagnosticVMOptions as UnlockExperimentalVMOptions
                        handler.setExperimental(true);
                    } else if (optionString.equals("-XX:-UnlockExperimentalVMOptions") ||
                                    optionString.equals("-XX:-UnlockDiagnosticVMOptions")) {
                        handler.setExperimental(false);
                    } else if (optionString.startsWith("--vm.")) {
                        handler.handleVMOption(optionString);
                    } else if (optionString.startsWith("-Xcomp")) {
                        builder.option("engine.CompileImmediately", "true");
                    } else if (optionString.startsWith("-Xbatch")) {
                        builder.option("engine.BackgroundCompilation", "false");
                        builder.option("engine.CompileImmediately", "true");
                    } else if (optionString.startsWith("-Xint") || optionString.equals("-XX:TieredStopAtLevel=0")) {
                        builder.option("engine.Compilation", "false");
                    } else if (optionString.startsWith("-XX:")) {
                        handler.handleXXArg(optionString);
                    } else if (optionString.startsWith("--help:")) {
                        handler.help(optionString);
                    } else if (isExperimentalFlag(optionString)) {
                        // skip: previously handled
                    } else if (optionString.equals("--polyglot")) {
                        // skip: handled by mokapot
                    } else if (optionString.equals("--native")) {
                        // skip: silently succeed.
                    } else if (optionString.equals("--jvm")) {
                        throw abort("Unsupported flag: '--jvm' mode is not supported with this launcher.");
                    } else {
                        handler.parsePolyglotOption(optionString);
                    }
                }
            } catch (ArgumentException e) {
                if (!ignoreUnrecognized) {
                    // Failed to parse
                    warn(e.getMessage());
                    return JNI_ERR();
                }
            }
        }

        for (String xOption : xOptions) {
            var opt = xOption;
            if (autoAdjustHeapSize) {
                opt = maybeAdjustMaxHeapSize(xOption);
            }
            RuntimeOptions.set(opt.substring(2 /* drop the -X */), null);
        }

        if (bootClasspathPrepend != null) {
            builder.option("java.BootClasspathPrepend", bootClasspathPrepend);
        }
        if (bootClasspathAppend != null) {
            builder.option("java.BootClasspathAppend", bootClasspathAppend);
        }

        if (classpath != null) {
            builder.option("java.Classpath", classpath);
        }

        for (int i = 0; i < jvmArgs.size(); i++) {
            builder.option("java.VMArguments." + i, jvmArgs.get(i));
        }

        handler.argumentProcessingDone();
        return JNIErrors.JNI_OK();
    }

    private static String maybeAdjustMaxHeapSize(String optionString) {
        // (Jun 2024) Espresso uses more memory than HotSpot does, so if the user has set a very
        // small heap size that would work on HotSpot then we have to bump it up. 64mb is too small
        // to run Gradle's wrapper program which is required to use Espresso with Gradle, so, we
        // go to the next power of two beyond that. This number can be reduced in future when
        // memory efficiency is better.
        if (!optionString.startsWith("-Xmx")) {
            return optionString;
        }
        long maxHeapSizeBytes = parseLong(optionString.substring(4));
        final int floorMB = 128;
        if (maxHeapSizeBytes < floorMB * 1024 * 1024) {
            return "-Xmx" + floorMB + "m";
        } else {
            return optionString;
        }
    }

    private static long parseLong(String v) {
        String valueString = v.trim().toLowerCase(Locale.ROOT);
        long scale = 1;
        if (valueString.endsWith("k")) {
            scale = 1024L;
        } else if (valueString.endsWith("m")) {
            scale = 1024L * 1024L;
        } else if (valueString.endsWith("g")) {
            scale = 1024L * 1024L * 1024L;
        } else if (valueString.endsWith("t")) {
            scale = 1024L * 1024L * 1024L * 1024L;
        }

        if (scale != 1) {
            /* Remove trailing scale character. */
            valueString = valueString.substring(0, valueString.length() - 1);
        }

        return Long.parseLong(valueString) * scale;
    }

    private static void buildJvmArg(List<String> jvmArgs, String optionString) {
        for (String ignored : ignoredJvmArgs) {
            if (optionString.startsWith(ignored)) {
                return;
            }
        }
        jvmArgs.add(optionString);
    }

    private static boolean isExperimentalFlag(String optionString) {
        // return false for "--experimental-options=[garbage]
        return optionString.equals("--experimental-options") ||
                        optionString.equals("--experimental-options=true") ||
                        optionString.equals("--experimental-options=false") ||
                        optionString.equals("-XX:+UnlockDiagnosticVMOptions") ||
                        optionString.equals("-XX:-UnlockDiagnosticVMOptions");
    }

    private static boolean isXOption(String optionString) {
        return optionString.startsWith("-Xms") || optionString.startsWith("-Xmx") || optionString.startsWith("-Xmn") || optionString.startsWith("-Xss");
    }

    private static String appendPath(String paths, String toAppend) {
        if (paths != null && paths.length() != 0) {
            return toAppend != null && toAppend.length() != 0 ? paths + File.pathSeparator + toAppend : paths;
        } else {
            return toAppend;
        }
    }

    private static String prependPath(String toPrepend, String paths) {
        if (paths != null && paths.length() != 0) {
            return toPrepend != null && toPrepend.length() != 0 ? toPrepend + File.pathSeparator + paths : paths;
        } else {
            return toPrepend;
        }
    }

    private static String[] splitEquals(String value) {
        int eqIdx = value.indexOf('=');
        String k;
        String v;
        if (eqIdx >= 0) {
            k = value.substring(0, eqIdx);
            v = value.substring(eqIdx + 1);
        } else {
            k = value;
            v = "";
        }
        return new String[]{k, v};
    }

    public static class ArgumentException extends RuntimeException {
        private static final long serialVersionUID = 5430103471994299046L;

        private final boolean isExperimental;

        ArgumentException(String message, boolean isExperimental) {
            super(message);
            this.isExperimental = isExperimental;
        }

        public boolean isExperimental() {
            return isExperimental;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    public static ArgumentException abort(String message) {
        throw new Arguments.ArgumentException(message, false);
    }

    public static ArgumentException abortExperimental(String message) {
        throw new Arguments.ArgumentException(message, true);
    }

    public static void warn(String message) {
        STDERR.println(message);
    }
}
