/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.maven.downloader;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
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
            Level level = Level.parse(logLevel.toUpperCase(Locale.ROOT));
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
        }
        if (!repoUrl.endsWith("/")) {
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
