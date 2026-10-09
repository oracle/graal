/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver.launcher.configuration;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.oracle.svm.driver.launcher.json.BundleJSONParser;
import com.oracle.svm.driver.launcher.json.BundleJSONParserException;

public class BundleArgsParser extends BundleConfigurationParser {

    private static final String PLATFORM_FIELD = "platform";
    private static final String ARGUMENTS_FIELD = "args";

    /// A contiguous native-image command-line addition interpreted using its originating platform.
    public record ArgumentGroup(String platform, List<String> arguments) {
        public ArgumentGroup {
            Objects.requireNonNull(platform);
            arguments = List.copyOf(arguments);
        }
    }

    private final List<String> args;

    public BundleArgsParser(List<String> args) {
        this.args = args;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        for (var arg : asList(json, "Expected a list of arguments")) {
            args.add(arg.toString());
        }
    }

    public static List<ArgumentGroup> parseBuildArgumentGroups(Reader reader, String legacyPlatform, boolean legacyFormat) throws IOException {
        Object json = new BundleJSONParser(reader).parse();
        if (legacyFormat) {
            ArrayList<String> arguments = new ArrayList<>();
            new BundleArgsParser(arguments).parseAndRegister(json, null);
            return List.of(new ArgumentGroup(legacyPlatform, arguments));
        }

        ArrayList<ArgumentGroup> groups = new ArrayList<>();
        for (Object rawGroup : asList(json, "Expected a list of argument groups")) {
            Map<String, Object> group = asMap(rawGroup, "Expected an argument group object");
            Object rawPlatform = group.get(PLATFORM_FIELD);
            if (rawPlatform == null) {
                throw new BundleJSONParserException("Expected " + PLATFORM_FIELD + "-field in argument group object");
            }
            Object rawArguments = group.get(ARGUMENTS_FIELD);
            if (rawArguments == null) {
                throw new BundleJSONParserException("Expected " + ARGUMENTS_FIELD + "-field in argument group object");
            }
            ArrayList<String> arguments = new ArrayList<>();
            new BundleArgsParser(arguments).parseAndRegister(rawArguments, null);
            groups.add(new ArgumentGroup(rawPlatform.toString(), arguments));
        }
        return List.copyOf(groups);
    }
}
