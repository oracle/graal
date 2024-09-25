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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.graalvm.nativeimage.impl.ReflectionRegistry;
import org.graalvm.util.json.JSONParserException;

import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.configure.ReflectionConfigurationParser;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;

public final class ConfigurationParserUtils {

    public static ReflectionConfigurationParser<Class<?>> create(String combinedFileKey, boolean strictMetadata, ReflectionRegistry registry, ImageClassLoader imageClassLoader) {
        return ReflectionConfigurationParser.create(combinedFileKey, strictMetadata, RegistryAdapter.create(registry, imageClassLoader),
                        ConfigurationFiles.Options.StrictConfiguration.getValue(), ConfigurationFiles.Options.WarnAboutMissingReflectionOrJNIMetadataElements.getValue());
    }

    /**
     * Parses configurations in files specified by {@code configFilesOption} and resources specified
     * by {@code configResourcesOption} and registers the parsed elements using
     * {@link ConfigurationParser#parseAndRegister} .
     *
     * @param featureName name of the feature using the configuration (e.g., "JNI")
     * @param directoryFileName file name for searches via {@link ConfigurationFiles}.
     * @return the total number of successfully parsed configuration files and resources.
     */
    public static int parseAndRegisterConfigurations(ConfigurationParser parser, ImageClassLoader classLoader, String featureName,
                    HostedOptionKey<LocatableMultiOptionValue.Paths> configFilesOption, HostedOptionKey<LocatableMultiOptionValue.Strings> configResourcesOption, String directoryFileName) {

        List<Path> paths = configFilesOption.getValue().values();
        List<String> resourceValues = configResourcesOption.getValue().values();
        return parseAndRegisterConfigurations(parser, classLoader, featureName, directoryFileName, paths, resourceValues);
    }

    public static int parseAndRegisterConfigurationsFromCombinedFile(ConfigurationParser parser, ImageClassLoader classLoader, String featureName) {
        return parseAndRegisterConfigurations(parser, classLoader, featureName, ConfigurationFile.REACHABILITY_METADATA.getFileName(), Collections.emptyList(), Collections.emptyList());
    }

    public static int parseAndRegisterConfigurations(ConfigurationParser parser, ImageClassLoader classLoader,
                    String featureName,
                    String directoryFileName, List<Path> paths,
                    List<String> resourceValues) {
        int parsedCount = 0;
        Stream<Path> files = Stream.concat(paths.stream(),
                        ConfigurationFiles.findConfigurationFiles(directoryFileName).stream());
        parsedCount += files.map(Path::toAbsolutePath).mapToInt(path -> {
            if (!Files.exists(path)) {
                throw UserError.abort("The %s configuration file \"%s\" does not exist.", featureName, path);
            }
            doParseAndRegister(parser, featureName, path);
            return 1;
        }).sum();

        Stream<URL> configResourcesFromOption = resourceValues.stream().flatMap(s -> {
            Enumeration<URL> urls;
            try {
                urls = classLoader.findResourcesByName(s);
            } catch (IOException e) {
                throw UserError.abort(e, "Error while looking for %s in %s", s, classLoader);
            }
            if (!urls.hasMoreElements()) {
                throw UserError.abort("Could not find %s configuration resource \"%s\".", featureName, s);
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
            doParseAndRegister(parser, featureName, url);
            return 1;
        }).sum();
        return parsedCount;
    }

    private static void doParseAndRegister(ConfigurationParser parser, String featureName, Object location) {
        try {
            URI uri;
            if (location instanceof Path) {
                uri = ((Path) location).toUri();
            } else {
                uri = ((URL) location).toURI();
            }
            parser.parseAndRegister(uri);
        } catch (IOException | URISyntaxException | JSONParserException e) {
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = e.toString();
            }
            throw UserError.abort(
                            "Error parsing %s configuration in %s:%n%s%nVerify that the configuration matches the corresponding schema at " +
                                            "https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/",
                            featureName, location, errorMessage);
        }
    }
}
