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

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.posix.headers.Limits;
import com.oracle.svm.core.posix.headers.Pwd;
import com.oracle.svm.core.posix.headers.Unistd;

public abstract class PosixSystemPropertiesSupport extends SystemPropertiesSupport {

    /*
     * Initialization code is adapted from the JDK native code that initializes the system
     * properties, as found in src/solaris/native/java/lang/java_props_md.c
     */

    @Override
    protected String userNameValue() {
        Pwd.passwd pwent = Pwd.getpwuid(Unistd.getuid());
        return pwent.isNull() ? "?" : CTypeConversion.toJavaString(pwent.pw_name());
    }

    @Override
    protected String userHomeValue() {
        Pwd.passwd pwent = Pwd.getpwuid(Unistd.getuid());
        return pwent.isNull() ? "?" : CTypeConversion.toJavaString(pwent.pw_dir());
    }

    @Override
    protected String userDirValue() {
        int bufSize = Limits.MAXPATHLEN();
        CCharPointer buf = StackValue.get(bufSize);
        if (Unistd.getcwd(buf, WordFactory.unsigned(bufSize)).isNonNull()) {
            return CTypeConversion.toJavaString(buf);
        } else {
            throw new java.lang.Error("Properties init: Could not determine current working directory.");
        }
    }
}
