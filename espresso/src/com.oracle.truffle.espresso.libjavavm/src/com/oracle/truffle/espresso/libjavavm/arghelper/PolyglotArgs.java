/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libjavavm.arghelper;

import static com.oracle.truffle.espresso.libjavavm.Arguments.abort;
import static com.oracle.truffle.espresso.libjavavm.Arguments.abortExperimental;
import static com.oracle.truffle.espresso.libjavavm.arghelper.ArgumentsHandler.isBooleanOption;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import org.graalvm.launcher.Launcher;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionStability;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;

/**
 * Handles communicating options to polyglot.
 */
class PolyglotArgs {
    private final Context.Builder builder;
    private final ArgumentsHandler handler;

    private Engine tempEngine;

    private String logFilePattern;
    private boolean displayVMOutput = true;
    private boolean logVMOutput;

    private Handler logHandler;

    PolyglotArgs(Context.Builder builder, ArgumentsHandler handler) {
        this.builder = builder;
        this.handler = handler;
    }

    private Engine getTempEngine() {
        if (tempEngine == null) {
            tempEngine = Engine.newBuilder().useSystemProperties(false).build();
        }
        return tempEngine;
    }

    void argumentProcessingDone() {
        if (tempEngine != null) {
            tempEngine.close();
            tempEngine = null;
        }
        try {
            logHandler = makeLogHandler();
        } catch (IOException ioe) {
            throw abort(ioe.toString());
        }
        builder.logHandler(logHandler);
    }

    private Handler makeLogHandler() throws IOException {
        Path logFile = null;
        if (logVMOutput) {
            logFile = makeLogFilePath(logFilePattern != null ? logFilePattern : "espresso_%p.log");
        }
        OutputStream out;
        if (displayVMOutput) {
            if (logVMOutput) {
                out = new TeeOutputStream(System.out, Launcher.newLogStream(logFile));
            } else {
                out = System.out;
            }
        } else {
            if (logVMOutput) {
                out = Launcher.newLogStream(logFile);
            } else {
                out = NullOutputStream.INSTANCE;
            }
        }
        StreamHandler streamHandler;
        if (displayVMOutput) {
            streamHandler = new FlushingStreamHandler(out, LogFormatter.INSTANCE);
        } else {
            streamHandler = new StreamHandler(out, LogFormatter.INSTANCE);
        }
        /*
         * Unlike Handler, StreamHandler sets its default Level to INFO. Truffle already handles
         * levels so streamHandler's internal level should be as permissive as possible.
         */
        streamHandler.setLevel(Level.ALL);
        return streamHandler;
    }

    private static final class FlushingStreamHandler extends StreamHandler {
        FlushingStreamHandler(OutputStream out, Formatter formatter) {
            super(out, formatter);
        }

        @Override
        public void publish(LogRecord record) {
            super.publish(record);
            flush();
        }
    }

    /**
     * See also {@code com.oracle.truffle.polyglot.PolyglotLoggers.StreamLogHandler.FormatterImpl}.
     */
    private static final class LogFormatter extends Formatter {
        static final LogFormatter INSTANCE = new LogFormatter();

        @Override
        public String format(LogRecord record) {
            String loggerName = formatLoggerName(record.getLoggerName());
            String message = formatMessage(record);
            String stackTrace = formatStackTrace(record.getThrown());
            if (loggerName == null) {
                // raw message
                if (stackTrace.isEmpty()) {
                    return message;
                }
                return message + stackTrace;
            }
            return String.format("[%1$s] %2$s: %3$s%4$s%n", loggerName, record.getLevel().getName(), message, stackTrace);
        }

        private static String formatStackTrace(Throwable exception) {
            if (exception == null) {
                return "";
            }
            StringWriter str = new StringWriter();
            try (PrintWriter out = new PrintWriter(str)) {
                out.println();
                exception.printStackTrace(out);
            }
            return str.toString();
        }

        private static String formatLoggerName(String loggerName) {
            if (loggerName == null) {
                return null;
            }
            String id;
            String name;
            int index = loggerName.indexOf('.');
            if (index < 0) {
                id = loggerName;
                name = "";
            } else {
                id = loggerName.substring(0, index);
                name = loggerName.substring(index + 1);
            }
            if (name.isEmpty()) {
                return id;
            }
            StringBuilder sb = new StringBuilder(id);
            sb.append("::");
            sb.append(possibleSimpleName(name));
            return sb.toString();
        }

        private static String possibleSimpleName(String loggerName) {
            int index = -1;
            for (int i = loggerName.indexOf('.'); i >= 0; i = loggerName.indexOf('.', i + 1)) {
                if (i + 1 < loggerName.length() && Character.isUpperCase(loggerName.charAt(i + 1))) {
                    index = i + 1;
                    break;
                }
            }
            return index < 0 ? loggerName : loggerName.substring(index);
        }
    }

    /**
     * Creates a path for the log given a pattern. In this pattern:
     * <ul>
     * <li>{@code %p} will be replaced by {@code pid1234} where 1234 is the current process id</li>
     * <li>{@code %t} will be replaced by a timestamp for the current time with the format
     * {@code yyyy-MM-dd_HH-mm-ss}</li>
     * </ul>
     * See {@code make_log_name} in {@code ostream.cpp}.
     */
    private static Path makeLogFilePath(String logFilePattern) {
        String logFileName = logFilePattern;
        int pidIdx = logFileName.indexOf("%p");
        int tsIdx = logFileName.indexOf("%t");
        if (pidIdx >= 0 || tsIdx >= 0) {
            StringBuilder sb = new StringBuilder();
            long pid = ProcessHandle.current().pid();
            if (pidIdx >= 0 && tsIdx >= 0) {
                if (pidIdx < tsIdx) {
                    sb.append(logFileName, 0, pidIdx);
                    sb.append("pid");
                    sb.append(pid);
                    sb.append(logFileName, pidIdx + 2, tsIdx);
                    sb.append(getLogPathTimestamp());
                    sb.append(logFileName, tsIdx + 2, logFileName.length());
                } else {
                    sb.append(logFileName, 0, tsIdx);
                    sb.append(getLogPathTimestamp());
                    sb.append(logFileName, tsIdx + 2, pidIdx);
                    sb.append("pid");
                    sb.append(pid);
                    sb.append(logFileName, pidIdx + 2, logFileName.length());
                }
            } else if (pidIdx >= 0) {
                sb.append(logFileName, 0, pidIdx);
                sb.append("pid");
                sb.append(pid);
                sb.append(logFileName, pidIdx + 2, logFileName.length());
            } else {
                sb.append(logFileName, 0, tsIdx);
                sb.append(getLogPathTimestamp());
                sb.append(logFileName, tsIdx + 2, logFileName.length());
            }
            logFileName = sb.toString();
        }
        return Path.of(logFileName);
    }

    private static String getLogPathTimestamp() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(LocalDateTime.now());
    }

    private static final class NullOutputStream extends OutputStream {
        static OutputStream INSTANCE = new NullOutputStream();

        @Override
        @SuppressWarnings("unused")
        public void write(int b) {
        }

        @Override
        @SuppressWarnings("unused")
        public void write(byte[] b, int off, int len) {
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream a;
        private final OutputStream b;

        TeeOutputStream(OutputStream a, OutputStream b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void write(int v) throws IOException {
            this.a.write(v);
            this.b.write(v);
        }

        @Override
        public void write(byte[] data, int off, int len) throws IOException {
            this.a.write(data, off, len);
            this.b.write(data, off, len);
        }

        @Override
        public void flush() throws IOException {
            this.a.flush();
            this.b.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                this.a.close();
            } finally {
                this.b.close();
            }

        }
    }

    Handler getLogHandler() {
        return logHandler;
    }

    void setLogFile(String logFileName) {
        logFilePattern = logFileName;
    }

    void setDisplayVMOutput(boolean displayVMOutput) {
        this.displayVMOutput = displayVMOutput;
    }

    void setLogVMOutput(boolean logVMOutput) {
        this.logVMOutput = logVMOutput;
    }

    void parsePolyglotOption(String arg, boolean experimentalOptions) {
        if (arg.length() <= 2 || !arg.startsWith("--")) {
            throw abort(String.format("Unrecognized option: %s%n", arg));
        }
        int eqIdx = arg.indexOf('=');
        String key;
        String value;
        if (eqIdx < 0) {
            key = arg.substring(2);
            value = "";
        } else {
            key = arg.substring(2, eqIdx);
            value = arg.substring(eqIdx + 1);
        }

        int index = key.indexOf('.');
        String group = key;
        if (index >= 0) {
            group = group.substring(0, index);
        }
        parsePolyglotOption(group, key, value, arg, experimentalOptions);
    }

    void parsePolyglotOption(String group, String key, String value, String arg, boolean experimentalOptions) {
        if ("log".equals(group)) {
            if (key.endsWith(".level")) {
                try {
                    Level.parse(value);
                    builder.option(key, value);
                } catch (IllegalArgumentException e) {
                    throw abort(String.format("Invalid log level %s specified. %s'", arg, e.getMessage()));
                }
                return;
            } else if ("log.file".equals(key)) {
                logFilePattern = value;
                logVMOutput = true;
                displayVMOutput = false;
                return;
            }
        }
        OptionDescriptor descriptor = findOptionDescriptor(group, key);
        if (descriptor == null) {
            descriptor = findOptionDescriptor("java", "java" + "." + key);
            if (descriptor == null) {
                throw abort(String.format("Unrecognized option: %s%n", arg));
            }
        }
        String actualValue = value;
        if (isBooleanOption(descriptor) && actualValue.isEmpty()) {
            actualValue = "true";
        }
        try {
            descriptor.getKey().getType().convert(actualValue);
        } catch (IllegalArgumentException e) {
            throw abort(String.format("Invalid argument %s specified. %s'", arg, e.getMessage()));
        }
        if (!experimentalOptions && descriptor.getStability() == OptionStability.EXPERIMENTAL) {
            throw abortExperimental(String.format("Option '%s' is experimental and must be enabled via '--experimental-options'%n" +
                            "Do not use experimental options in production environments.", arg));
        }
        // use the full name of the found descriptor
        builder.option(descriptor.getName(), actualValue);
    }

    private OptionDescriptor findOptionDescriptor(String group, String key) {
        OptionDescriptors descriptors = null;
        if ("engine".equals(group) || "compiler".equals(group)) {
            descriptors = getTempEngine().getOptions();
        } else {
            Engine engine = getTempEngine();
            if (engine.getLanguages().containsKey(group)) {
                descriptors = engine.getLanguages().get(group).getOptions();
            } else if (engine.getInstruments().containsKey(group)) {
                descriptors = engine.getInstruments().get(group).getOptions();
            }
        }
        if (descriptors == null) {
            return null;
        }
        return descriptors.get(key);
    }

    void printToolsHelp(OptionCategory optionCategory) {
        Map<Instrument, List<PrintableOption>> instrumentsOptions = new HashMap<>();
        List<Instrument> instruments = sortedInstruments(getTempEngine());
        for (Instrument instrument : instruments) {
            List<PrintableOption> options = filterOptions(instrument.getOptions(), optionCategory);
            if (!options.isEmpty()) {
                instrumentsOptions.put(instrument, options);
            }
        }
        if (!instrumentsOptions.isEmpty()) {
            handler.printRaw(optionsTitle("tool", optionCategory));
            for (Instrument instrument : instruments) {
                List<PrintableOption> options = instrumentsOptions.get(instrument);
                if (options != null) {
                    printOptions(options, "  " + instrument.getName() + ":", 4);
                }
            }
        }
    }

    void printEngineHelp(OptionCategory optionCategory) {
        List<PrintableOption> engineOptions = filterOptions(getTempEngine().getOptions(), optionCategory);
        if (!engineOptions.isEmpty()) {
            printOptions(engineOptions, optionsTitle("engine", optionCategory), 2);
        }
    }

    void printLanguageHelp(OptionCategory optionCategory) {
        Map<Language, List<PrintableOption>> languagesOptions = new HashMap<>();
        List<Language> languages = sortedLanguages(getTempEngine());
        for (Language language : languages) {
            List<PrintableOption> options = filterOptions(language.getOptions(), optionCategory);
            if (!options.isEmpty()) {
                languagesOptions.put(language, options);
            }
        }
        if (!languagesOptions.isEmpty()) {
            handler.printRaw(optionsTitle("language", optionCategory));
            for (Language language : languages) {
                List<PrintableOption> options = languagesOptions.get(language);
                if (options != null) {
                    printOptions(options, "  " + language.getName() + ":", 4);
                }
            }
        }
    }

    static final class PrintableOption implements Comparable<PrintableOption> {
        final String option;
        final String description;

        private PrintableOption(String option, String description) {
            this.option = option;
            this.description = description;
        }

        @Override
        public int compareTo(PrintableOption o) {
            return this.option.compareTo(o.option);
        }
    }

    private static String optionsTitle(String kind, OptionCategory optionCategory) {
        String category = switch (optionCategory) {
            case USER -> "User ";
            case EXPERT -> "Expert ";
            case INTERNAL -> "Internal ";
            default -> "";
        };
        return category + kind + " options:";
    }

    private void printOptions(List<PrintableOption> options, String title, int indentation) {
        Collections.sort(options);
        handler.printRaw(title);
        for (PrintableOption option : options) {
            printOption(option, indentation);
        }
    }

    private void printOption(PrintableOption option, int indentation) {
        handler.printLauncherOption(option.option, option.description, indentation);
    }

    private static List<PrintableOption> filterOptions(OptionDescriptors descriptors, OptionCategory optionCategory) {
        List<PrintableOption> options = new ArrayList<>();
        for (OptionDescriptor descriptor : descriptors) {
            if (!descriptor.isDeprecated() && sameCategory(descriptor, optionCategory)) {
                options.add(asPrintableOption(descriptor));
            }
        }
        return options;
    }

    private static boolean sameCategory(OptionDescriptor descriptor, OptionCategory optionCategory) {
        return descriptor.getCategory().ordinal() == optionCategory.ordinal();
    }

    private static PrintableOption asPrintableOption(OptionDescriptor descriptor) {
        StringBuilder key = new StringBuilder("--");
        key.append(descriptor.getName());
        Object defaultValue = descriptor.getKey().getDefaultValue();
        if (defaultValue != Boolean.FALSE) {
            key.append("=<");
            key.append(descriptor.getKey().getType().getName());
            key.append(">");
        }
        return new PrintableOption(key.toString(), descriptor.getHelp());
    }

    private static List<Language> sortedLanguages(Engine engine) {
        List<Language> languages = new ArrayList<>(engine.getLanguages().values());
        languages.sort(Comparator.comparing(Language::getId));
        return languages;
    }

    private static List<Instrument> sortedInstruments(Engine engine) {
        List<Instrument> instruments = new ArrayList<>();
        for (Instrument instrument : engine.getInstruments().values()) {
            // no options not accessible to the user.
            if (!instrument.getOptions().iterator().hasNext()) {
                continue;
            }
            instruments.add(instrument);
        }
        instruments.sort(Comparator.comparing(Instrument::getId));
        return instruments;
    }

}
