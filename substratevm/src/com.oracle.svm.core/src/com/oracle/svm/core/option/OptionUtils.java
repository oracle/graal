/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.compiler.options.OptionKey;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.UserError;

/**
 * This class contains static helper methods related to options.
 */
public class OptionUtils {

    /**
     * Utility for string option values that are a, e.g., comma-separated list, but can also be
     * provided multiple times on the command line (so the option type is
     * LocatableMultiOptionValue.Strings). The returned list contains all {@link String#trim()
     * trimmed} string parts, with empty strings filtered out.
     */
    public static List<String> flatten(String delimiter, LocatableMultiOptionValue.Strings values) {
        return flatten(delimiter, values.values());
    }

    public static List<String> flatten(String delimiter, String[] values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return flatten(delimiter, Arrays.asList(values));
    }

    public static List<String> flatten(String delimiter, List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                for (String component : SubstrateUtil.split(value, delimiter)) {
                    String trimmed = component.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
            }
        }
        return result;
    }

    public static List<String> resolveOptionValueRedirection(OptionKey<?> option, String optionValue, OptionOrigin origin) {
        return Arrays.asList(SubstrateUtil.split(optionValue, ",")).stream()
                        .flatMap(entry -> {
                            try {
                                return resolveOptionValueRedirectionFlatMap(entry, origin);
                            } catch (IOException e) {
                                throw UserError.abort(e, "Option '%s' from %s contains invalid option value redirection.",
                                                SubstrateOptionsParser.commandArgument(option, optionValue), origin);
                            }
                        })
                        .collect(Collectors.toList());
    }

    private static Stream<? extends String> resolveOptionValueRedirectionFlatMap(String entry, OptionOrigin origin) throws IOException {
        if (entry.trim().startsWith("@")) {
            URI jarFileURI = URI.create("jar:" + origin.container());
            FileSystem probeJarFS;
            try {
                probeJarFS = FileSystems.newFileSystem(jarFileURI, Collections.emptyMap());
            } catch (UnsupportedOperationException e) {
                throw new FileNotFoundException();
            }
            if (probeJarFS != null) {
                try (FileSystem jarFS = probeJarFS) {
                    var normalizedRedirPath = origin.location().getParent().resolve(entry.substring(1)).normalize();
                    var pathInJarFS = jarFS.getPath(normalizedRedirPath.toString());
                    if (Files.exists(pathInJarFS)) {
                        return Files.readAllLines(pathInJarFS).stream();
                    }
                    throw new FileNotFoundException(pathInJarFS.toString());
                }
            }
            throw new FileNotFoundException(jarFileURI.toString());
        } else {
            return Stream.of(entry);
        }
    }
}
