/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.maven.downloader;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

public class Main {

    static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    static final String LOGGING_PROP = "org.graalvm.maven.downloader.logLevel";
    static {
        String logLevel = System.getenv(LOGGING_PROP);
        if (logLevel != null) {
            Level level = Level.parse(logLevel.toUpperCase());
            LOGGER.setLevel(level);
            Handler[] handlers = Logger.getLogger("").getHandlers();
            for (Handler h : handlers) {
                h.setLevel(level);
            }
        }
    }

    static record Arguments(String repoUrl, String outputDir, String groupId, String artifactId, String version) {
    }

    static Arguments parseArguments(String... args) {
        if (args.length == 0 || "--help".equals(args[0])) {
            printHelp();
            System.exit(0);
        }

        String repoUrl = null;
        String outputDir = null;
        String groupId = null;
        String artifactId = null;
        String version = null;

        for (int i = 0; i < args.length; i++) {
            if ('-' == args[i].charAt(0)) {
                if (args[i].length() < 2) {
                    exitErr("Invalid argument: " + args[i]);
                }
                if (args.length - 1 == i) {
                    exitErr("Expected argument value after: " + args[i]);
                }
                switch (args[i].charAt(1)) {
                    case 'r':
                        repoUrl = args[i + 1];
                        break;
                    case 'o':
                        outputDir = args[i + 1];
                        break;
                    case 'g':
                        groupId = args[i + 1];
                        break;
                    case 'a':
                        artifactId = args[i + 1];
                        break;
                    case 'v':
                        version = args[i + 1];
                        break;
                    default:
                        exitErr("Invalid argument: " + args[i]);
                }
                i++;
            } else if (args.length == 1) {
                artifactId = args[i];
            } else {
                exitErr("Invalid argument: " + args[i]);
            }
        }

        if (repoUrl == null) {
            repoUrl = OptionProperties.getDefaultRepo();
        } else if (!repoUrl.endsWith("/")) {
            repoUrl = repoUrl + "/";
        }
        if (outputDir == null) {
            outputDir = OptionProperties.getDefaultOutputDir();
        }
        if (groupId == null) {
            groupId = OptionProperties.getDefaultGroup();
        }
        if (artifactId == null) {
            exitErr("Missing artifactId argument -a");
        }
        if (version == null) {
            version = OptionProperties.getDefaultVersion();
        }

        return new Arguments(repoUrl, outputDir, groupId, artifactId, version);
    }

    public static void main(String... args) throws IOException, URISyntaxException, ClassCastException, ParserConfigurationException, SAXException, NoSuchAlgorithmException {
        Arguments parsedArgs = parseArguments(args);

        if (parsedArgs.outputDir == null) {
            exitErr("Missing output directory argument -o");
        }
        Path outputPath = Paths.get(parsedArgs.outputDir);
        if (!Files.exists(outputPath)) {
            Files.createDirectory(outputPath);
        } else {
            if (!Files.isDirectory(outputPath)) {
                exitErr("Output folder has to be a directory.");
            }
        }

        LOGGER.info(String.format("downloading %s:%s:%s from %s to %s", parsedArgs.groupId, parsedArgs.artifactId, parsedArgs.version, parsedArgs.repoUrl, parsedArgs.outputDir));

        new MVNDownloader(parsedArgs.outputDir).downloadDependencies(parsedArgs.repoUrl, parsedArgs.groupId, parsedArgs.artifactId, parsedArgs.version);
    }

    private static void exitErr(String err) {
        System.err.println(err + "\n");
        printHelp();
        System.exit(1);
    }

    private static void printHelp() {
        System.out.println(String.format("""
                        Usage: %s [OPTION...]

                        This tool downloads maven artifacts and their dependencies to a single directory.

                        There are two operating modes.

                        Specifying everything needed for download and resolution:
                          -r maven-repository-url [default: %s]
                          -o output-directory [default: %s]
                          -g group-id [default: %s]
                          -v artifact-version [default: %s]
                          -a artifact-id

                        Passing just a single argument:
                          This is treated as an artifact ID. The other options then use their defaults.


                        Environment options:
                          'http_proxy' and 'https_proxy' are observed for proxy configuration
                          %s can be used to set the default version
                          %s is observed to configure the default maven repo
                          %s is observed to configure logging level
                        """,
                        OptionProperties.getExeName(),
                        OptionProperties.getDefaultRepo(),
                        OptionProperties.getDefaultOutputDir(),
                        OptionProperties.getDefaultGroup(),
                        OptionProperties.getDefaultVersion(),
                        OptionProperties.VERSION_PROP,
                        OptionProperties.MAVEN_PROP,
                        LOGGING_PROP));
    }
}
