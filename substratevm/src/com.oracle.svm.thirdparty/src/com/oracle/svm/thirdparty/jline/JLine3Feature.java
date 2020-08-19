/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (c) 2002-2020, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package com.oracle.svm.thirdparty.jline;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.Resources;

@AutomaticFeature
final class JLine3Feature implements Feature {

    private static final List<String> RESOURCES = Arrays.asList(
                    "capabilities.txt",
                    "colors.txt",
                    "ansi.caps",
                    "dumb.caps",
                    "dumb-color.caps",
                    "screen.caps",
                    "screen-256color.caps",
                    "windows.caps",
                    "windows-256color.caps",
                    "windows-conemu.caps",
                    "windows-vtp.caps",
                    "xterm.caps",
                    "xterm-256color.caps");
    private static final String RESOURCE_PATH = "org/graalvm/shadowed/org/jline/utils/";
    private static final String JNA_SUPPORT_IMPL = "org.graalvm.shadowed.org.jline.terminal.impl.jna.JnaSupportImpl";

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return access.findClassByName(JNA_SUPPORT_IMPL) != null;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        for (String resource : RESOURCES) {
            String resourcePath = RESOURCE_PATH + resource;
            final InputStream resourceAsStream = ClassLoader.getSystemResourceAsStream(resourcePath);
            Resources.registerResource(resourcePath, resourceAsStream);
        }
    }
}
