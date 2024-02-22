/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni.headers;

import com.oracle.svm.core.util.BasedOnJDKFile;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;

final class JNIHeaderDirectivesJDK22OrLater extends JNIHeaderDirectives {
    @Override
    public boolean isInConfiguration() {
        return JavaVersionUtil.JAVA_SPEC >= 22;
    }
}

@CContext(JNIHeaderDirectivesJDK22OrLater.class)
public final class JNIVersionJDKLatest {

    // Checkstyle: stop

    /*
     * GR-50948: there is not yet a JNI_VERSION_XX constant defined for JDK latest. As soon as it
     * gets available, the "value" property of the CConstant annotation below must be removed.
     */
    @CConstant(value = "JNI_VERSION_21")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+10/src/java.base/share/native/include/jni.h#L1985-L1996")
    public static native int JNI_VERSION_LATEST();

    // Checkstyle: resume

    private JNIVersionJDKLatest() {
    }
}
