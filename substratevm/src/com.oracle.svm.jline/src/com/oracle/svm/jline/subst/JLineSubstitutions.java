/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jline.subst;

// Checkstyle: stop
import java.io.IOException;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jline.Terminal;
import jline.Terminal2;
import jline.UnixTerminal;
import jline.UnsupportedTerminal;
import jline.internal.Log;
import sun.misc.Signal;
import sun.misc.SignalHandler;
//Checkstyle: resume

@AutomaticFeature
final class JLineFeature implements Feature {

    static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(JLineFeature.class);
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return access.findClassByName("jline.console.ConsoleReader") != null;
    }
}

@TargetClass(className = "jline.TerminalFactory", onlyWith = JLineFeature.IsEnabled.class)
final class Target_jline_TerminalFactory {

    @SuppressWarnings("unused")
    @Substitute
    public static Terminal create(String ttyDevice) {
        Terminal t;
        try {
            t = new UnixTerminal();
            t.init();
        } catch (Exception e) {
            Log.error("Failed to construct terminal; falling back to UnsupportedTerminal", e);
            t = new UnsupportedTerminal();
        }

        Log.debug("Created Terminal: ", t);

        return t;
    }
}

@TargetClass(className = "jline.console.ConsoleReader", onlyWith = JLineFeature.IsEnabled.class)
final class Target_jline_console_ConsoleReader {

    @Alias Terminal2 terminal;

    @Alias
    native void drawLine() throws IOException;

    @Alias
    native void flush() throws IOException;

    @Substitute
    private void setupSigCont() {
        SignalHandler signalHandler = new SignalHandler() {
            @Override
            public void handle(Signal arg0) {
                /* Original implementation calls this code using reflection. */
                try {
                    terminal.init();
                    drawLine();
                    flush();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        };
        Signal.handle(new Signal("CONT"), signalHandler);
    }
}

@TargetClass(className = "jline.internal.TerminalLineSettings", onlyWith = JLineFeature.IsEnabled.class)
final class Target_jline_internal_TerminalLineSettings {
    @Substitute
    private static ProcessBuilder inheritInput(ProcessBuilder pb) throws Exception {
        /* Original implementation calls this method using reflection. */
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        return pb;
    }
}

public final class JLineSubstitutions {
}
