/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.driver.NativeImage.ArgumentQueue;
import com.oracle.svm.hosted.driver.IncludeOptionsSupport;
import com.oracle.svm.hosted.driver.IncludeOptionsSupport.ExtendedOption;
import com.oracle.svm.hosted.driver.LayerOptionsSupport.LayerOption;
import com.oracle.svm.util.LogUtils;

class DefaultOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    private static final String requireValidJarFileMessage = "-jar requires a valid jarfile";
    private static final String newStyleClasspathOptionName = "--class-path";

    static final String addModulesOption = "--add-modules";
    static final String limitModulesOption = "--limit-modules";
    private static final String moduleSetModifierOptionErrorMessage = " requires modules to be specified";

    static final String ADD_ENV_VAR_OPTION = "-E";

    /* Defunct legacy options that we have to accept to maintain backward compatibility */
    private static final String noServerOption = "--no-server";

    DefaultOptionHandler(NativeImage nativeImage) {
        super(nativeImage);
    }

    @Override
    public boolean consume(ArgumentQueue args) {
        String headArg = args.peek();
        switch (headArg) {
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
            case "-p":
            case "--module-path":
                args.poll();
                String mpArgs = args.poll();
                if (mpArgs == null) {
                    NativeImage.showError(headArg + " requires module path specification");
                }
                processModulePathArgs(mpArgs);
                return true;
            case "-m":
            case "--module":
                args.poll();
                String mainClassModuleArg = args.poll();
                if (mainClassModuleArg == null) {
                    NativeImage.showError(headArg + " requires module name");
                }
                String[] mainClassModuleArgParts = mainClassModuleArg.split("/", 2);
                if (mainClassModuleArgParts.length > 1) {
                    nativeImage.addPlainImageBuilderArg(nativeImage.oHClass + mainClassModuleArgParts[1], OptionOrigin.originDriver);
                }
                nativeImage.addPlainImageBuilderArg(nativeImage.oHModule + mainClassModuleArgParts[0], OptionOrigin.originDriver);
                nativeImage.setModuleOptionMode(true);
                return true;
            case addModulesOption:
                args.poll();
                String addModulesArgs = args.poll();
                if (addModulesArgs == null) {
                    NativeImage.showError(headArg + moduleSetModifierOptionErrorMessage);
                }
                nativeImage.addAddedModules(addModulesArgs);
                return true;
            case limitModulesOption:
                args.poll();
                String limitModulesArgs = args.poll();
                if (limitModulesArgs == null) {
                    NativeImage.showError(headArg + moduleSetModifierOptionErrorMessage);
                }
                nativeImage.addLimitedModules(limitModulesArgs);
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
            case "--diagnostics-mode":
                args.poll();
                nativeImage.enableDiagnostics();
                nativeImage.addPlainImageBuilderArg("-H:+DiagnosticsMode");
                nativeImage.addPlainImageBuilderArg("-H:DiagnosticsDir=" + nativeImage.diagnosticsDir);
                System.out.println("# Diagnostics mode enabled: image-build reports are saved to " + nativeImage.diagnosticsDir);
                return true;
            case noServerOption:
                args.poll();
                LogUtils.warning("Ignoring server-mode native-image argument " + headArg + ".");
                return true;
            case "--enable-preview":
                args.poll();
                nativeImage.addCustomJavaArgs("--enable-preview");
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
        if (headArg.startsWith(nativeImage.oHLayerCreate)) {
            String rawLayerCreateValue = headArg.substring(nativeImage.oHLayerCreate.length());
            if (!rawLayerCreateValue.isEmpty()) {
                List<String> layerCreateValue = OptionUtils.resolveOptionValuesRedirection(SubstrateOptions.LayerCreate, rawLayerCreateValue, OptionOrigin.from(args.argumentOrigin));
                LayerOption layerOption = LayerOption.parse(layerCreateValue);
                for (ExtendedOption option : layerOption.extendedOptions()) {
                    var packageOptionValue = IncludeOptionsSupport.PackageOptionValue.from(option);
                    if (packageOptionValue == null) {
                        continue;
                    }
                    String packageName = packageOptionValue.name();
                    if (packageOptionValue.isWildcard()) {
                        nativeImage.systemPackagesToModules.forEach((systemPackageName, moduleName) -> {
                            if (systemPackageName.startsWith(packageName)) {
                                nativeImage.addAddedModules(moduleName);
                            }
                        });
                    } else {
                        String moduleName = nativeImage.systemPackagesToModules.get(packageName);
                        if (moduleName != null) {
                            nativeImage.addAddedModules(moduleName);
                        }
                    }
                }
            }
        }
        if (headArg.startsWith(NativeImage.oH)) {
            args.poll();
            nativeImage.addPlainImageBuilderArg(headArg, args.argumentOrigin);
            return true;
        }
        if (headArg.startsWith(NativeImage.oR)) {
            args.poll();
            nativeImage.addPlainImageBuilderArg(headArg);
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
        if (headArg.startsWith(ADD_ENV_VAR_OPTION)) {
            args.poll();
            String envVarSetting = headArg.substring(ADD_ENV_VAR_OPTION.length());
            String[] keyValue = envVarSetting.split("=", 2);
            String valueDefinedOrInherited = keyValue.length > 1 ? keyValue[1] : null;
            nativeImage.imageBuilderEnvironment.put(keyValue[0], valueDefinedOrInherited);
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
        if (headArg.startsWith(addModulesOption + "=")) {
            args.poll();
            String addModulesArgs = headArg.substring(addModulesOption.length() + 1);
            if (addModulesArgs.isEmpty()) {
                NativeImage.showError(headArg + moduleSetModifierOptionErrorMessage);
            }
            nativeImage.addAddedModules(addModulesArgs);
            return true;
        }
        if (headArg.startsWith(limitModulesOption + "=")) {
            args.poll();
            String limitModulesArgs = headArg.substring(limitModulesOption.length() + 1);
            if (limitModulesArgs.isEmpty()) {
                NativeImage.showError(headArg + moduleSetModifierOptionErrorMessage);
            }
            nativeImage.addLimitedModules(limitModulesArgs);
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

    private void processModulePathArgs(String mpArgs) {
        for (String mpEntry : mpArgs.split(File.pathSeparator, Integer.MAX_VALUE)) {
            nativeImage.addImageModulePath(Paths.get(mpEntry), false, true);
        }
    }

    private void handleJarFileArg(Path jarFilePath) {
        if (Files.isDirectory(jarFilePath)) {
            NativeImage.showError(jarFilePath + " is a directory. (" + requireValidJarFileMessage + ")");
        }
        String jarFileName = jarFilePath.getFileName().toString();
        String jarSuffix = ".jar";
        String jarFileNameBase;
        if (jarFileName.endsWith(jarSuffix)) {
            jarFileNameBase = jarFileName.substring(0, jarFileName.length() - jarSuffix.length());
        } else {
            jarFileNameBase = jarFileName;
        }
        if (!jarFileNameBase.isEmpty()) {
            String origin = "manifest from " + jarFilePath.toUri();
            nativeImage.addPlainImageBuilderArg(nativeImage.oHName + jarFileNameBase, origin, false);
        }
        Path finalFilePath = nativeImage.useBundle() ? nativeImage.bundleSupport.substituteClassPath(jarFilePath) : jarFilePath;
        if (!NativeImage.processJarManifestMainAttributes(finalFilePath, nativeImage::handleManifestFileAttributes)) {
            NativeImage.showError("No manifest in " + finalFilePath);
        }
        nativeImage.addCustomImageClasspath(finalFilePath);
    }
}
