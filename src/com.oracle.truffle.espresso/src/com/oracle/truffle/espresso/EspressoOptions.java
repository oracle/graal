/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso;

import java.util.Collections;
import java.util.Map;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionMap;
import org.graalvm.options.OptionType;

import com.oracle.truffle.api.Option;

@Option.Group(EspressoLanguage.ID)
public final class EspressoOptions {

    @Option(help = "User-defined system properties.", category = OptionCategory.USER) //
    public static final OptionKey<OptionMap<String>> Properties = OptionKey.mapOf(String.class);

    // Injecting java.io.File.pathSeparator is a hack for OptionProcessor.
    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories, JAR files, and ZIP archives to search for boot class files. These are used in place of the boot class files included in the JDK.", category = OptionCategory.USER) //
    public static final OptionKey<String> BootClasspath = new OptionKey<>("");

    // Injecting java.io.File.pathSeparator is a hack for OptionProcessor.
    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories, JAR files, and ZIP archives to append to the front of the default bootstrap class path.", category = OptionCategory.USER) //
    public static final OptionKey<String> BootClasspathAppend = new OptionKey<>("");

    // Injecting java.io.File.pathSeparator is a hack for OptionProcessor.
    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories, JAR files, and ZIP archives to prepend to the front of the default bootstrap class path.", category = OptionCategory.USER) //
    public static final OptionKey<String> BootClasspathPrepend = new OptionKey<>("");

    // Injecting java.io.File.pathSeparator is a hack for OptionProcessor.
    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories, JAR archives, and ZIP archives to search for class files.", category = OptionCategory.USER) //
    public static final OptionKey<String> Classpath = new OptionKey<>("");

    @Option(help = "Use MethodHandle(s) instead of reflection to call substitutions.", category = OptionCategory.USER) //
    public static final OptionKey<Boolean> IntrinsicsViaMethodHandles = new OptionKey<>(false);

    @Option(help = "Installation directory for Java Runtime Environment (JRE)", category = OptionCategory.USER) //
    public static final OptionKey<String> JavaHome = new OptionKey<>("");

    @Option(help = "Enable assertions.", category = OptionCategory.USER) //
    public static final OptionKey<Boolean> EnableAssertions = new OptionKey<>(false);

    @Option(help = "Enable system assertions.", category = OptionCategory.USER) //
    public static final OptionKey<Boolean> EnableSystemAssertions = new OptionKey<>(false);

    // Threads are enabled by default.
    public static final boolean ENABLE_THREADS = (System.getProperty("espresso.EnableThreads") == null) || Boolean.getBoolean("espresso.EnableThreads");

    // Bytecode Verification is enabled by default.
    public static final boolean ENABLE_VERIFICATION = (System.getProperty("espresso.EnableVerify") == null) || Boolean.getBoolean("espresso.EnableVerify");

    public static final boolean RUNNING_ON_SVM = ImageInfo.inImageCode();

    public static final String INCEPTION_NAME = System.getProperty("espresso.inception.name", "#");
}
