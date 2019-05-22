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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Queue;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.graalvm.compiler.options.OptionType;

import com.oracle.svm.driver.MacroOption.MacroOptionKind;
import com.oracle.svm.core.util.ClasspathUtils;

class DefaultOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    private static final String verboseOption = "--verbose";

    static final String helpText = NativeImage.getResource("/Help.txt");
    static final String helpExtraText = NativeImage.getResource("/HelpExtra.txt");

    DefaultOptionHandler(NativeImage nativeImage) {
        super(nativeImage);
    }

    boolean useDebugAttach = false;

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
                String message = "GraalVM Version " + NativeImage.graalvmVersion;
                if (!NativeImage.graalvmConfig.isEmpty()) {
                    message += " " + NativeImage.graalvmConfig;
                }
                nativeImage.showMessage(message);
                System.exit(0);
                return true;
            case "--help-extra":
                args.poll();
                nativeImage.showMessage(helpExtraText);
                nativeImage.optionRegistry.showOptions(MacroOptionKind.Macro, true, nativeImage::showMessage);
                nativeImage.showNewline();
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
                for (String cp : cpArgs.split(File.pathSeparator, Integer.MAX_VALUE)) {
                    /* Conform to `java` command empty cp entry handling. */
                    String cpEntry = cp.isEmpty() ? "." : cp;
                    nativeImage.addCustomImageClasspath(cpEntry);
                }
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
                    NativeImage.showError("-jar requires jar file specification");
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
                nativeImage.setQueryOption(OptionType.User.name());
                return true;
            case "--expert-options-all":
                args.poll();
                nativeImage.setQueryOption("");
                return true;
        }

        String debugAttach = "--debug-attach";
        if (headArg.startsWith(debugAttach)) {
            if (useDebugAttach) {
                throw NativeImage.showError("The " + debugAttach + " option can only be used once.");
            }
            useDebugAttach = true;
            String debugAttachArg = args.poll();
            String portSuffix = debugAttachArg.substring(debugAttach.length());
            int debugPort = 8000;
            if (!portSuffix.isEmpty()) {
                try {
                    debugPort = Integer.parseInt(portSuffix.substring(1));
                } catch (NumberFormatException e) {
                    NativeImage.showError("Invalid " + debugAttach + " option: " + debugAttachArg);
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
        return false;
    }

    private void handleJarFileArg(Path filePath) {
        try (JarFile jarFile = new JarFile(filePath.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                NativeImage.showError("No manifest in " + filePath);
            }
            Attributes mainAttributes = manifest.getMainAttributes();
            String mainClass = mainAttributes.getValue("Main-Class");
            if (mainClass == null) {
                NativeImage.showError("No main manifest attribute, in " + filePath);
            }
            nativeImage.addPlainImageBuilderArg(nativeImage.oHClass + mainClass);
            String jarFileName = filePath.getFileName().toString();
            String jarSuffix = ".jar";
            String jarFileNameBase;
            if (jarFileName.endsWith(jarSuffix)) {
                jarFileNameBase = jarFileName.substring(0, jarFileName.length() - jarSuffix.length());
            } else {
                jarFileNameBase = jarFileName;
            }
            if (!jarFileNameBase.isEmpty()) {
                nativeImage.addPlainImageBuilderArg(nativeImage.oHName + jarFileNameBase);
            }
            String classPath = mainAttributes.getValue("Class-Path");
            /* Missing Class-Path Attribute is tolerable */
            if (classPath != null) {
                for (String cp : classPath.split(" +")) {
                    Path manifestClassPath = ClasspathUtils.stringToClasspath(cp);
                    if (!manifestClassPath.isAbsolute()) {
                        /* Resolve relative manifestClassPath against directory containing jar */
                        manifestClassPath = filePath.getParent().resolve(manifestClassPath);
                    }
                    nativeImage.addImageProvidedClasspath(manifestClassPath);
                }
            }
            nativeImage.addCustomImageClasspath(filePath);
        } catch (NativeImage.NativeImageError ex) {
            throw ex;
        } catch (Throwable ex) {
            throw NativeImage.showError("Invalid or corrupt jarfile " + filePath);
        }
    }

    @Override
    void addFallbackBuildArgs(List<String> buildArgs) {
        if (nativeImage.isVerbose()) {
            buildArgs.add(verboseOption);
        }
    }
}
