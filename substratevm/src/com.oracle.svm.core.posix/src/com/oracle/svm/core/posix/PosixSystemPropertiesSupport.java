/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.posix.headers.Limits;
import com.oracle.svm.core.posix.headers.Unistd;

public abstract class PosixSystemPropertiesSupport extends SystemPropertiesSupport {

    public PosixSystemPropertiesSupport(boolean compatibilityMode) {
        super(compatibilityMode);
    }

    @Override
    protected String jvmLibName() {
        return "libjvm" + jvmLibSuffix();
    }

    protected abstract String jvmLibSuffix();

    @Override
    protected String sunBootLibraryPathValue() {
        String executableDirectory = executableDirectory();
        if (executableDirectory == null) {
            return "";
        }
        /*
         * HotSpot uses the VM library directory for shared libraries and the executable directory
         * for statically linked launchers. In libjvmci-style shared-library images, the loaded
         * image can live outside the JDK, so prefer java.home if it was explicitly initialized.
         * Otherwise derive the JDK library directory from the launcher and fall back to the
         * launcher directory only when no enclosing JDK home is found.
         */
        String javaHome = getInitialProperty("java.home", false);
        if (javaHome == null) {
            javaHome = findEnclosingJavaHome(executableDirectory, "lib", "libjava" + jvmLibSuffix());
        }
        if (javaHome != null) {
            return childPath(javaHome, "lib");
        }
        if (SubstrateOptions.SharedLibrary.getValue() && pathEndsWithName(executableDirectory, "bin")) {
            String fallbackJavaHome = parentPath(executableDirectory);
            if (fallbackJavaHome != null) {
                return childPath(fallbackJavaHome, "lib");
            }
        }
        return executableDirectory;
    }

    @Override
    protected boolean pathExists(String path) {
        try (CTypeConversion.CCharPointerHolder pathHolder = CTypeConversion.toCString(path)) {
            PosixStat.stat stat = UnsafeStackValue.get(PosixStat.stat.class);
            return PosixStat.restartableLstat(pathHolder.get(), stat) == 0;
        }
    }

    /*
     * Initialization code is adapted from the JDK native code that initializes the system
     * properties, as found in src/solaris/native/java/lang/java_props_md.c
     */

    @Override
    protected String userNameValue() {
        String name = PosixUtils.getUserName(Unistd.getuid());
        return name == null ? "?" : name;
    }

    @Override
    protected String userHomeValue() {
        String dir = PosixUtils.getUserDir(Unistd.getuid());
        return dir == null ? "?" : dir;
    }

    @Override
    protected String userDirValue() {
        int bufSize = Limits.MAXPATHLEN();
        CCharPointer buf = UnsafeStackValue.get(bufSize);
        if (Unistd.getcwd(buf, Word.unsigned(bufSize)).isNonNull()) {
            return CTypeConversion.toJavaString(buf);
        } else {
            throw new java.lang.Error("Properties init: Could not determine current working directory.");
        }
    }
}
