/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.graalvm.collections.Pair;
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

    private Path logFile;

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
        if (logFile != null) {
            try {
                builder.logHandler(newLogStream(logFile));
            } catch (IOException ioe) {
                throw abort(ioe.toString());
            }
        }
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
        if ("log".equals(group)) {
            if (key.endsWith(".level")) {
                try {
                    Level.parse(value);
                    builder.option(key, value);
                } catch (IllegalArgumentException e) {
                    throw abort(String.format("Invalid log level %s specified. %s'", arg, e.getMessage()));
                }
                return;
            } else if (key.equals("log.file")) {
                logFile = Paths.get(value);
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
        if (isBooleanOption(descriptor) && eqIdx < 0) {
            value = "true";
        }
        try {
            descriptor.getKey().getType().convert(value);
        } catch (IllegalArgumentException e) {
            throw abort(String.format("Invalid argument %s specified. %s'", arg, e.getMessage()));
        }
        if (!experimentalOptions && descriptor.getStability() == OptionStability.EXPERIMENTAL) {
            throw abortExperimental(String.format("Option '%s' is experimental and must be enabled via '--experimental-options'%n" +
                            "Do not use experimental options in production environments.", arg));
        }
        // use the full name of the found descriptor
        builder.option(descriptor.getName(), value);
    }

    private OptionDescriptor findOptionDescriptor(String group, String key) {
        OptionDescriptors descriptors = null;
        switch (group) {
            case "engine":
                descriptors = getTempEngine().getOptions();
                break;
            default:
                Engine engine = getTempEngine();
                if (engine.getLanguages().containsKey(group)) {
                    descriptors = engine.getLanguages().get(group).getOptions();
                } else if (engine.getInstruments().containsKey(group)) {
                    descriptors = engine.getInstruments().get(group).getOptions();
                }
                break;
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

        protected PrintableOption(String option, String description) {
            this.option = option;
            this.description = description;
        }

        @Override
        public int compareTo(PrintableOption o) {
            return this.option.compareTo(o.option);
        }
    }

    private static String optionsTitle(String kind, OptionCategory optionCategory) {
        String category;
        switch (optionCategory) {
            case USER:
                category = "User ";
                break;
            case EXPERT:
                category = "Expert ";
                break;
            case INTERNAL:
                category = "Internal ";
                break;
            default:
                category = "";
                break;
        }
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
        if (defaultValue instanceof Boolean && defaultValue == Boolean.FALSE) {
            // nothing to print
        } else {
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

    /**
     * Creates a new log file. The method uses a supplemental lock file to determine the file is
     * still opened for output; in that case, it creates a different file, named `path'1, `path`2,
     * ... until it finds a free name. Files not locked (actively written to) are overwritten.
     *
     * @param path the desired output for log
     * @return the OutputStream for logging
     * @throws IOException in case of I/O error opening the file
     * @since 20.0
     */
    protected static OutputStream newLogStream(Path path) throws IOException {
        Path usedPath = path;
        Path fileNamePath = path.getFileName();
        String fileName = fileNamePath == null ? "" : fileNamePath.toString();
        Path lockFile = null;
        FileChannel lockFileChannel = null;
        for (int unique = 0;; unique++) {
            StringBuilder lockFileNameBuilder = new StringBuilder(fileName);
            if (unique > 0) {
                lockFileNameBuilder.append(unique);
                usedPath = path.resolveSibling(lockFileNameBuilder.toString());
            }
            lockFileNameBuilder.append(".lck");
            lockFile = path.resolveSibling(lockFileNameBuilder.toString());
            Pair<FileChannel, Boolean> openResult = openChannel(lockFile);
            if (openResult != null) {
                lockFileChannel = openResult.getLeft();
                if (lock(lockFileChannel, openResult.getRight())) {
                    break;
                } else {
                    // Close and try next name
                    lockFileChannel.close();
                }
            }
        }
        assert lockFile != null && lockFileChannel != null;
        boolean success = false;
        try {
            OutputStream stream = new LockableOutputStream(
                            new BufferedOutputStream(Files.newOutputStream(usedPath, WRITE, CREATE, APPEND)),
                            lockFile,
                            lockFileChannel);
            success = true;
            return stream;
        } finally {
            if (!success) {
                LockableOutputStream.unlock(lockFile, lockFileChannel);
            }
        }
    }

    private static Pair<FileChannel, Boolean> openChannel(Path path) throws IOException {
        FileChannel channel = null;
        for (int retries = 0; channel == null && retries < 2; retries++) {
            try {
                channel = FileChannel.open(path, CREATE_NEW, WRITE);
                return Pair.create(channel, true);
            } catch (FileAlreadyExistsException faee) {
                // Maybe a FS race showing a zombie file, try to reuse it
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && isParentWritable(path)) {
                    try {
                        channel = FileChannel.open(path, WRITE, APPEND);
                        return Pair.create(channel, false);
                    } catch (NoSuchFileException x) {
                        // FS Race, next try we should be able to create with CREATE_NEW
                    } catch (IOException x) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    private static boolean isParentWritable(Path path) {
        Path parentPath = path.getParent();
        if (parentPath == null && !path.isAbsolute()) {
            parentPath = path.toAbsolutePath().getParent();
        }
        return parentPath != null && Files.isWritable(parentPath);
    }

    private static boolean lock(FileChannel lockFileChannel, boolean newFile) {
        boolean available = false;
        try {
            available = lockFileChannel.tryLock() != null;
        } catch (OverlappingFileLockException ofle) {
            // VM already holds lock continue with available set to false
        } catch (IOException ioe) {
            // Locking not supported by OS
            available = newFile;
        }
        return available;
    }

    private static final class LockableOutputStream extends OutputStream {

        private final OutputStream delegate;
        private final Path lockFile;
        private final FileChannel lockFileChannel;

        LockableOutputStream(OutputStream delegate, Path lockFile, FileChannel lockFileChannel) {
            this.delegate = delegate;
            this.lockFile = lockFile;
            this.lockFileChannel = lockFileChannel;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                unlock(lockFile, lockFileChannel);
            }
        }

        private static void unlock(Path lockFile, FileChannel lockFileChannel) {
            try {
                lockFileChannel.close();
            } catch (IOException ioe) {
                // Error while closing the channel, ignore.
            }
            try {
                Files.delete(lockFile);
            } catch (IOException ioe) {
                // Error while deleting the lock file, ignore.
            }
        }
    }
}
