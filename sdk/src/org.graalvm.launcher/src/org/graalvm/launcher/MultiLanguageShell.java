/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.launcher;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.PolyglotException.StackFrame;

import jline.console.ConsoleReader;
import jline.console.UserInterruptException;
import jline.console.history.History;
import jline.console.history.MemoryHistory;
import jline.internal.NonBlockingInputStream;

class MultiLanguageShell {
    private final Map<Language, History> histories = new HashMap<>();
    private final Context context;
    private final InputStream in;
    private final OutputStream out;
    private final String defaultStartLanguage;

    MultiLanguageShell(Context context, InputStream in, OutputStream out, String defaultStartLanguage) {
        this.context = context;
        this.in = in;
        this.out = out;
        this.defaultStartLanguage = defaultStartLanguage;
    }

    public int readEvalPrint() throws IOException {
        ConsoleReader console = new ConsoleReader(in, out);
        console.setHandleUserInterrupt(true);
        console.setExpandEvents(false);
        console.setCopyPasteDetection(true);

        console.println("GraalVM MultiLanguage Shell " + context.getEngine().getVersion());
        console.println("Copyright (c) 2013-2019, Oracle and/or its affiliates");

        List<Language> languages = new ArrayList<>();
        Set<Language> uniqueValues = new HashSet<>();
        for (Language language : context.getEngine().getLanguages().values()) {
            if (language.isInteractive()) {
                if (uniqueValues.add(language)) {
                    languages.add(language);
                }
            }
        }
        languages.sort(Comparator.comparing(Language::getName));

        Map<String, Language> prompts = new HashMap<>();

        StringBuilder promptsString = new StringBuilder();
        for (Language language : languages) {
            String prompt = createPrompt(language).trim();
            promptsString.append(prompt).append(" ");
            prompts.put(prompt, language);
            console.println("  " + language.getName() + " version " + language.getVersion());
        }

        if (languages.isEmpty()) {
            throw new Launcher.AbortException("Error: No Graal languages installed. Exiting shell.", 1);
        }

        printUsage(console, promptsString, false);

        int maxNameLength = 0;
        for (Language language : languages) {
            maxNameLength = Math.max(maxNameLength, language.getName().length());
        }

        String startLanguage = defaultStartLanguage;
        if (startLanguage == null) {
            startLanguage = languages.get(0).getId();
        }

        Language currentLanguage = context.getEngine().getLanguages().get(startLanguage);
        if (currentLanguage == null) {
            throw new Launcher.AbortException("Error: could not find language '" + startLanguage + "'", 1);
        }
        assert languages.indexOf(currentLanguage) >= 0;
        Source bufferSource = null;
        String id = currentLanguage.getId();
        // console.println("initialize time: " + (System.currentTimeMillis() - start));
        String prompt = createPrompt(currentLanguage);

        console.getKeys().bind(String.valueOf((char) 12), new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                throw new ChangeLanguageException(null);
            }
        });

        // initializes the language
        context.initialize(currentLanguage.getId());

        boolean verboseErrors = false;

        for (;;) {
            String input = null;
            Source source = null;
            try {
                input = console.readLine(bufferSource == null ? prompt : createBufferPrompt(prompt));

                if (input == null) {
                    break;
                } else if (bufferSource == null && input.trim().equals("")) {
                    continue;
                }

                Language switchedLanguage = null;

                String trimmedInput = input.trim();
                if (trimmedInput.equals("-usage")) {
                    printUsage(console, promptsString, true);
                    input = "";
                } else if (trimmedInput.equals("-verboseErrors")) {
                    verboseErrors = !verboseErrors;
                    if (verboseErrors) {
                        console.println("Verbose errors is now on.");
                    } else {
                        console.println("Verbose errors is now off.");
                    }
                    input = "";
                } else if (prompts.containsKey(trimmedInput)) {
                    switchedLanguage = prompts.get(input);
                    input = "";
                } else if (bufferSource != null) {
                    input = bufferSource.getCharacters() + "\n" + input;
                }

                NonBlockingInputStream nonBlockIn = ((NonBlockingInputStream) console.getInput());
                while (nonBlockIn.isNonBlockingEnabled() && nonBlockIn.peek(10) >= 0 && switchedLanguage == null) {
                    String line = console.readLine(createBufferPrompt(prompt));
                    String trimmedLine = line.trim();
                    if (prompts.containsKey(trimmedLine)) {
                        switchedLanguage = prompts.get(trimmedLine);
                        break;
                    } else {
                        input += "\n" + line;
                    }
                }
                if (!input.trim().equals("")) {
                    source = Source.newBuilder(currentLanguage.getId(), input, "<shell>").interactive(true).build();
                    context.eval(source);
                    bufferSource = null;
                    console.getHistory().replace(source.getCharacters());
                }

                if (switchedLanguage != null) {
                    throw new ChangeLanguageException(switchedLanguage);
                }

            } catch (UserInterruptException | EOFException e) {
                // interrupted by ctrl-c
                break;
            } catch (ChangeLanguageException e) {
                bufferSource = null;
                histories.put(currentLanguage, console.getHistory());
                currentLanguage = e.getLanguage() == null ? languages.get((languages.indexOf(currentLanguage) + 1) % languages.size()) : e.getLanguage();
                History history = histories.computeIfAbsent(currentLanguage, k -> new MemoryHistory());
                console.setHistory(history);
                id = currentLanguage.getId();
                prompt = createPrompt(currentLanguage);
                console.resetPromptLine("", "", 0);
                context.initialize(id);
            } catch (PolyglotException e) {
                bufferSource = null;
                if (e.isInternalError()) {
                    console.println("Internal error occurred: " + e.toString());
                    if (verboseErrors) {
                        e.printStackTrace(new PrintWriter(console.getOutput()));
                    } else {
                        console.println("Run with --verbose to see the full stack trace.");
                    }
                } else if (e.isExit()) {
                    return e.getExitStatus();
                } else if (e.isCancelled()) {
                    console.println("Execution got cancelled.");
                } else if (e.isIncompleteSource()) {
                    bufferSource = source;
                } else if (e.isSyntaxError()) {
                    console.println(e.getMessage());
                } else {
                    List<StackFrame> trace = new ArrayList<>();
                    for (StackFrame stackFrame : e.getPolyglotStackTrace()) {
                        trace.add(stackFrame);
                    }
                    // remove trailing host frames
                    for (int i = trace.size() - 1; i >= 0; i--) {
                        if (trace.get(i).isHostFrame()) {
                            trace.remove(i);
                        } else {
                            break;
                        }
                    }
                    if (e.isHostException()) {
                        console.println(e.asHostException().toString());
                    } else {
                        console.println(e.getMessage());
                    }
                    // no need to print stack traces with single entry
                    if (trace.size() > 1) {
                        for (StackFrame stackFrame : trace) {
                            console.print("        at ");
                            console.println(stackFrame.toString());
                        }
                    }
                }
            } catch (Throwable e) {
                console.println("Internal error occurred: " + e.toString());
                if (verboseErrors) {
                    e.printStackTrace(new PrintWriter(console.getOutput()));
                } else {
                    console.println("Run with --verbose to see the full stack trace.");
                }
            }
        }
        return 0;
    }

    private static void printUsage(ConsoleReader console, StringBuilder promptsString, boolean showCommands) throws IOException {
        if (showCommands) {
            console.println("Commands:");
            console.println("  -usage           to show this list.");
            console.println("  -verboseErrors   to toggle verbose error messages (default off).");
            console.println("  " + promptsString + "    to switch to a language.");
        } else {
            console.println("Usage: ");
            console.println("  Use Ctrl+L to switch language and Ctrl+D to exit.");
            console.println("  Enter -usage to get a list of available commands.");
        }
    }

    @SuppressWarnings("serial")
    private static class ChangeLanguageException extends RuntimeException {

        private final Language language;

        ChangeLanguageException(Language language) {
            this.language = language;
        }

        public Language getLanguage() {
            return language;
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }

    private static String createBufferPrompt(String prompt) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < prompt.length() - 2; i++) {
            b.append(" ");
        }
        return b.append("+ ").toString();
    }

    private static String createPrompt(Language currentLanguage) {
        return String.format("%s> ", currentLanguage.getId());
    }

}
