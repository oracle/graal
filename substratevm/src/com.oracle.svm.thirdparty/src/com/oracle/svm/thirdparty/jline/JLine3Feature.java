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
package com.oracle.svm.thirdparty.jline;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
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
    private static final String RESOURCE_PATH = "org/jline/utils/";
    private static final String JNA_SUPPORT_IMPL = "org.jline.terminal.impl.jna.JnaSupportImpl";

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        for (String resource : RESOURCES) {
            String resourcePath = RESOURCE_PATH + resource;
            final InputStream resourceAsStream = ClassLoader.getSystemResourceAsStream(resourcePath);
            Resources.registerResource(resourcePath, resourceAsStream);
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return access.findClassByName(JNA_SUPPORT_IMPL) != null;
    }

    static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(JLine3Feature.class);
        }
    }
}

@TargetClass(className = "org.jline.terminal.spi.Pty", onlyWith = com.oracle.svm.thirdparty.jline.JLine3Feature.IsEnabled.class)
final class Target_org_jline_terminal_spi_Pty {
}

@TargetClass(className = "org.jline.terminal.Attributes", onlyWith = com.oracle.svm.thirdparty.jline.JLine3Feature.IsEnabled.class)
final class Target_org_jline_terminal_Attributes {
}

@TargetClass(className = "org.jline.terminal.Size", onlyWith = com.oracle.svm.thirdparty.jline.JLine3Feature.IsEnabled.class)
final class Target_org_jline_terminal_Size {
}

@TargetClass(className = "org.jline.terminal.impl.jna.JnaSupportImpl", onlyWith = com.oracle.svm.thirdparty.jline.JLine3Feature.IsEnabled.class)
final class Target_org_jline_terminal_impl_jna_JnaSupportImpl_open {

    @Substitute
    public Target_org_jline_terminal_spi_Pty open(Target_org_jline_terminal_Attributes attributes, Target_org_jline_terminal_Size size) throws IOException {
        throw new RuntimeException();
    }

    @Substitute
    public Target_org_jline_terminal_spi_Pty current() throws IOException {
        throw new RuntimeException();
    }
}

@TargetClass(className = "org.jline.builtins.Nano", onlyWith = com.oracle.svm.thirdparty.jline.JLine3Feature.IsEnabled.class, innerClass = "Buffer")
final class Target_org_jline_builtins_Nano_Buffer {

    @Alias List<String> lines;
    @Alias private Charset charset;

    @Substitute
    void read(InputStream fis) throws IOException {
        // TODO: Licence? This is a copy of the jline method
        // https://github.com/jline/jline3/blob/master/builtins/src/main/java/org/jline/builtins/Nano.java#L267
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];

        int remaining;
        while ((remaining = fis.read(buffer)) > 0) {
            bos.write(buffer, 0, remaining);
        }

        byte[] bytes = bos.toByteArray();

/*-        try {
/*-            UniversalDetector detector = new UniversalDetector((CharsetListener) null);
/*-            detector.handleData(bytes, 0, bytes.length);
/*-            detector.dataEnd();
/*-            if (detector.getDetectedCharset() != null) {
/*-                this.charset = Charset.forName(detector.getDetectedCharset());
/*-            }
/*-        } catch (Throwable var17) {
/*-            ;
/*-        } */

        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), this.charset));
        Throwable var7 = null;

        try {
            this.lines.clear();

            String line;
            while ((line = reader.readLine()) != null) {
                this.lines.add(line);
            }
        } catch (Throwable var18) {
            var7 = var18;
            throw var18;
        } finally {
            if (reader != null) {
                if (var7 != null) {
                    try {
                        reader.close();
                    } catch (Throwable var16) {
                        var7.addSuppressed(var16);
                    }
                } else {
                    reader.close();
                }
            }

        }

        if (this.lines.isEmpty()) {
            this.lines.add("");
        }

        this.computeAllOffsets();
        this.moveToChar(0);
    }

    @Alias
    private void moveToChar(int i) {
    }

    @Alias
    private void computeAllOffsets() {

    }
}
