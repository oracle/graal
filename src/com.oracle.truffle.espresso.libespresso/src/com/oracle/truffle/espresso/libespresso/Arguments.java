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
package com.oracle.truffle.espresso.libespresso;

import static com.oracle.truffle.espresso.libespresso.jniapi.JNIErrors.JNI_ERR;

import java.io.File;
import java.io.PrintStream;

import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.polyglot.Context;
import org.graalvm.word.Pointer;

import com.oracle.truffle.espresso.libespresso.jniapi.JNIErrors;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVMInitArgs;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVMOption;

public final class Arguments {
    private static final PrintStream STDERR = System.err;

    private Arguments() {
    }

    private static class ModulePropertyCounter {
        public ModulePropertyCounter(Context.Builder builder) {
            this.builder = builder;
        }

        private static final String ADD_MODULES = "jdk.module.addmods";
        private static final String ADD_EXPORTS = "jdk.module.addexports";
        private static final String ADD_OPENS = "jdk.module.addopens";
        private static final String ADD_READS = "jdk.module.addreads";

        private final Context.Builder builder;

        private int addModules = 0;
        private int addExports = 0;
        private int addOpens = 0;
        private int addReads = 0;

        void addModules(String value) {
            addNumbered(ADD_MODULES, value, addModules++);
        }

        void addExports(String value) {
            addNumbered(ADD_EXPORTS, value, addExports++);
        }

        void addOpens(String value) {
            addNumbered(ADD_OPENS, value, addOpens++);
        }

        void addReads(String value) {
            addNumbered(ADD_READS, value, addReads++);
        }

        void addNumbered(String prop, String value, int count) {
            builder.option(prop + "." + count, value);
        }
    }

    public static int setupContext(Context.Builder builder, JNIJavaVMInitArgs args) {
        Pointer p = (Pointer) args.getOptions();
        int count = args.getNOptions();
        String classpath = null;
        String bootClasspathPrepend = null;
        String bootClasspathAppend = null;
        ModulePropertyCounter modulePropHandler = new ModulePropertyCounter(builder);
        for (int i = 0; i < count; i++) {
            JNIJavaVMOption option = (JNIJavaVMOption) p.add(i * SizeOf.get(JNIJavaVMOption.class));
            CCharPointer str = option.getOptionString();
            if (str.isNonNull()) {
                String optionString = CTypeConversion.toJavaString(option.getOptionString());
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
                } else if (optionString.startsWith("-D")) {
                    String key = optionString.substring("-D".length());
                    int splitAt = key.indexOf("=");
                    String value = "";
                    if (splitAt >= 0) {
                        value = key.substring(splitAt + 1);
                        key = key.substring(0, splitAt);
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
                    builder.option("java.Properties." + key, value);
                } else if (optionString.equals("-ea") || optionString.equals("-enableassertions")) {
                    builder.option("java.EnableAssertions", "true");
                } else if (optionString.equals("-esa") || optionString.equals("-enablesystemassertions")) {
                    builder.option("java.EnableSystemAssertions", "true");
                } else if (optionString.startsWith("--add-reads=")) {
                    modulePropHandler.addReads(optionString.substring("--add-reads=".length()));
                } else if (optionString.startsWith("--add-exports=")) {
                    modulePropHandler.addExports(optionString.substring("--add-exports=".length()));
                } else if (optionString.startsWith("--add-opens=")) {
                    modulePropHandler.addOpens(optionString.substring("--add-opens=".length()));
                } else if (optionString.startsWith("--add-modules=")) {
                    modulePropHandler.addModules(optionString.substring("--add-modules=".length()));
                } else if (optionString.startsWith("--module-path=")) {
                    builder.option("jdk.module.path", optionString.substring("--module-path".length()));
                } else if (optionString.startsWith("--upgrade-module-path=")) {
                    builder.option("jdk.module.upgrade.path", optionString.substring("--upgrade-module-path=".length()));
                } else if (optionString.startsWith("--limit-modules=")) {
                    builder.option("jdk.module.limitmods", optionString.substring("--limit-modules=".length()));
                } else if (isXOption(optionString)) {
                    RuntimeOptions.set(optionString.substring("-X".length()), null);
                } else if (optionString.equals("--polyglot")) {
                    // skip: handled by mokapot
                } else {
                    // TODO XX: and X options
                    STDERR.printf("Unrecognized option: %s%n", optionString);
                    return JNI_ERR();
                }
            }
        }

        if (bootClasspathPrepend != null) {
            builder.option("java.BootClasspathPrepend", bootClasspathPrepend);
        }
        if (bootClasspathAppend != null) {
            builder.option("java.BootClasspathAppend", bootClasspathAppend);
        }

        // classpath provenance order:
        // (1) the java.class.path property
        if (classpath == null) {
            // (2) the environment variable CLASSPATH
            classpath = System.getenv("CLASSPATH");
            if (classpath == null) {
                // (3) the current working directory only
                classpath = ".";
            }
        }

        builder.option("java.Classpath", classpath);
        return JNIErrors.JNI_OK();
    }

    public static boolean isXOption(String optionString) {
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
}
