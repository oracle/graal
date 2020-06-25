/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Queue;

import org.graalvm.compiler.options.OptionType;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.driver.MacroOption.MacroOptionKind;

class DefaultOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    private static final String verboseOption = "--verbose";
    private static final String requireValidJarFileMessage = "-jar requires a valid jarfile";
    private static final String newStyleClasspathOptionName = "--class-path";

    static final String helpText = NativeImage.getResource("/Help.txt");
    static final String helpExtraText = NativeImage.getResource("/HelpExtra.txt");
    static final String noServerOption = SubstrateOptions.NO_SERVER;
    static final String verboseServerOption = "--verbose-server";
    static final String serverOptionPrefix = "--server-";

    DefaultOptionHandler(NativeImage nativeImage) {
        super(nativeImage);
    }

    boolean useDebugAttach = false;

    private static void singleArgumentCheck(Queue<String> args, String arg) {
        if (!args.isEmpty()) {
            NativeImage.showError("Option " + arg + " cannot be combined with other options.");
        }
    }

    @Override
    public boolean consume(Queue<String> args) {
        String headArg = args.peek();
        switch (headArg) {
            case "--help":
                args.poll();
                singleArgumentCheck(args, headArg);
                nativeImage.showMessage(helpText);
                nativeImage.showNewline();
                nativeImage.apiOptionHandler.printOptions(nativeImage::showMessage);
                nativeImage.showNewline();
                nativeImage.optionRegistry.showOptions(null, true, nativeImage::showMessage);
                nativeImage.showNewline();
                System.exit(0);
                return true;
            case "--version":
                args.poll();
                singleArgumentCheck(args, headArg);
                String message = "GraalVM Version " + NativeImage.graalvmVersion;
                if (!NativeImage.graalvmConfig.isEmpty()) {
                    message += " " + NativeImage.graalvmConfig;
                }
                message += " (Java Version " + System.getProperty("java.version") + ")";
                nativeImage.showMessage(message);
                System.exit(0);
                return true;
            case "--help-extra":
                args.poll();
                singleArgumentCheck(args, headArg);
                nativeImage.showMessage(helpExtraText);
                nativeImage.optionRegistry.showOptions(MacroOptionKind.Macro, true, nativeImage::showMessage);
                nativeImage.showNewline();
                System.exit(0);
                return true;
            case "-cp":
            case "-classpath":
            case newStyleClasspathOptionName:
                args.poll();
                String cpArgs = args.poll();
                if (cpArgs == null) {
                    NativeImage.showError(headArg + " requires class path specification");
                }
                processClasspathArgs(cpArgs);
                return true;
            case "--configurations-path":
                args.poll();
                String configPath = args.poll();
                if (configPath == null) {
                    NativeImage.showError(headArg + " requires a " + File.pathSeparator + " separated list of directories");
                }
                for (String configDir : configPath.split(File.pathSeparator)) {
                    nativeImage.addMacroOptionRoot(nativeImage.canonicalize(Paths.get(configDir)));
                }
                return true;
            case "-jar":
                args.poll();
                String jarFilePathStr = args.poll();
                if (jarFilePathStr == null) {
                    NativeImage.showError(requireValidJarFileMessage);
                }
                handleJarFileArg(nativeImage.canonicalize(Paths.get(jarFilePathStr)));
                nativeImage.setJarOptionMode(true);
                return true;
            case verboseOption:
                args.poll();
                nativeImage.setVerbose(true);
                return true;
            case "--dry-run":
                args.poll();
                nativeImage.setDryRun(true);
                return true;
            case "--expert-options":
                args.poll();
                nativeImage.setPrintFlagsOptionQuery(OptionType.User.name());
                return true;
            case "--expert-options-all":
                args.poll();
                nativeImage.setPrintFlagsOptionQuery("");
                return true;
            case "--expert-options-detail":
                args.poll();
                String optionNames = args.poll();
                nativeImage.setPrintFlagsWithExtraHelpOptionQuery(optionNames);
                return true;
            case noServerOption:
            case verboseServerOption:
                args.poll();
                NativeImage.showWarning("Ignoring server-mode native-image argument " + headArg + ".");
                return true;
        }

        String debugAttach = "--debug-attach";
        if (headArg.startsWith(debugAttach)) {
            if (useDebugAttach) {
                throw NativeImage.showError("The " + debugAttach + " option can only be used once.");
            }
            useDebugAttach = true;
            String debugAttachArg = args.poll();
            String addressSuffix = debugAttachArg.substring(debugAttach.length());
            String address = addressSuffix.isEmpty() ? "8000" : addressSuffix.substring(1);
            /* Using agentlib to allow interoperability with other agents */
            nativeImage.addImageBuilderJavaArgs("-agentlib:jdwp=transport=dt_socket,server=y,address=" + address + ",suspend=y");
            /* Disable watchdog mechanism */
            nativeImage.addPlainImageBuilderArg(nativeImage.oHDeadlockWatchdogInterval + "0");
            return true;
        }

        String singleArgClasspathPrefix = newStyleClasspathOptionName + "=";
        if (headArg.startsWith(singleArgClasspathPrefix)) {
            String cpArgs = args.poll().substring(singleArgClasspathPrefix.length());
            if (cpArgs.isEmpty()) {
                NativeImage.showError(headArg + " requires class path specification");
            }
            processClasspathArgs(cpArgs);
            return true;
        }
        if (headArg.startsWith(NativeImage.oH) || headArg.startsWith(NativeImage.oR)) {
            args.poll();
            nativeImage.addCustomImageBuilderArgs(headArg);
            return true;
        }
        String javaArgsPrefix = "-D";
        if (headArg.startsWith(javaArgsPrefix)) {
            args.poll();
            nativeImage.addCustomJavaArgs(headArg);
            return true;
        }
        String optionKeyPrefix = "-V";
        if (headArg.startsWith(optionKeyPrefix)) {
            args.poll();
            String keyValueStr = headArg.substring(optionKeyPrefix.length());
            String[] keyValue = keyValueStr.split("=");
            if (keyValue.length != 2) {
                throw NativeImage.showError("Use " + optionKeyPrefix + "<key>=<value>");
            }
            nativeImage.addOptionKeyValue(keyValue[0], keyValue[1]);
            return true;
        }
        if (headArg.startsWith("-J")) {
            args.poll();
            if (headArg.equals("-J")) {
                NativeImage.showError("The -J option should not be followed by a space");
            } else {
                nativeImage.addCustomJavaArgs(headArg.substring(2));
            }
            return true;
        }
        String optimizeOption = "-O";
        if (headArg.startsWith(optimizeOption)) {
            args.poll();
            if (headArg.equals(optimizeOption)) {
                NativeImage.showError("The " + optimizeOption + " option should not be followed by a space");
            } else {
                nativeImage.addPlainImageBuilderArg(nativeImage.oHOptimize + headArg.substring(2));
            }
            return true;
        }
        if (headArg.startsWith(serverOptionPrefix)) {
            args.poll();
            NativeImage.showWarning("Ignoring server-mode native-image argument " + headArg + ".");
            return true;
        }
        return false;
    }

    private void processClasspathArgs(String cpArgs) {
        for (String cp : cpArgs.split(File.pathSeparator, Integer.MAX_VALUE)) {
            /* Conform to `java` command empty cp entry handling. */
            String cpEntry = cp.isEmpty() ? "." : cp;
            nativeImage.addCustomImageClasspath(cpEntry);
        }
    }

    private void handleJarFileArg(Path filePath) {
        if (Files.isDirectory(filePath)) {
            NativeImage.showError(filePath + " is a directory. (" + requireValidJarFileMessage + ")");
        }
        if (!NativeImage.processJarManifestMainAttributes(filePath, nativeImage::handleMainClassAttribute)) {
            NativeImage.showError("No manifest in " + filePath);
        }
        nativeImage.addCustomImageClasspath(filePath);
    }

    @Override
    void addFallbackBuildArgs(List<String> buildArgs) {
        if (nativeImage.isVerbose()) {
            buildArgs.add(verboseOption);
        }
    }
}
