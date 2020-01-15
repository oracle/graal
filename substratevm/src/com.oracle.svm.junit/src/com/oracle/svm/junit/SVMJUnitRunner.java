/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.junit;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.FeatureAccess;
import org.junit.internal.JUnitSystem;
import org.junit.internal.RealSystem;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import com.oracle.mxtool.junit.MxJUnitRequest;
import com.oracle.mxtool.junit.MxJUnitWrapper;
import com.oracle.mxtool.junit.MxJUnitWrapper.MxJUnitConfig;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;

import junit.runner.Version;

public class SVMJUnitRunner {

    public static class Options {
        @Option(help = "") //
        public static final HostedOptionKey<String> TestFile = new HostedOptionKey<>("");
    }

    private final MxJUnitRequest request;
    private final String missingClassesStr;

    @Platforms(Platform.HOSTED_ONLY.class)
    SVMJUnitRunner(FeatureAccess access) {
        MxJUnitRequest.Builder builder = new MxJUnitRequest.Builder() {

            @Override
            protected Class<?> resolveClass(String name) throws ClassNotFoundException {
                Class<?> ret = access.findClassByName(name);
                if (ret == null) {
                    throw new ClassNotFoundException(name);
                }
                return ret;
            }
        };

        try (BufferedReader br = new BufferedReader(new FileReader(Options.TestFile.getValue()))) {
            String buf;
            while ((buf = br.readLine()) != null) {
                builder.addTestSpec(buf);
            }
        } catch (Exception ex) {
            throw VMError.shouldNotReachHere(ex);
        }

        request = builder.build();

        missingClassesStr = getMissingClasses();
        if (missingClassesStr != null) {
            String testFileOption = SubstrateOptionsParser.commandArgument(Options.TestFile, Options.TestFile.getValue());
            StringBuilder msg = new StringBuilder("Warning: The test configuration file specified via ").append(testFileOption)
                            .append(" contains missing classes. Test execution will fail at run time. ")
                            .append("Missing classes in configuration file: ").append(missingClassesStr);
            // Checkstyle: stop
            System.out.println(msg);
            // Checkstyle: resume
        }
    }

    Request getJUnitRequest() {
        return request.getRequest();
    }

    /* Get a comma separated list of missing classes as reported by the request object. */
    private String getMissingClasses() {
        List<Failure> missingClasses = request.getMissingClasses();
        if (missingClasses.size() > 0) {
            StringBuilder missingClassesBuilder = new StringBuilder();
            String delim = "";
            for (Failure missingClass : missingClasses) {
                missingClassesBuilder.append(delim).append(missingClass.getDescription().getDisplayName());
                delim = ", ";
            }
            return missingClassesBuilder.toString();
        }
        return null;
    }

    private void run(String[] args) {
        JUnitSystem system = new RealSystem();
        JUnitCore junitCore = new JUnitCore();
        system.out().println("SVMJUnitCore");
        system.out().println("JUnit version " + Version.id());

        MxJUnitConfig config = new MxJUnitConfig();

        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            switch (arg) {
                case "--verbose":
                    config.verbose = true;
                    break;
                case "--very-verbose":
                    config.veryVerbose = true;
                    break;
                case "--fail-fast":
                    config.failFast = true;
                    break;
                case "--enable-timing":
                    config.enableTiming = true;
                    break;
                case "--color":
                    config.color = true;
                    break;
                case "--gc-after-test":
                    config.gcAfterTest = true;
                    break;
                case "--repeat":
                    if (i < args.length) {
                        String count = args[i++];
                        try {
                            config.repeatCount = Integer.valueOf(count);
                        } catch (NumberFormatException ex) {
                            system.out().println("Invalid argument to --repeat, expected number, but got " + count);
                        }
                    } else {
                        system.out().println("Missing argument to --repeat");
                    }
                    break;
                case "--eager-stacktrace":
                    config.eagerStackTrace = true;
                    break;
                default:
                    system.out().println("Unknown command line argument: " + arg);
                    break;
            }
        }

        Result result = MxJUnitWrapper.runRequest(junitCore, system, config, request);

        if (result.wasSuccessful()) {
            system.out().println("Test run PASSED. Exiting with status 0.");
            System.exit(0);
        } else {
            StringBuilder msg = new StringBuilder("Test run FAILED!");
            if (missingClassesStr != null) {
                msg.append(System.lineSeparator());
                msg.append("Missing classes in configuration file: ").append(missingClassesStr);
                msg.append(System.lineSeparator());
            }
            msg.append("Exiting with status 1.");
            system.out().println(msg);
            System.exit(1);
        }

    }

    public static void main(String[] args) {
        ImageSingletons.lookup(SVMJUnitRunner.class).run(args);
    }
}
