/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.graalvm.compiler.options.OptionType;

import com.oracle.svm.core.VM;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.util.ExitStatus;
import com.oracle.svm.driver.NativeImage.ArgumentQueue;
import com.oracle.svm.util.LogUtils;

class CmdLineOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    private static final String HELP_TEXT = NativeImage.getResource("/Help.txt");
    private static final String HELP_EXTRA_TEXT = NativeImage.getResource("/HelpExtra.txt");

    static final String VERBOSE_OPTION = "--verbose";
    static final String DRY_RUN_OPTION = "--dry-run";
    static final String DEBUG_ATTACH_OPTION = "--debug-attach";
    /* Defunct legacy options that we have to accept to maintain backward compatibility */
    private static final String VERBOSE_SERVER_OPTION = "--verbose-server";
    private static final String SERVER_OPTION_PREFIX = "--server-";

    private static final String LAUNCHER_NAME = "native-image";

    boolean useDebugAttach = false;

    CmdLineOptionHandler(NativeImage nativeImage) {
        super(nativeImage);
    }

    @Override
    boolean consume(ArgumentQueue args) {
        String headArg = args.peek();
        boolean consumed = consume(args, headArg);
        if (consumed) {
            OptionOrigin origin = OptionOrigin.from(args.argumentOrigin);
            if (!origin.commandLineLike()) {
                String msg = String.format("Using '%s' provided by %s is only allowed on command line.", headArg, origin);
                throw NativeImage.showError(msg);
            }
        }
        return consumed;
    }

    private boolean consume(ArgumentQueue args, String headArg) {
        switch (headArg) {
            case "--help":
                nativeImage.showMessage(HELP_TEXT);
                nativeImage.showNewline();
                nativeImage.apiOptionHandler.printOptions(nativeImage::showMessage, false);
                nativeImage.showNewline();
                nativeImage.optionRegistry.showOptions(null, true, nativeImage::showMessage);
                nativeImage.showNewline();
                System.exit(ExitStatus.OK.getValue());
                return true;
            case "--version":
                printVersion();
                System.exit(ExitStatus.OK.getValue());
                return true;
            case "--help-extra":
                nativeImage.showMessage(HELP_EXTRA_TEXT);
                nativeImage.apiOptionHandler.printOptions(nativeImage::showMessage, true);
                nativeImage.showNewline();
                nativeImage.optionRegistry.showOptions(OptionUtils.MacroOptionKind.Macro, true, nativeImage::showMessage);
                nativeImage.showNewline();
                System.exit(ExitStatus.OK.getValue());
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
            case "--exclude-config":
                args.poll();
                String excludeJar = args.poll();
                if (excludeJar == null) {
                    NativeImage.showError(headArg + " requires two arguments: a jar regular expression and a resource regular expression");
                }
                String excludeConfig = args.poll();
                if (excludeConfig == null) {
                    NativeImage.showError(headArg + " requires resource regular expression");
                }
                nativeImage.addExcludeConfig(Pattern.compile(excludeJar), Pattern.compile(excludeConfig));
                return true;
            case VERBOSE_OPTION:
                args.poll();
                nativeImage.addVerbose();
                return true;
            case DRY_RUN_OPTION:
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
            case VERBOSE_SERVER_OPTION:
                args.poll();
                LogUtils.warning("Ignoring server-mode native-image argument " + headArg + ".");
                return true;
        }

        if (headArg.startsWith(BundleSupport.BUNDLE_OPTION)) {
            nativeImage.bundleSupport = BundleSupport.create(nativeImage, args.poll(), args);
            return true;
        }

        if (headArg.startsWith(DEBUG_ATTACH_OPTION)) {
            if (useDebugAttach) {
                throw NativeImage.showError("The " + DEBUG_ATTACH_OPTION + " option can only be used once.");
            }
            useDebugAttach = true;
            String debugAttachArg = args.poll();
            String addressSuffix = debugAttachArg.substring(DEBUG_ATTACH_OPTION.length());
            String address = addressSuffix.isEmpty() ? "8000" : addressSuffix.substring(1);
            /* Using agentlib to allow interoperability with other agents */
            nativeImage.addImageBuilderJavaArgs("-agentlib:jdwp=transport=dt_socket,server=y,address=" + address + ",suspend=y");
            /* Disable watchdog mechanism */
            nativeImage.addPlainImageBuilderArg(nativeImage.oHDeadlockWatchdogInterval + "0");
            return true;
        }

        if (headArg.startsWith(SERVER_OPTION_PREFIX)) {
            args.poll();
            LogUtils.warning("Ignoring server-mode native-image argument " + headArg + ".");
            String serverOptionCommand = headArg.substring(SERVER_OPTION_PREFIX.length());
            if (!serverOptionCommand.startsWith("session=")) {
                /*
                 * All but the --server-session=... option used to exit(0). We want to simulate that
                 * behaviour for proper backward compatibility.
                 */
                System.exit(ExitStatus.OK.getValue());
            }
            return true;
        }

        return false;
    }

    /**
     * Prints version output following
     * "src/java.base/share/classes/java/lang/VersionProps.java.template#print(boolean)".
     */
    private void printVersion() {
        /* First line: platform version. */
        String javaVersion = System.getProperty("java.version");
        String javaVersionDate = System.getProperty("java.version.date");
        Optional<String> versionOpt = Runtime.version().optional();
        boolean isLTS = versionOpt.isPresent() && versionOpt.get().startsWith("LTS");
        nativeImage.showMessage("%s %s %s", LAUNCHER_NAME, javaVersion, javaVersionDate, isLTS ? " LTS" : "");

        /* Second line: runtime version (ie, libraries). */
        String javaRuntimeVersion = System.getProperty("java.runtime.version");

        String jdkDebugLevel = System.getProperty("jdk.debug", "release");
        if ("release".equals(jdkDebugLevel)) {
            /* Do not show debug level "release" builds */
            jdkDebugLevel = "";
        } else {
            jdkDebugLevel = jdkDebugLevel + " ";
        }

        String javaRuntimeName = System.getProperty("java.runtime.name");
        String vendorVersion = VM.getVendorVersion();
        vendorVersion = vendorVersion.isEmpty() ? "" : " " + vendorVersion;
        nativeImage.showMessage("%s%s (%sbuild %s)", javaRuntimeName, vendorVersion, jdkDebugLevel, javaRuntimeVersion);

        /* Third line: VM information. */
        String javaVMName = System.getProperty("java.vm.name");
        String javaVMVersion = System.getProperty("java.vm.version");
        String javaVMInfo = System.getProperty("java.vm.info");
        nativeImage.showMessage("%s%s (%sbuild %s, %s)", javaVMName, vendorVersion, jdkDebugLevel, javaVMVersion, javaVMInfo);
    }

    @Override
    void addFallbackBuildArgs(List<String> buildArgs) {
        if (nativeImage.isVerbose()) {
            buildArgs.add(VERBOSE_OPTION);
        }
    }
}
