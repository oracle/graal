/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.configure.command;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import com.oracle.svm.configure.ConfigurationUsageException;

public abstract class ConfigurationCommand {
    protected static final int VALUE_INDEX = 1;
    protected static final int OPTION_INDEX = 0;
    protected static final int OPTION_VALUE_LENGTH = 2;
    protected static final String OPTION_VALUE_SEP = "=";

    protected static final String BAD_OPTION_FORMAT = "Format is not valid: %s. " +
                    "Options should be in format --<option>=<value>. ";

    public abstract String getName();

    public abstract void apply(Iterator<String> argumentsIterator) throws IOException;

    public String getUsage() {
        return "native-image-configure " + getName() + " [options]";
    }

    public final String getDescription() {
        return getName() + getDescription0();
    }

    protected abstract String getDescription0();

    protected static Path requirePath(String current, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new ConfigurationUsageException("Argument must be provided for: " + current);
        }
        return Paths.get(value);
    }

    protected static URI requirePathUri(String current, String value) {
        return requirePath(current, value).toUri();
    }

    protected static Path getOrCreateDirectory(String current, String value) throws IOException {
        Path directory = requirePath(current, value);
        if (!Files.exists(directory)) {
            Files.createDirectory(directory);
        } else if (!Files.isDirectory(directory)) {
            throw new NoSuchFileException(value);
        }
        return directory;
    }

    protected static Path getOrCreateFile(String option, String value) throws IOException {
        Path file = requirePath(option, value);
        if (!Files.exists(file)) {
            Files.createFile(file);
        } else {
            if (Files.isDirectory(file)) {
                throw new ConfigurationUsageException(option + " expects a path to a file, but directory path is given: " + value + ". Please use a path to a file.");
            }
        }
        return file;
    }
}
