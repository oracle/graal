/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Pwd;
import com.oracle.svm.core.posix.headers.Pwd.passwd;
import com.oracle.svm.core.posix.headers.Pwd.passwdPointer;
import com.oracle.svm.core.posix.headers.Unistd;

/**
 * Substitutions for the Java Authentication and Authorization Service (JAAS,
 * {@code javax.security.auth} and {@code com.sun.security.auth} packages).
 */
public final class PosixJaasSubstitutions {
    private PosixJaasSubstitutions() {
    }
}

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@TargetClass(className = "com.sun.security.auth.module.UnixSystem")
final class Target_com_sun_security_auth_module_UnixSystem {
    @Alias String username;
    @Alias long uid;
    @Alias long gid;
    @Alias long[] groups;

    @Substitute
    Target_com_sun_security_auth_module_UnixSystem() {
        getUnixInfo();
    }

    @Substitute
    void getUnixInfo() {
        int realUid = Unistd.getuid();

        Word pwsize = WordFactory.signed(Unistd.sysconf(Unistd._SC_GETPW_R_SIZE_MAX()));
        if (pwsize.lessThan(0)) { // indicates no definitive bound: try different sizes
            pwsize = WordFactory.signed(1024);
        }
        CCharPointer pwbuf = LibC.malloc(pwsize);
        try {
            passwd pwent = StackValue.get(passwd.class);
            passwdPointer p = StackValue.get(passwdPointer.class);
            int result;
            do {
                if (pwbuf.isNull()) {
                    throw new OutOfMemoryError("Native heap");
                }
                p.write(WordFactory.nullPointer());
                result = Pwd.getpwuid_r(realUid, pwent, pwbuf, pwsize, p);
                if (result == Errno.ERANGE()) {
                    pwsize = pwsize.add(pwsize);
                    pwbuf = LibC.realloc(pwbuf, pwsize);
                } else if (result < 0 && result != Errno.EINTR()) {
                    throw new RuntimeException("getpwuid_r error: " + Errno.errno());
                }
            } while (result != 0);

            this.uid = pwent.pw_uid();
            this.gid = pwent.pw_gid();
            this.username = CTypeConversion.toJavaString(pwent.pw_name());
        } finally {
            LibC.free(pwbuf);
        }

        int ngroups = Unistd.getgroups(0, WordFactory.nullPointer());
        int[] groupIds = new int[ngroups];
        try (PinnedObject pinnedGroupIds = PinnedObject.create(groupIds)) {
            ngroups = Unistd.getgroups(groupIds.length, pinnedGroupIds.addressOfArrayElement(0));
        }
        this.groups = new long[ngroups];
        for (int i = 0; i < this.groups.length; i++) {
            this.groups[i] = groupIds[i];
        }
    }

}
