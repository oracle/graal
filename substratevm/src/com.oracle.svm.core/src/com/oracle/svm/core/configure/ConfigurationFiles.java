/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.configure;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionStability;
import org.graalvm.compiler.options.OptionType;

import com.oracle.svm.core.option.BundleMember;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.util.UserError;

/**
 * Gathers configuration files from specified directories without having to provide each
 * configuration file individually.
 */
public final class ConfigurationFiles {

    public static final class Options {
        @Option(help = "Directories directly containing configuration files for dynamic features at runtime.", type = OptionType.User, stability = OptionStability.STABLE)//
        @BundleMember(role = BundleMember.Role.Input)//
        static final HostedOptionKey<LocatableMultiOptionValue.Paths> ConfigurationFileDirectories = new HostedOptionKey<>(LocatableMultiOptionValue.Paths.buildWithCommaDelimiter());

        @Option(help = "Resource path above configuration resources for dynamic features at runtime.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ConfigurationResourceRoots = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        @Option(help = "file:doc-files/ReflectionConfigurationFilesHelp.txt", type = OptionType.User)//
        @BundleMember(role = BundleMember.Role.Input)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Paths> ReflectionConfigurationFiles = new HostedOptionKey<>(LocatableMultiOptionValue.Paths.buildWithCommaDelimiter());
        @Option(help = "Resources describing program elements to be made available for reflection (see ReflectionConfigurationFiles).", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ReflectionConfigurationResources = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        @Option(help = "file:doc-files/ProxyConfigurationFilesHelp.txt", type = OptionType.User)//
        @BundleMember(role = BundleMember.Role.Input)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Paths> DynamicProxyConfigurationFiles = new HostedOptionKey<>(LocatableMultiOptionValue.Paths.buildWithCommaDelimiter());
        @Option(help = "Resources describing program elements to be made available for reflection (see ProxyConfigurationFiles).", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> DynamicProxyConfigurationResources = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        @Option(help = "file:doc-files/SerializationConfigurationFilesHelp.txt", type = OptionType.User)//
        @BundleMember(role = BundleMember.Role.Input)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Paths> SerializationConfigurationFiles = new HostedOptionKey<>(LocatableMultiOptionValue.Paths.buildWithCommaDelimiter());
        @Option(help = "Resources describing program elements to be made available for serialization (see SerializationConfigurationFiles).", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> SerializationConfigurationResources = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        @Option(help = "file:doc-files/SerializationConfigurationFilesHelp.txt", type = OptionType.User)//
        @BundleMember(role = BundleMember.Role.Input)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Paths> SerializationDenyConfigurationFiles = new HostedOptionKey<>(LocatableMultiOptionValue.Paths.buildWithCommaDelimiter());
        @Option(help = "Resources describing program elements that must not be made available for serialization.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> SerializationDenyConfigurationResources = new HostedOptionKey<>(
                        LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        @Option(help = "Files describing Java resources to be included in the image according to the schema at " +
                        "https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/resource-config-schema-v1.0.0.json", type = OptionType.User)//
        @BundleMember(role = BundleMember.Role.Input)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Paths> ResourceConfigurationFiles = new HostedOptionKey<>(LocatableMultiOptionValue.Paths.buildWithCommaDelimiter());
        @Option(help = "Resources describing Java resources to be included in the image according to the schema at " +
                        "https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/resource-config-schema-v1.0.0.json", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ResourceConfigurationResources = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        @Option(help = "Files describing program elements to be made accessible via JNI (for syntax, see ReflectionConfigurationFiles)", type = OptionType.User)//
        @BundleMember(role = BundleMember.Role.Input)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Paths> JNIConfigurationFiles = new HostedOptionKey<>(LocatableMultiOptionValue.Paths.buildWithCommaDelimiter());
        @Option(help = "Resources describing program elements to be made accessible via JNI (see JNIConfigurationFiles).", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> JNIConfigurationResources = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        @Option(help = "Resources describing reachability metadata needed for the program " +
                        "https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/reachability-metadata-schema-v1.0.0.json", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ReachabilityMetadataResources = new HostedOptionKey<>(
                        LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        @Option(help = "Files describing stubs allowing foreign calls.", type = OptionType.User)//
        @BundleMember(role = BundleMember.Role.Input)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Paths> ForeignConfigurationFiles = new HostedOptionKey<>(LocatableMultiOptionValue.Paths.buildWithCommaDelimiter());
        @Option(help = "Resources describing stubs allowing foreign calls.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ForeignResources = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        @Option(help = "Files describing predefined classes that can be loaded at runtime according to the schema at " +
                        "https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/predefined-classes-config-schema-v1.0.0.json", type = OptionType.User)//
        @BundleMember(role = BundleMember.Role.Input)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Paths> PredefinedClassesConfigurationFiles = new HostedOptionKey<>(LocatableMultiOptionValue.Paths.buildWithCommaDelimiter());
        @Option(help = "Resources describing predefined classes that can be loaded at runtime according to the schema at " +
                        "https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/predefined-classes-config-schema-v1.0.0.json", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> PredefinedClassesConfigurationResources = new HostedOptionKey<>(
                        LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        @Option(help = "When configuration files do not match their schema, abort the image build instead of emitting a warning.")//
        public static final HostedOptionKey<Boolean> StrictConfiguration = new HostedOptionKey<>(false);

        @Option(help = "Warn when reflection and JNI configuration files have elements that could not be found on the classpath or modulepath.", type = OptionType.Expert)//
        public static final HostedOptionKey<Boolean> WarnAboutMissingReflectionOrJNIMetadataElements = new HostedOptionKey<>(false);
    }

    public static List<Path> findConfigurationFiles(String fileName) {
        List<Path> files = new ArrayList<>();
        for (Path configDir : Options.ConfigurationFileDirectories.getValue().values()) {
            if (Files.exists(configDir.resolve(ConfigurationFile.LOCK_FILE_NAME))) {
                throw foundLockFile("Configuration file directory '" + configDir + "'");
            }
            Path path = configDir.resolve(fileName);
            if (Files.exists(path)) {
                files.add(path);
            }
        }
        return files;
    }

    public static List<URL> findConfigurationResources(String fileName, ClassLoader classLoader) {
        List<URL> resources = new ArrayList<>();
        for (String root : Options.ConfigurationResourceRoots.getValue().values()) {
            /*
             * Resource path handling is cumbersome: we want users to be able to pass "/" or "." for
             * the classpath root, but only relative paths without "." are permitted, so we strip
             * these first. Redundant separators also do not work, so we change "root//fileName" to
             * "root/fileName".
             */
            final String separator = "/"; // always for resources (not platform-dependent)
            String relativeRoot = Stream.of(root.split(separator)).filter(part -> !part.isEmpty() && !part.equals(".")).collect(Collectors.joining(separator));
            try {
                String lockPath = relativeRoot.isEmpty() ? ConfigurationFile.LOCK_FILE_NAME
                                : (relativeRoot + '/' + ConfigurationFile.LOCK_FILE_NAME);
                Enumeration<URL> resource = classLoader.getResources(lockPath);
                if (resource != null && resource.hasMoreElements()) {
                    throw foundLockFile("Configuration resource root '" + root + "'");
                }
            } catch (IOException ignored) {
            }
            String relativePath = relativeRoot.isEmpty() ? fileName : (relativeRoot + '/' + fileName);
            try {
                for (Enumeration<URL> e = classLoader.getResources(relativePath); e.hasMoreElements();) {
                    resources.add(e.nextElement());
                }
            } catch (IOException e) {
                throw UserError.abort(e, "Error while looking for %s in %s", fileName, root);
            }
        }
        return resources;
    }

    private static UserError.UserException foundLockFile(String container) {
        throw UserError.abort("%s contains file '%s', which means an agent is currently writing to it." +
                        "The agent must finish execution before its generated configuration can be used to build a native image." +
                        "Unless the lock file is a leftover from an earlier process that terminated abruptly, it is unsafe to delete it.",
                        container, ConfigurationFile.LOCK_FILE_NAME);
    }
}
