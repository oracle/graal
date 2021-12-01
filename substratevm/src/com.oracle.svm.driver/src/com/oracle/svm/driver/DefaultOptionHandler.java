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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.graalvm.compiler.options.OptionType;

import com.oracle.svm.driver.MacroOption.MacroOptionKind;
import com.oracle.svm.driver.NativeImage.ArgumentQueue;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.runtime.JVMCI;

class DefaultOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    private static final String verboseOption = "--verbose";
    private static final String requireValidJarFileMessage = "-jar requires a valid jarfile";
    private static final String newStyleClasspathOptionName = "--class-path";

    private static final String addModulesOption = "--add-modules";
    private static final String addModulesErrorMessage = " requires modules to be specified";

    static final String helpText = NativeImage.getResource("/Help.txt");
    static final String helpExtraText = NativeImage.getResource("/HelpExtra.txt");

    /* Defunct legacy options that we have to accept to maintain backward compatibility */
    static final String noServerOption = "--no-server";
    static final String verboseServerOption = "--verbose-server";
    static final String serverOptionPrefix = "--server-";

    DefaultOptionHandler(NativeImage nativeImage) {
        super(nativeImage);
    }

    boolean useDebugAttach = false;
    boolean disableAtFiles = false;

    private static void singleArgumentCheck(ArgumentQueue args, String arg) {
        if (!args.isEmpty()) {
            NativeImage.showError("Option " + arg + " cannot be combined with other options.");
        }
    }

    private static final String javaRuntimeVersion = System.getProperty("java.runtime.version");

    @Override
    public boolean consume(ArgumentQueue args) {
        String headArg = args.peek();
        switch (headArg) {
            case "--help":
                args.poll();
                singleArgumentCheck(args, headArg);
                nativeImage.showMessage(helpText);
                nativeImage.showNewline();
                nativeImage.apiOptionHandler.printOptions(nativeImage::showMessage, false);
                nativeImage.showNewline();
                nativeImage.optionRegistry.showOptions(null, true, nativeImage::showMessage);
                nativeImage.showNewline();
                System.exit(0);
                return true;
            case "--version":
                args.poll();
                singleArgumentCheck(args, headArg);
                String message;
                if (NativeImage.IS_AOT) {
                    message = System.getProperty("java.vm.version");
                } else {
                    message = "native-image " + NativeImage.graalvmVersion + " " + NativeImage.graalvmConfig;
                }
                message += " (Java Version " + javaRuntimeVersion + ")";
                nativeImage.showMessage(message);
                System.exit(0);
                return true;
            case "--help-extra":
                args.poll();
                singleArgumentCheck(args, headArg);
                nativeImage.showMessage(helpExtraText);
                nativeImage.apiOptionHandler.printOptions(nativeImage::showMessage, true);
                nativeImage.showNewline();
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
                    nativeImage.addPlainImageBuilderArg(nativeImage.oHClass + mainClassModuleArgParts[1]);
                }
                nativeImage.addPlainImageBuilderArg(nativeImage.oHModule + mainClassModuleArgParts[0]);
                nativeImage.setModuleOptionMode(true);
                return true;
            case addModulesOption:
                args.poll();
                String addModulesArgs = args.poll();
                if (addModulesArgs == null) {
                    NativeImage.showError(headArg + addModulesErrorMessage);
                }
                if (nativeImage.config.useJavaModules()) {
                    nativeImage.addImageBuilderJavaArgs(addModulesOption, addModulesArgs);
                    nativeImage.addAddedModules(addModulesArgs);
                } else {
                    NativeImage.showWarning("Ignoring unsupported module option: " + addModulesOption + " " + addModulesArgs);
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
            case "--diagnostics-mode":
                args.poll();
                nativeImage.setDiagnostics(true);
                nativeImage.addPlainImageBuilderArg("-H:+DiagnosticsMode");
                nativeImage.addPlainImageBuilderArg("-H:DiagnosticsDir=" + nativeImage.diagnosticsDir);
                System.out.println("# Diagnostics mode enabled: image-build reports are saved to " + nativeImage.diagnosticsDir);
                return true;
            case "--list-cpu-features":
                args.poll();
                Architecture arch = JVMCI.getRuntime().getHostJVMCIBackend().getTarget().arch;
                if (arch instanceof AMD64) {
                    nativeImage.showMessage("All AMD64 CPUFeatures: " + Arrays.toString(AMD64.CPUFeature.values()));
                    nativeImage.showNewline();
                    nativeImage.showMessage("Host machine AMD64 CPUFeatures: " + ((AMD64) arch).getFeatures().toString());
                } else {
                    nativeImage.showMessage("All AArch64 CPUFeatures: " + Arrays.toString(AArch64.CPUFeature.values()));
                    nativeImage.showNewline();
                    nativeImage.showMessage("Host machine AArch64 CPUFeatures: " + ((AArch64) arch).getFeatures().toString());
                }
                nativeImage.showNewline();
                System.exit(0);
                return true;
            case "--disable-@files":
                args.poll();
                disableAtFiles = true;
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
        if (headArg.startsWith(NativeImage.oH)) {
            args.poll();
            nativeImage.addCustomImageBuilderArgs(NativeImage.injectHostedOptionOrigin(headArg, args.argumentOrigin));
            return true;
        }
        if (headArg.startsWith(NativeImage.oR)) {
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
            String serverOptionCommand = headArg.substring(serverOptionPrefix.length());
            if (!serverOptionCommand.startsWith("session=")) {
                /*
                 * All but the --server-session=... option used to exit(0). We want to simulate that
                 * behaviour for proper backward compatibility.
                 */
                System.exit(0);
            }
            return true;
        }
        if (headArg.startsWith(addModulesOption + "=")) {
            args.poll();
            String addModulesArgs = headArg.substring(addModulesOption.length() + 1);
            if (addModulesArgs.isEmpty()) {
                NativeImage.showError(headArg + addModulesErrorMessage);
            }
            if (nativeImage.config.useJavaModules()) {
                nativeImage.addImageBuilderJavaArgs(addModulesOption, addModulesArgs);
                nativeImage.addAddedModules(addModulesArgs);
            } else {
                NativeImage.showWarning("Ignoring unsupported module option: " + addModulesOption + " " + addModulesArgs);
            }
            return true;
        }
        if (headArg.startsWith("@") && !disableAtFiles) {
            args.poll();
            headArg = headArg.substring(1);
            Path argFile = Paths.get(headArg);
            NativeImage.NativeImageArgsProcessor processor = nativeImage.new NativeImageArgsProcessor(argFile.toString());
            readArgFile(argFile).forEach(processor::accept);
            List<String> leftoverArgs = processor.apply(false);
            if (leftoverArgs.size() > 0) {
                NativeImage.showError("Found unrecognized options while parsing argument file '" + argFile + "':\n" + String.join("\n", leftoverArgs));
            }
            return true;
        }
        return false;
    }

    // Ported from JDK11's java.base/share/native/libjli/args.c
    enum PARSER_STATE {
        FIND_NEXT,
        IN_COMMENT,
        IN_QUOTE,
        IN_ESCAPE,
        SKIP_LEAD_WS,
        IN_TOKEN
    }

    class CTX_ARGS {
        PARSER_STATE state;
        int cptr;
        int eob;
        char quoteChar;
        List<String> parts;
        String options;
    }

    // Ported from JDK11's java.base/share/native/libjli/args.c
    private List<String> readArgFile(Path file) {
        List<String> arguments = new ArrayList<>();
        // Use of the at sign (@) to recursively interpret files isn't supported.
        arguments.add("--disable-@files");

        String options = null;
        try {
            options = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NativeImage.showError("Error reading argument file", e);
        }

        CTX_ARGS ctx = new CTX_ARGS();
        ctx.state = PARSER_STATE.FIND_NEXT;
        ctx.parts = new ArrayList<>(4);
        ctx.quoteChar = '"';
        ctx.cptr = 0;
        ctx.eob = options.length();
        ctx.options = options;

        String token = nextToken(ctx);
        while (token != null) {
            arguments.add(token);
            token = nextToken(ctx);
        }

        // remaining partial token
        if (ctx.state == PARSER_STATE.IN_TOKEN || ctx.state == PARSER_STATE.IN_QUOTE) {
            if (ctx.parts.size() != 0) {
                token = String.join("", ctx.parts);
                arguments.add(token);
            }
        }
        return arguments;
    }

    // Ported from JDK11's java.base/share/native/libjli/args.c
    @SuppressWarnings("fallthrough")
    private static String nextToken(CTX_ARGS ctx) {
        int nextc = ctx.cptr;
        int eob = ctx.eob;
        int anchor = nextc;
        String token;

        for (; nextc < eob; nextc++) {
            char ch = ctx.options.charAt(nextc);

            // Skip white space characters
            if (ctx.state == PARSER_STATE.FIND_NEXT || ctx.state == PARSER_STATE.SKIP_LEAD_WS) {
                while (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\f') {
                    nextc++;
                    if (nextc >= eob) {
                        return null;
                    }
                    ch = ctx.options.charAt(nextc);
                }
                ctx.state = (ctx.state == PARSER_STATE.FIND_NEXT) ? PARSER_STATE.IN_TOKEN : PARSER_STATE.IN_QUOTE;
                anchor = nextc;
                // Deal with escape sequences
            } else if (ctx.state == PARSER_STATE.IN_ESCAPE) {
                // concatenation directive
                if (ch == '\n' || ch == '\r') {
                    ctx.state = PARSER_STATE.SKIP_LEAD_WS;
                } else {
                    // escaped character
                    char[] escaped = new char[2];
                    escaped[1] = '\0';
                    switch (ch) {
                        case 'n':
                            escaped[0] = '\n';
                            break;
                        case 'r':
                            escaped[0] = '\r';
                            break;
                        case 't':
                            escaped[0] = '\t';
                            break;
                        case 'f':
                            escaped[0] = '\f';
                            break;
                        default:
                            escaped[0] = ch;
                            break;
                    }
                    ctx.parts.add(String.valueOf(escaped));
                    ctx.state = PARSER_STATE.IN_QUOTE;
                }
                // anchor to next character
                anchor = nextc + 1;
                continue;
                // ignore comment to EOL
            } else if (ctx.state == PARSER_STATE.IN_COMMENT) {
                while (ch != '\n' && ch != '\r') {
                    nextc++;
                    if (nextc >= eob) {
                        return null;
                    }
                    ch = ctx.options.charAt(nextc);
                }
                anchor = nextc + 1;
                ctx.state = PARSER_STATE.FIND_NEXT;
                continue;
            }

            assert (ctx.state != PARSER_STATE.IN_ESCAPE);
            assert (ctx.state != PARSER_STATE.FIND_NEXT);
            assert (ctx.state != PARSER_STATE.SKIP_LEAD_WS);
            assert (ctx.state != PARSER_STATE.IN_COMMENT);

            switch (ch) {
                case ' ':
                case '\t':
                case '\f':
                    if (ctx.state == PARSER_STATE.IN_QUOTE) {
                        continue;
                    }
                    // fall through
                case '\n':
                case '\r':
                    if (ctx.parts.size() == 0) {
                        token = ctx.options.substring(anchor, nextc);
                    } else {
                        ctx.parts.add(ctx.options.substring(anchor, nextc));
                        token = String.join("", ctx.parts);
                        ctx.parts = new ArrayList<>();
                    }
                    ctx.cptr = nextc + 1;
                    ctx.state = PARSER_STATE.FIND_NEXT;
                    return token;
                case '#':
                    if (ctx.state == PARSER_STATE.IN_QUOTE) {
                        continue;
                    }
                    ctx.state = PARSER_STATE.IN_COMMENT;
                    anchor = nextc + 1;
                    break;
                case '\\':
                    if (ctx.state != PARSER_STATE.IN_QUOTE) {
                        continue;
                    }
                    ctx.parts.add(ctx.options.substring(anchor, nextc));
                    ctx.state = PARSER_STATE.IN_ESCAPE;
                    // anchor after backslash character
                    anchor = nextc + 1;
                    break;
                case '\'':
                case '"':
                    if (ctx.state == PARSER_STATE.IN_QUOTE && ctx.quoteChar != ch) {
                        // not matching quote
                        continue;
                    }
                    // partial before quote
                    if (anchor != nextc) {
                        ctx.parts.add(ctx.options.substring(anchor, nextc));
                    }
                    // anchor after quote character
                    anchor = nextc + 1;
                    if (ctx.state == PARSER_STATE.IN_TOKEN) {
                        ctx.quoteChar = ch;
                        ctx.state = PARSER_STATE.IN_QUOTE;
                    } else {
                        ctx.state = PARSER_STATE.IN_TOKEN;
                    }
                    break;
                default:
                    break;
            }
        }

        assert (nextc == eob);
        // Only need partial token, not comment or whitespaces
        if (ctx.state == PARSER_STATE.IN_TOKEN || ctx.state == PARSER_STATE.IN_QUOTE) {
            if (anchor < nextc) {
                // not yet return until end of stream, we have part of a token.
                ctx.parts.add(ctx.options.substring(anchor, nextc));
            }
        }
        return null;
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
            nativeImage.addImageModulePath(Paths.get(mpEntry), false);
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
