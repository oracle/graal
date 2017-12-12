/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.VMError;

class NativeImageSupport {
    final String hostedOptionPrefix;
    final String runtimeOptionPrefix;

    final String platform;
    final String svmVersion;
    final String graalvmVersion;
    final String usageText;
    final String helpText;
    final String helpTextX;

    NativeImageSupport() {
        hostedOptionPrefix = "-H:";
        runtimeOptionPrefix = "-R:";

        platform = getPlatform();
        svmVersion = System.getProperty("substratevm.version");
        String tmpGraalVmVersion = System.getProperty("org.graalvm.version");
        if (tmpGraalVmVersion == null) {
            tmpGraalVmVersion = System.getProperty("graalvm.version");
        }
        if (tmpGraalVmVersion == null) {
            throw new RuntimeException("Could not find GraalVM version in graalvm.version or org.graalvm.version");
        }
        graalvmVersion = tmpGraalVmVersion;
        usageText = getResource("/Usage.txt");
        helpText = getResource("/Help.txt");
        helpTextX = getResource("/HelpX.txt");
    }

    private static String getPlatform() {
        if (Platform.includedIn(Platform.DARWIN.class)) {
            return "darwin";
        }
        if (Platform.includedIn(Platform.LINUX.class)) {
            return "linux";
        }
        throw VMError.shouldNotReachHere();
    }

    private static String getResource(String resourceName) {
        try (InputStream input = NativeImage.class.getResourceAsStream(resourceName)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            VMError.shouldNotReachHere(e);
        }
        return null;
    }
}

@AutomaticFeature
class NativeImageFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(NativeImageSupport.class, new NativeImageSupport());
    }
}
