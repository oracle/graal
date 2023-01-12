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
import java.util.List;

import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.driver.NativeImage.ArgumentQueue;

class DefaultOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    static final String verboseOption = "--verbose";
    private static final String requireValidJarFileMessage = "-jar requires a valid jarfile";
    private static final String newStyleClasspathOptionName = "--class-path";

    static final String addModulesOption = "--add-modules";
    private static final String addModulesErrorMessage = " requires modules to be specified";

    /* Defunct legacy options that we have to accept to maintain backward compatibility */
    private static final String noServerOption = "--no-server";

    DefaultOptionHandler(NativeImage nativeImage) {
        super(nativeImage);
    }

    boolean disableAtFiles = false;

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
                nativeImage.addImageBuilderJavaArgs(addModulesOption, addModulesArgs);
                nativeImage.addAddedModules(addModulesArgs);
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
                nativeImage.setDiagnostics(true);
                nativeImage.addPlainImageBuilderArg("-H:+DiagnosticsMode");
                nativeImage.addPlainImageBuilderArg("-H:DiagnosticsDir=" + nativeImage.diagnosticsDir);
                System.out.println("# Diagnostics mode enabled: image-build reports are saved to " + nativeImage.diagnosticsDir);
                return true;
            case "--disable-@files":
                args.poll();
                disableAtFiles = true;
                return true;
            case noServerOption:
                args.poll();
                NativeImage.showWarning("Ignoring server-mode native-image argument " + headArg + ".");
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
        if (headArg.startsWith(NativeImage.oH)) {
            args.poll();
            nativeImage.addPlainImageBuilderArg(NativeImage.injectHostedOptionOrigin(headArg, args.argumentOrigin));
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
                NativeImage.showError(headArg + addModulesErrorMessage);
            }
            nativeImage.addImageBuilderJavaArgs(addModulesOption, addModulesArgs);
            nativeImage.addAddedModules(addModulesArgs);
            return true;
        }
        if (headArg.startsWith("@") && !disableAtFiles) {
            args.poll();
            headArg = headArg.substring(1);
            Path argFile = Paths.get(headArg);
            NativeImage.NativeImageArgsProcessor processor = nativeImage.new NativeImageArgsProcessor(OptionOrigin.argFilePrefix + argFile);
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
