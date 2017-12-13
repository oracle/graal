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

import java.nio.file.Path;
import java.nio.file.Paths;

import com.oracle.svm.truffle.nfi.TruffleNFIFeature;

public class OptionHandlerRuby extends TruffleOptionHandler {

    @Override
    public String getInfo() {
        return "build truffle-based ruby (TruffleRuby) image";
    }

    @Override
    String getTruffleLanguageName() {
        return "TruffleRuby";
    }

    @Override
    String getTruffleLanguageOptionName() {
        return "ruby";
    }

    @Override
    String getLauncherClass() {
        return null;
    }

    @Override
    public void applyTruffleLanguageOptions(ImageBuilderConfig config, Path... truffleLanguageJars) {
        config.addImageClasspath(truffleLanguageJars);
        if (config.isRelease()) {
            /* We need JLINE from graalvm.jar on the ImageBuilderClasspath */
            Path graalvmDir = config.getRootDir().resolve(Paths.get("jre", "lib", "graalvm"));
            config.addImageBuilderClasspath(graalvmDir.resolve("jline.jar"));
        }
        config.addImageBuilderFeatures(TruffleNFIFeature.class.getName());
        config.addImageBuilderArgs(
                        "-R:YoungGenerationSize=1g",
                        "-R:OldGenerationSize=2g",
                        "-H:+AddAllCharsets");

        config.addImageBuilderSubstitutions("org/truffleruby/aot/substitutions.json");

        Path truffleDir = config.getRootDir().resolve("jre/lib/truffle");
        config.addImageBuilderClasspath(truffleDir.resolve("truffle-nfi.jar"));
    }

    @Override
    void applyTruffleLanguageShellOptions(ImageBuilderConfig config) {
        config.addImageBuilderJavaArgs("-Xmx6G");
        config.addImageBuilderArgs(
                        "-H:Class=org.truffleruby.Main",
                        "-H:Name=ruby");
    }
}
