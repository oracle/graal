/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

import com.oracle.svm.hosted.util.JDKArgsUtils;
import com.oracle.svm.shared.option.OptionOrigin;

public class ArgFilesOptionPreprocessor {

    private static final String DISABLE_AT_FILES_OPTION = "--disable-@files";
    private static final String DIRECTIVE_PREFIX = "# native-image:";
    private static final String VALUE_CONTINUATIONS_SETTING = "value-continuations";
    private static final String RECURSIVE_ARGUMENT_FILES_SETTING = "recursive-argument-files";
    private static final int MAX_ARGUMENT_FILE_NESTING = 64;
    private static final int MAX_ARGUMENT_FILE_EXPANSION_SIZE = 16 * 1024 * 1024;

    private record ArgumentFileSettings(boolean valueContinuations, boolean recursiveArgumentFiles) {
        private static final ArgumentFileSettings DEFAULT = new ArgumentFileSettings(false, false);
    }

    private record ParsedArgumentFile(List<String> arguments, ArgumentFileSettings settings) {
    }

    private record ActiveArgumentFile(String identity, String displayPath) {
    }

    private static final class ArgumentFileExpansion {
        private final List<ActiveArgumentFile> activeFiles = new ArrayList<>();
        private long remainingCharacters = Integer.MAX_VALUE;

        private void accountForContents(String contents, ArgumentFileSettings settings) {
            if (activeFiles.size() == 1 && settings.recursiveArgumentFiles()) {
                // Recursion can multiply small inputs into huge expansions. Only roots that opt in
                // receive the smaller budget; ordinary argument files retain the Java file-size limit.
                remainingCharacters = MAX_ARGUMENT_FILE_EXPANSION_SIZE;
            }
            if (contents.length() > remainingCharacters) {
                throw NativeImage.showError("Argument-file expansion rooted at '" + activeFiles.getFirst().displayPath() + "' exceeds the maximum cumulative input size of " +
                                MAX_ARGUMENT_FILE_EXPANSION_SIZE + " characters while expanding '" + activeFiles.getLast().displayPath() +
                                "'. Reduce repeated includes or the argument-file contents.");
            }
            remainingCharacters -= contents.length();
        }
    }

    private boolean disableAtFiles = false;

    public List<String> process(String currentArg) {
        return process(currentArg, null);
    }

    public List<String> process(String currentArg, OptionOrigin origin) {
        String argWithoutQuotes = currentArg.replaceAll("['\"]*", "");
        if (DISABLE_AT_FILES_OPTION.equals(argWithoutQuotes)) {
            disableAtFiles = true;
            return List.of();
        }

        if (!disableAtFiles && argWithoutQuotes.startsWith("@")) {
            String argWithoutAt = argWithoutQuotes.substring(1);
            if (argWithoutAt.startsWith("@")) {
                // escaped @argument
                return List.of(argWithoutAt);
            }
            Path argFile = Paths.get(argWithoutAt);
            return processArgumentFile(argFile, origin, null, new ArgumentFileExpansion());
        }

        return List.of(currentArg);
    }

    private List<String> processArgumentFile(Path argFile, OptionOrigin origin, Path includingFile, ArgumentFileExpansion expansion) {
        List<ActiveArgumentFile> activeFiles = expansion.activeFiles;
        Path resolvedFile = resolveArgumentFile(argFile, origin, includingFile);
        String identity = getArgumentFileIdentity(resolvedFile, origin);
        int cycleStart = -1;
        for (int i = 0; i < activeFiles.size(); i++) {
            if (activeFiles.get(i).identity().equals(identity)) {
                cycleStart = i;
                break;
            }
        }
        if (cycleStart >= 0) {
            StringJoiner chain = new StringJoiner(" -> ");
            for (int i = cycleStart; i < activeFiles.size(); i++) {
                chain.add(activeFiles.get(i).displayPath());
            }
            chain.add(displayArgumentFile(resolvedFile, origin));
            throw NativeImage.showError("Argument-file inclusion cycle detected: " + chain);
        }
        if (activeFiles.size() >= MAX_ARGUMENT_FILE_NESTING) {
            StringJoiner chain = new StringJoiner(" -> ");
            activeFiles.forEach(file -> chain.add(file.displayPath()));
            chain.add(displayArgumentFile(resolvedFile, origin));
            throw NativeImage.showError("Argument-file inclusion exceeds the maximum nesting depth of " + MAX_ARGUMENT_FILE_NESTING + ": " + chain);
        }

        activeFiles.add(new ActiveArgumentFile(identity, displayArgumentFile(resolvedFile, origin)));
        try {
            ParsedArgumentFile parsed = readArgFile(resolvedFile, origin, expansion);
            List<String> result = new ArrayList<>();
            for (String argument : parsed.arguments()) {
                if (parsed.settings().recursiveArgumentFiles() && !disableAtFiles && argument.startsWith("@")) {
                    String referencedFile = argument.substring(1);
                    if (referencedFile.startsWith("@")) {
                        // An escaped @argument is literal and is not expanded recursively.
                        addArg(result, referencedFile);
                    } else {
                        result.addAll(processArgumentFile(Paths.get(referencedFile), origin, resolvedFile, expansion));
                    }
                } else {
                    addArg(result, argument);
                }
            }
            return result;
        } finally {
            activeFiles.remove(activeFiles.size() - 1);
        }
    }

    private static Path resolveArgumentFile(Path argFile, OptionOrigin origin, Path includingFile) {
        if (origin != null && argFile.isAbsolute()) {
            throw NativeImage.showError("Argument file '" + argFile + "' must be relative to " + origin);
        }
        Path resolved = argFile;
        if (includingFile != null) {
            Path parent = includingFile.getParent();
            if (parent != null) {
                resolved = parent.resolve(argFile);
            }
        }
        // Match java @file lookup for command-line paths: let the filesystem resolve symlinks and '..'.
        if (origin != null) {
            resolved = origin.normalizeRedirectionPath(resolved);
            // Check before JAR filesystem resolution, which would discard leading '..' components.
            Path relativeToContainer = origin.resolveRedirectionPath(resolved);
            if (relativeToContainer.getRoot() != null || relativeToContainer.startsWith("..")) {
                throw NativeImage.showError("Argument file '" + argFile + "' referenced by " + origin + " escapes its containing directory or JAR");
            }
        }
        return resolved;
    }

    private static String getArgumentFileIdentity(Path argFile, OptionOrigin origin) {
        if (origin == null) {
            return canonicalPath(argFile).toString();
        }
        Path relativeToContainer = origin.resolveRedirectionPath(argFile);
        if (origin.container() != null && "file".equals(origin.container().getScheme())) {
            Path container = Path.of(origin.container());
            if (Files.isDirectory(container)) {
                return canonicalPath(container.resolve(relativeToContainer)).toString();
            }
            return canonicalPath(container) + "!/" + relativeToContainer.toString().replace('\\', '/');
        }
        return origin + "!/" + relativeToContainer.toString().replace('\\', '/');
    }

    private static Path canonicalPath(Path path) {
        Path absolute = path.toAbsolutePath();
        try {
            return absolute.toRealPath();
        } catch (IOException ignored) {
            return absolute;
        }
    }

    private static String displayArgumentFile(Path argFile, OptionOrigin origin) {
        return origin == null ? argFile.toString() : origin + " -> " + argFile;
    }

    // Ported from JDK11's java.base/share/native/libjli/args.c
    private enum PARSER_STATE {
        FIND_NEXT,
        IN_COMMENT,
        IN_QUOTE,
        IN_ESCAPE,
        SKIP_LEAD_WS,
        IN_TOKEN
    }

    private static final class CTX_ARGS {
        PARSER_STATE state;
        int cptr;
        int eob;
        char quoteChar;
        List<String> parts;
        String options;
        Path file;
        boolean markBlankLines;
        boolean blankLineBeforeToken;
    }

    // Ported from JDK11's java.base/share/native/libjli/args.c
    private static ParsedArgumentFile readArgFile(Path file, OptionOrigin origin, ArgumentFileExpansion expansion) {
        List<String> arguments = new ArrayList<>();

        String options = null;
        try {
            options = origin == null ? new String(Files.readAllBytes(file), StandardCharsets.UTF_8) : origin.readRedirectionFile(file);
        } catch (IOException e) {
            throw NativeImage.showError("Cannot read argument file '" + file + "'", e);
        }

        ArgumentFileSettings settings = getArgumentFileSettings(options, file);
        expansion.accountForContents(options, settings);
        CTX_ARGS ctx = new CTX_ARGS();
        ctx.state = PARSER_STATE.FIND_NEXT;
        ctx.parts = new ArrayList<>(4);
        ctx.quoteChar = '"';
        ctx.cptr = 0;
        ctx.eob = options.length();
        ctx.options = options;
        ctx.file = file;
        ctx.markBlankLines = settings.valueContinuations();

        while (true) {
            String token = nextToken(ctx);
            if (ctx.blankLineBeforeToken) {
                // Null marks a section boundary, removed during continuation expansion.
                arguments.add(null);
            }
            if (token == null) {
                break;
            }
            arguments.add(token);
        }

        // remaining partial token
        if (ctx.state == PARSER_STATE.IN_TOKEN || ctx.state == PARSER_STATE.IN_QUOTE) {
            if (ctx.parts.size() != 0) {
                arguments.add(String.join("", ctx.parts));
            }
        }
        List<String> expanded = settings.valueContinuations() ? expandOptionValueContinuations(arguments) : arguments;
        return new ParsedArgumentFile(expanded, settings);
    }

    private static ArgumentFileSettings getArgumentFileSettings(String options, Path file) {
        String firstLine = options.lines().findFirst().orElse("");
        if (firstLine.startsWith(DIRECTIVE_PREFIX)) {
            return parseDirective(firstLine, file, 1);
        }
        return ArgumentFileSettings.DEFAULT;
    }

    private static ArgumentFileSettings parseDirective(String directive, Path file, long line) {
        String settingText = directive.substring(DIRECTIVE_PREFIX.length()).strip();
        String[] settingNames = settingText.split(" +");
        Set<String> seen = new HashSet<>();
        boolean valueContinuations = false;
        boolean recursiveArgumentFiles = false;
        for (String settingName : settingNames) {
            if (!seen.add(settingName)) {
                throw NativeImage.showError(file + ":" + line + ": Duplicate argument-file setting: '" + settingName + "'.");
            }
            switch (settingName) {
                case VALUE_CONTINUATIONS_SETTING -> valueContinuations = true;
                case RECURSIVE_ARGUMENT_FILES_SETTING -> recursiveArgumentFiles = true;
                default -> throw NativeImage.showError(file + ":" + line + ": Unknown argument-file setting: '" + settingName + "'. Expected '" + VALUE_CONTINUATIONS_SETTING + "' or '" +
                                RECURSIVE_ARGUMENT_FILES_SETTING + "'.");
            }
        }
        return new ArgumentFileSettings(valueContinuations, recursiveArgumentFiles);
    }

    /// Expands the option-value sections enabled by the file's first-line marker after Java-style tokenization.
    /// An option ending in `=...` collects comma-separated values until a blank line, another such option, or EOF.
    private static List<String> expandOptionValueContinuations(List<String> arguments) {
        List<String> expanded = new ArrayList<>();
        StringJoiner continuation = null;
        for (String arg : arguments) {
            if (arg == null || (arg.startsWith("-") && arg.endsWith("=..."))) {
                if (continuation != null) {
                    expanded.add(continuation.toString());
                }
                continuation = arg == null ? null : new StringJoiner(",", arg.substring(0, arg.length() - 3), "");
            } else if (continuation != null) {
                continuation.add(arg);
            } else {
                expanded.add(arg);
            }
        }
        if (continuation != null) {
            expanded.add(continuation.toString());
        }
        return expanded;
    }

    private void addArg(List<String> args, String arg) {
        Objects.requireNonNull(arg);
        if (DISABLE_AT_FILES_OPTION.equals(arg)) {
            disableAtFiles = true;
        } else {
            args.add(arg);
        }
    }

    private static boolean isBlankLineEnd(String options, int index) {
        char ch = options.charAt(index);
        if ((ch != '\n' && ch != '\r') || (ch == '\n' && index > 0 && options.charAt(index - 1) == '\r')) {
            return false;
        }
        for (int i = index - 1; i >= 0; i--) {
            char previous = options.charAt(i);
            if (previous == '\n' || previous == '\r') {
                break;
            }
            if (!JDKArgsUtils.isspace(previous)) {
                return false;
            }
        }
        return true;
    }

    private static void validateDirective(CTX_ARGS ctx, int commentStart) {
        String options = ctx.options;
        if (!options.startsWith(DIRECTIVE_PREFIX, commentStart)) {
            return;
        }
        int lineStart = Math.max(options.lastIndexOf('\n', commentStart), options.lastIndexOf('\r', commentStart)) + 1;
        if (!options.substring(lineStart, commentStart).isBlank()) {
            // A directive must be a standalone comment, not a trailing comment on an argument.
            return;
        }
        long line = options.substring(0, commentStart + 1).lines().count();
        String location = ctx.file + ":" + line + ": ";
        if (commentStart != 0) {
            throw NativeImage.showError(location + "Native Image argument-file directives must start the first line. Move the directive before any leading comments or whitespace.");
        }
        int lineEnd = commentStart;
        while (lineEnd < options.length() && options.charAt(lineEnd) != '\n' && options.charAt(lineEnd) != '\r') {
            lineEnd++;
        }
        String directive = options.substring(commentStart, lineEnd);
        parseDirective(directive, ctx.file, line);
    }

    // Ported from JDK11's java.base/share/native/libjli/args.c
    @SuppressWarnings("fallthrough")
    private static String nextToken(CTX_ARGS ctx) {
        ctx.blankLineBeforeToken = false;
        int nextc = ctx.cptr;
        int eob = ctx.eob;
        int anchor = nextc;
        String token;

        for (; nextc < eob; nextc++) {
            char ch = ctx.options.charAt(nextc);

            // Skip white space characters
            if (ctx.state == PARSER_STATE.FIND_NEXT || ctx.state == PARSER_STATE.SKIP_LEAD_WS) {
                while (JDKArgsUtils.isspace(ch)) {
                    if (ctx.markBlankLines && ctx.state == PARSER_STATE.FIND_NEXT && isBlankLineEnd(ctx.options, nextc)) {
                        ctx.blankLineBeforeToken = true;
                    }
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
                    String escaped;
                    switch (ch) {
                        case 'n':
                            escaped = "\n";
                            break;
                        case 'r':
                            escaped = "\r";
                            break;
                        case 't':
                            escaped = "\t";
                            break;
                        case 'f':
                            escaped = "\f";
                            break;
                        default:
                            escaped = String.valueOf(ch);
                            break;
                    }
                    ctx.parts.add(escaped);
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
                    validateDirective(ctx, nextc);
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
}
