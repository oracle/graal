/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Queue;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.graalvm.compiler.options.OptionType;

import com.oracle.svm.hosted.image.AbstractBootImage.NativeImageKind;

class DefaultOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    static final String helpText = NativeImage.getResource("/Help.txt");
    static final String helpExtraText = NativeImage.getResource("/HelpExtra.txt");

    DefaultOptionHandler(NativeImage nativeImage) {
        super(nativeImage);
    }

    @Override
    public boolean consume(Queue<String> args) {
        String headArg = args.peek();
        switch (headArg) {
            case "--help":
                args.poll();
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
                nativeImage.showMessage("SubstrateVM Version Info");
                nativeImage.showMessage(NativeImage.svmVersion.replace(',', '\n'));
                nativeImage.showMessage("GraalVM Version " + NativeImage.graalvmVersion);
                System.exit(0);
                return true;
            case "--help-extra":
                args.poll();
                nativeImage.showMessage(helpExtraText);
                System.exit(0);
                return true;
            case "-cp":
            case "-classpath":
            case "--class-path":
                args.poll();
                String cpArgs = args.poll();
                if (cpArgs == null) {
                    NativeImage.showError(headArg + " requires class path specification");
                }
                for (String cp : cpArgs.split(File.pathSeparator)) {
                    nativeImage.addCustomImageClasspath(Paths.get(cp));
                }
                return true;
            case "--configurations-path":
                args.poll();
                String configPath = args.poll();
                if (configPath == null) {
                    NativeImage.showError(headArg + " requires a " + File.pathSeparator + " separated list of directories");
                }
                for (String configDir : configPath.split(File.pathSeparator)) {
                    nativeImage.addMacroOptionRoot(Paths.get(configDir));
                }
                return true;
            case "-jar":
                args.poll();
                String jarFilePathStr = args.poll();
                if (jarFilePathStr == null) {
                    NativeImage.showError("-jar requires jar file specification");
                }
                handleJarFileArg(nativeImage.canonicalize(Paths.get(jarFilePathStr)).toFile());
                return true;
            case "--verbose":
                args.poll();
                nativeImage.setVerbose(true);
                return true;
            case "--dry-run":
                args.poll();
                nativeImage.setDryRun(true);
                return true;
            case "-shared":
            case "--shared":
                args.poll();
                nativeImage.addImageBuilderArg(NativeImage.oHKind + NativeImageKind.SHARED_LIBRARY.name());
                return true;
            case "-ea":
                args.poll();
                nativeImage.addImageBuilderArg(NativeImage.oH + NativeImage.enableRuntimeAssertions);
                return true;
            case "-g":
                args.poll();
                nativeImage.addImageBuilderArg(NativeImage.oHDebug + 2);
                return true;
            case "--expert-options":
                args.poll();
                String expertUserOption = OptionType.User.name();
                nativeImage.addImageBuilderArg(NativeImage.oH + NativeImage.enablePrintFlags + expertUserOption);
                nativeImage.addImageBuilderArg(NativeImage.oR + NativeImage.enablePrintFlags + expertUserOption);
                return true;
            case "--expert-options-all":
                args.poll();
                nativeImage.addImageBuilderArg(NativeImage.oH + NativeImage.enablePrintFlags);
                nativeImage.addImageBuilderArg(NativeImage.oR + NativeImage.enablePrintFlags);
                return true;
        }

        String debugAttach = "--debug-attach";
        if (headArg.startsWith(debugAttach)) {
            String debugAttachArg = args.poll();
            String portSuffix = debugAttachArg.substring(debugAttach.length());
            int debugPort = 8000;
            if (!portSuffix.isEmpty()) {
                try {
                    debugPort = Integer.parseInt(portSuffix.substring(1));
                } catch (NumberFormatException e) {
                    NativeImage.showError("Invalid --debug-attach option: " + debugAttachArg);
                }
            }
            nativeImage.addImageBuilderJavaArgs("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,address=" + debugPort + ",suspend=y");
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
                nativeImage.addImageBuilderArg(NativeImage.oHOptimize + headArg.substring(2));
            }
            return true;
        }
        return false;
    }

    private void handleJarFileArg(File file) {
        try {
            Manifest manifest = null;
            for (FastJar.Entry entry : FastJar.list(file)) {
                if ("META-INF/MANIFEST.MF".equals(entry.name)) {
                    manifest = new Manifest(FastJar.getInputStream(file, entry));
                }
            }
            if (manifest == null) {
                return;
            }
            Attributes mainAttributes = manifest.getMainAttributes();
            String mainClass = mainAttributes.getValue("Main-Class");
            if (mainClass == null) {
                return;
            }
            nativeImage.addImageBuilderArg(NativeImage.oHClass + mainClass);
            String jarFileName = file.getName();
            String jarFileNameBase = jarFileName.substring(0, jarFileName.length() - 4);
            nativeImage.addImageBuilderArg(NativeImage.oHName + jarFileNameBase);
            Path filePath = file.toPath();
            nativeImage.addImageClasspath(filePath);
            String classPath = mainAttributes.getValue("Class-Path");
            /* Missing Class-Path Attribute is tolerable */
            if (classPath != null) {
                for (String cp : classPath.split(" +")) {
                    Path manifestClassPath = Paths.get(cp);
                    if (!manifestClassPath.isAbsolute()) {
                        /* Resolve relative manifestClassPath against directory containing jar */
                        manifestClassPath = filePath.getParent().resolve(manifestClassPath);
                    }
                    nativeImage.addImageClasspath(manifestClassPath);
                }
            }
        } catch (IOException e) {
            NativeImage.showError("Given file does not appear to be a jar-file: " + file, e);
        }
    }
}
