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
package com.oracle.svm.hosted.config;

import static com.oracle.svm.core.SubstrateOptions.PrintFlags;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.graalvm.nativeimage.impl.ReflectionRegistry;

import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.configure.ReflectionConfigurationParser;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.json.JSONParserException;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageOptions;

public final class ConfigurationParserUtils {

    public static ReflectionConfigurationParser<Class<?>> create(ReflectionRegistry registry, ImageClassLoader imageClassLoader) {
        return new ReflectionConfigurationParser<>(new ReflectionRegistryAdapter(registry, imageClassLoader), NativeImageOptions.AllowIncompleteClasspath.getValue());
    }

    /**
     * Parses configurations in files specified by {@code configFilesOption} and resources specified
     * by {@code configResourcesOption} and registers the parsed elements using
     * {@link ConfigurationParser#parseAndRegister(Reader)} .
     *
     * @param featureName name of the feature using the configuration (e.g., "JNI")
     * @param directoryFileName file name for searches via {@link ConfigurationFiles}.
     * @return the total number of successfully parsed configuration files and resources.
     */
    public static int parseAndRegisterConfigurations(ConfigurationParser parser, ImageClassLoader classLoader, String featureName,
                    HostedOptionKey<String[]> configFilesOption, HostedOptionKey<String[]> configResourcesOption, String directoryFileName) {

        int parsedCount = 0;

        Stream<Path> files = Stream.concat(OptionUtils.flatten(",", configFilesOption.getValue()).stream().map(Paths::get),
                        ConfigurationFiles.findConfigurationFiles(directoryFileName).stream());
        parsedCount += files.map(Path::toAbsolutePath).mapToInt(path -> {
            if (!Files.exists(path)) {
                throw UserError.abort("The " + featureName + " configuration file \"" + path + "\" does not exist.");
            }
            try (Reader reader = new FileReader(path.toFile())) {
                doParseAndRegister(parser, reader, featureName, path, configFilesOption);
                return 1;
            } catch (IOException e) {
                throw UserError.abort("Could not open " + path + ": " + e.getMessage());
            }
        }).sum();

        Stream<URL> configResourcesFromOption = OptionUtils.flatten(",", configResourcesOption.getValue()).stream().flatMap(s -> {
            Enumeration<URL> urls;
            try {
                urls = classLoader.findResourcesByName(s);
            } catch (IOException e) {
                throw UserError.abort(e, "Error while looking for " + s + " in " + classLoader);
            }
            if (!urls.hasMoreElements()) {
                throw UserError.abort("Could not find " + featureName + " configuration resource \"" + s + "\".");
            }
            return StreamSupport.stream(new AbstractSpliterator<URL>(Long.MAX_VALUE, Spliterator.ORDERED) {
                @Override
                public boolean tryAdvance(Consumer<? super URL> action) {
                    if (!urls.hasMoreElements()) {
                        return false;
                    }
                    action.accept(urls.nextElement());
                    return true;
                }
            }, false);
        });
        Stream<URL> resources = Stream.concat(configResourcesFromOption, ConfigurationFiles.findConfigurationResources(directoryFileName, classLoader.getClassLoader()).stream());
        parsedCount += resources.mapToInt(url -> {
            try {
                URLConnection urlConnection = url.openConnection();
                urlConnection.setUseCaches(false);
                try (Reader reader = new InputStreamReader(urlConnection.getInputStream())) {
                    doParseAndRegister(parser, reader, featureName, url, configResourcesOption);
                    return 1;
                }
            } catch (IOException e) {
                throw UserError.abort("Could not open " + url + ": " + e.getMessage());
            }
        }).sum();

        return parsedCount;
    }

    private static void doParseAndRegister(ConfigurationParser parser, Reader reader, String featureName, Object location, HostedOptionKey<String[]> option) {
        try {
            parser.parseAndRegister(reader);
        } catch (IOException | JSONParserException e) {
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = e.toString();
            }
            throw UserError.abort("Error parsing " + featureName + " configuration in " + location + ":\n" + errorMessage +
                            "\nVerify that the configuration matches the schema described in the " +
                            SubstrateOptionsParser.commandArgument(PrintFlags, "+") + " output for option " + option.getName() + ".");
        }
    }
}
