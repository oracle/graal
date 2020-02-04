/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.driver;

import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;

public class NativeImageServerHelper {
    @Fold
    public static boolean isInConfiguration() {
        return Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class);
    }

    /*
     * Ensures started server keeps running even after native-image completes.
     */
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static int daemonize(Runnable runnable) {
        int pid = Unistd.fork();
        switch (pid) {
            case 0:
                break;
            default:
                return pid;
        }

        /* The server should not get signals from the native-image during the first run. */
        Unistd.setsid();

        runnable.run();
        System.exit(0);
        return -1;
    }
}

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
class UnistdDirectives implements CContext.Directives {
    @Override
    public boolean isInConfiguration() {
        return NativeImageServerHelper.isInConfiguration();
    }

    @Override
    public List<String> getHeaderFiles() {
        return Arrays.asList(new String[]{"<unistd.h>"});
    }

    @Override
    public List<String> getMacroDefinitions() {
        return Arrays.asList("_GNU_SOURCE", "_LARGEFILE64_SOURCE");
    }
}

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@CContext(UnistdDirectives.class)
class Unistd {
    /**
     * Create a new session with the calling process as its leader. The process group IDs of the
     * session and the calling process are set to the process ID of the calling process, which is
     * returned.
     */
    @CFunction
    public static native int setsid();

    /** Return the session ID of the given process. */
    @CFunction
    public static native int getsid(int pid);

    /** Return identifier for the current host. */
    @CFunction
    public static native long gethostid();

    /**
     * Clone the calling process, creating an exact copy. Return -1 for errors, 0 to the new
     * process, and the process ID of the new process to the old process.
     */
    @CFunction
    public static native int fork();
}
