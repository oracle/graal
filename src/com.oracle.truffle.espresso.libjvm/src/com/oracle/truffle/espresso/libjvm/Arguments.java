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
package com.oracle.truffle.espresso.libjvm;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.polyglot.Context;
import org.graalvm.word.Pointer;

import com.oracle.truffle.espresso.libjvm.nativeapi.JNIErrors;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIJavaVMInitArgs;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIJavaVMOption;

public final class Arguments {
    private Arguments() {
    }

    public static int setupContext(Context.Builder builder, JNIJavaVMInitArgs args) {
        Pointer p = (Pointer) args.getOptions();
        int count = args.getNOptions();
        String classpath = null;
        String bootClasspathPrepend = null;
        String bootClasspathAppend = null;
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
                    builder.option("java.BootClasspathAppend", appendPath(bootClasspathAppend, optionString.substring("-Xbootclasspath/a:".length())));
                } else if (optionString.startsWith("-Xbootclasspath/p:")) {
                    builder.option("java.BootClasspathPrepend", prependPath(optionString.substring("-Xbootclasspath/p:".length()), bootClasspathPrepend));
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
                } else {
                    // TODO XX: and X options
                    return JNIErrors.JNI_ERR();
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

        Path libJVMPath = Paths.get(ProcessProperties.getObjectFile(JNIInvocationInterface.CREATE_JAVA_VM_SYMBOL));
        // Get rid of /libjvm.so.
        Path libJVMDir = libJVMPath.getParent();
        // Get rid of /{client|server|truffle}.
        Path libDir = libJVMDir.getParent();
        builder.option("java.BootLibraryPath", libDir.toString());
        // Get rid of /lib.
        Path jreDir = libDir.getParent();
        builder.option("java.JavaHome", jreDir.toString());

        return JNIErrors.JNI_OK();
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
