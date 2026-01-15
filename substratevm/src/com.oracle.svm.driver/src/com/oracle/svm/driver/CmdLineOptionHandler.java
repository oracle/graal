/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.util.ExitStatus;
import com.oracle.svm.driver.NativeImage.ArgumentQueue;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.options.OptionType;

class CmdLineOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    static final String VERBOSE_OPTION = "--verbose";
    static final String DRY_RUN_OPTION = "--dry-run";
    static final String DEBUG_ATTACH_OPTION = "--debug-attach";
    /* Defunct legacy options that we have to accept to maintain backward compatibility */
    private static final String VERBOSE_SERVER_OPTION = "--verbose-server";
    private static final String SERVER_OPTION_PREFIX = "--server-";

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
                handleExcludeConfigOption(headArg, args);
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
            // warning should be early, before any other output of the feature
            if (nativeImage.bundleSupport == null) {
                LogUtils.warning("Native Image Bundles are an experimental feature.");
            }
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
            nativeImage.addPlainImageBuilderArg(nativeImage.oHDeadlockWatchdogInterval + "0", OptionOrigin.originDriver);
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

    private void handleExcludeConfigOption(String headArg, ArgumentQueue args) {
        String excludeJar = args.poll();
        if (excludeJar == null) {
            NativeImage.showError(headArg + " requires two arguments: a jar regular expression and a resource regular expression");
        }
        Pattern jarPattern;
        try {
            jarPattern = Pattern.compile(excludeJar);
        } catch (final PatternSyntaxException pse) {
            throw NativeImage.showError(headArg + " was used with an invalid jar regular expression: %s", pse);
        }
        String excludeConfig = args.poll();
        if (excludeConfig == null) {
            NativeImage.showError(headArg + " requires resource regular expression");
        }
        Pattern excludeConfigPattern;
        try {
            excludeConfigPattern = Pattern.compile(excludeConfig);
        } catch (final PatternSyntaxException pse) {
            throw NativeImage.showError(headArg + " was used with an invalid resource regular expression: %s", pse);
        }
        nativeImage.addExcludeConfig(jarPattern, excludeConfigPattern);
    }
}
