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
package com.oracle.svm.hosted.jdk;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;

@AutomaticallyRegisteredFeature
class JDKRegistrations extends JNIRegistrationUtil implements InternalFeature {

    /**
     * Registrations of class re-initialization at run time. This is independent whether the JNI
     * platform is used or not.
     */
    @Override
    public void duringSetup(DuringSetupAccess a) {
        rerunClassInit(a, "java.io.RandomAccessFile", "java.lang.ProcessEnvironment", "java.io.File$TempDirectory", "java.nio.file.TempFileHelper", "java.lang.Terminator");
        rerunClassInit(a, "java.lang.ProcessImpl", "java.lang.ProcessHandleImpl", "java.lang.ProcessHandleImpl$Info", "java.io.FilePermission");

        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            /*
             * The class initializer queries and caches state (like "is a tty") - some state on JDK
             * 17 and even more after JDK 17.
             */
            rerunClassInit(a, "java.io.Console");
        } else {
            /*
             * Ensure jdk.internal.access.SharedSecrets.javaIOAccess is initialized before scanning.
             */
            ((DuringSetupAccessImpl) a).ensureInitialized("java.io.Console");
        }

        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            /*
             * Holds system and user library paths derived from the `java.library.path` and
             * `sun.boot.library.path` system properties.
             */
            rerunClassInit(a, "jdk.internal.loader.NativeLibraries$LibraryPaths");
            /*
             * Contains lots of state that is only available at run time: loads a native library,
             * stores a `Random` object and the temporary directory in a static final field.
             */
            rerunClassInit(a, "sun.nio.ch.UnixDomainSockets");

            rerunClassInit(a, "java.util.concurrent.ThreadLocalRandom$ThreadLocalRandomProxy");
        }

        /*
         * Re-initialize the registered shutdown hooks, because any hooks registered during native
         * image construction must not survive into the running image. Both classes have only static
         * members and do not allow instantiation.
         */
        rerunClassInit(a, "java.lang.ApplicationShutdownHooks", "java.io.DeleteOnExitHook");

        /* Trigger initialization of java.net.URLConnection.fileNameMap. */
        java.net.URLConnection.getFileNameMap();
    }
}
