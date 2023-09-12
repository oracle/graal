/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.launcher;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class WasmLauncher extends AbstractLanguageLauncher {
    private File file = null;
    private VersionAction versionAction = VersionAction.None;
    private String customEntryPoint = null;
    private String[] programArguments = null;
    private ArrayList<String> argumentErrors = null;

    private static final String USAGE = "Usage: wasm [OPTION...] [FILE] [ARG...]";

    public static void main(String[] args) {
        new WasmLauncher().launch(args);
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        final ListIterator<String> argIterator = arguments.listIterator();
        final ArrayList<String> unrecognizedArguments = new ArrayList<>();
        final List<String> programArgumentsList = new ArrayList<>();
        argumentErrors = new ArrayList<>();

        // Add the default arguments.
        polyglotOptions.put("wasm.Builtins", "wasi_snapshot_preview1");

        while (argIterator.hasNext()) {
            final String argument = argIterator.next();
            if (file == null) {
                if (argument.startsWith("-")) {
                    switch (argument) {
                        case "--show-version":
                            versionAction = VersionAction.PrintAndContinue;
                            break;
                        case "--version":
                            versionAction = VersionAction.PrintAndExit;
                            break;
                        default:
                            if (argument.startsWith("--entry-point=")) {
                                String[] parts = argument.split("=", 2);
                                if (parts[1].isEmpty()) {
                                    argumentErrors.add("Must specify function name after --entry-point.");
                                } else {
                                    customEntryPoint = parts[1];
                                }
                            } else {
                                unrecognizedArguments.add(argument);
                            }
                            break;
                    }
                } else {
                    file = new File(argument);
                    programArgumentsList.add(argument);
                    break;
                }
            } else {
                programArgumentsList.add(argument);
                break;
            }
        }

        // Collect the program arguments.
        while (argIterator.hasNext()) {
            programArgumentsList.add(argIterator.next());
        }
        programArguments = programArgumentsList.toArray(new String[0]);

        return unrecognizedArguments;
    }

    @Override
    protected void validateArguments(Map<String, String> polyglotOptions) {
        for (String error : argumentErrors) {
            System.err.println(error);
        }
        if (versionAction != VersionAction.PrintAndExit) {
            if (file == null) {
                throw abort("The binary path is missing.\n" + USAGE);
            } else if (!file.exists()) {
                throw abort(String.format("WebAssembly binary '%s' does not exist.", file));
            }
        }
    }

    @Override
    protected void launch(Context.Builder contextBuilder) {
        System.exit(execute(contextBuilder));
    }

    private int execute(Context.Builder contextBuilder) {
        contextBuilder.arguments(getLanguageId(), programArguments);

        try (Context context = contextBuilder.build()) {
            runVersionAction(versionAction, context.getEngine());
            Value mainModule = context.eval(Source.newBuilder(getLanguageId(), file).build());

            Value entryPoint = detectEntryPoint(mainModule);
            if (entryPoint == null) {
                throw abort("No entry-point function found, cannot start program.");
            }

            entryPoint.execute();
            return 0;
        } catch (PolyglotException e) {
            if (e.isExit()) {
                return e.getExitStatus();
            }
            throw e;
        } catch (IOException e) {
            throw abort(String.format("Error loading file '%s': %s", file, e.getMessage()));
        } finally {
            System.out.flush();
            System.err.flush();
        }
    }

    private Value detectEntryPoint(Value mainModule) {
        if (customEntryPoint != null) {
            return mainModule.getMember(customEntryPoint);
        }
        Value candidate = mainModule.getMember("_start");
        if (candidate == null) {
            candidate = mainModule.getMember("_main");
        }
        return candidate;
    }

    @Override
    protected String getLanguageId() {
        return "wasm";
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        System.out.println();
        System.out.println("Usage: wasm [OPTION...] [FILE] [ARG...]");
        System.out.println("Run WebAssembly binary files on GraalVM's wasm engine.");
    }

    @Override
    protected void collectArguments(Set<String> options) {
    }
}
