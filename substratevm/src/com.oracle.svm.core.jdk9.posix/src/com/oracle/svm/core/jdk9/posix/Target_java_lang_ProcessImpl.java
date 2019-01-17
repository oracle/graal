/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk9.posix;

import java.io.IOException;

import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK9OrLater;
import com.oracle.svm.core.posix.Java_lang_Process_Supplement;
import com.oracle.svm.core.util.VMError;

@AutomaticFeature
class JavaLangSubstitutionsJDK9OrLaterFeature implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !GraalServices.Java8OrEarlier;
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        Class<?> processHandleImplClass = access.findClassByName("java.lang.ProcessHandleImpl");
        VMError.guarantee(processHandleImplClass != null);
        RuntimeClassInitialization.rerunClassInitialization(processHandleImplClass);
    }
}

@TargetClass(className = "java.lang.ProcessImpl", onlyWith = JDK9OrLater.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_lang_ProcessImpl {

    @SuppressWarnings({"static-method"})
    @Substitute
    private int forkAndExec(
                    int mode,
                    byte[] helperpath,
                    byte[] prog,
                    byte[] argBlock, int argc,
                    byte[] envBlock, int envc,
                    byte[] dir,
                    int[] fds,
                    boolean redirectErrorStream)
                    throws IOException {
        return Java_lang_Process_Supplement.forkAndExec(mode, helperpath, prog, argBlock, argc, envBlock, envc, dir, fds, redirectErrorStream);
    }

    @Substitute //
    private static /* native */ void init() {
        throw VMError.unsupportedFeature("JDK9OrLater: Target_java_lang_ProcessImpl.init");
    }
}
