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

public class OptionHandlerJavascript extends TruffleOptionHandler {

    @Override
    public String getInfo() {
        return "build truffle-based javascript (graal-js) image";
    }

    @Override
    String getTruffleLanguageName() {
        return "Graal.js";
    }

    @Override
    String getTruffleLanguageOptionName() {
        return "js";
    }

    @Override
    String getLauncherClass() {
        return "com.oracle.truffle.js.shell.JSLauncher";
    }

    @Override
    public void applyTruffleLanguageOptions(ImageBuilderConfig config, Path... truffleLanguageJars) {
        config.addImageClasspath(truffleLanguageJars);
        config.addImageBuilderJavaArgs(
                        "-Dtruffle.js.SubstrateVM=true",
                        "-Dtruffle.js.PrepareFirstContext=true");
        config.addImageBuilderArgs(
                        "-R:YoungGenerationSize=1g",
                        "-R:OldGenerationSize=3g");
    }

    @Override
    void applyTruffleLanguageShellOptions(ImageBuilderConfig config) {
        config.addImageBuilderJavaArgs("-Xmx3G");
        config.addImageBuilderArgs("-H:Name=js");
    }
}
