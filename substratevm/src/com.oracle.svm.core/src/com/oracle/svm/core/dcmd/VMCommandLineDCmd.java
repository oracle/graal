/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.dcmd;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.util.BasedOnJDKFile;

@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+27/src/hotspot/share/services/diagnosticCommand.hpp#L70-L81")
public class VMCommandLineDCmd extends AbstractDCmd {
    @Platforms(Platform.HOSTED_ONLY.class)
    public VMCommandLineDCmd() {
        super("VM.command_line", "Print the command line used to start this VM instance.", Impact.Low);
    }

    @Override
    public String execute(DCmdArguments args) throws Throwable {
        String lineBreak = System.lineSeparator();
        StringBuilder result = new StringBuilder("VM Arguments:");

        String[] mainArgs = ImageSingletons.lookup(JavaMainSupport.class).mainArgs;
        if (mainArgs != null) {
            result.append(lineBreak);
            result.append("java_command: ");
            for (String arg : mainArgs) {
                result.append(arg).append(" ");
            }
        }
        return result.toString();
    }
}
