/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posixsubst.headers;

import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.posix.headers.PosixDirectives;
import com.oracle.svm.core.util.VMError;

public class PosixSubstDirectives extends PosixDirectives {
    private static final String[] commonLibs = new String[]{
                    "<stdlib.h>",
                    "<dirent.h>",
                    "<grp.h>",
                    "<ifaddrs.h>",
                    "<langinfo.h>",
                    "<net/ethernet.h>",
                    "<net/if.h>",
                    "<netdb.h>",
                    "<netinet/in.h>",
                    "<netinet/ip.h>",
                    "<netinet/tcp.h>",
                    "<sched.h>",
                    "<semaphore.h>",
                    "<stdio.h>",
                    "<spawn.h>",
                    "<sys/file.h>",
                    "<sys/ioctl.h>",
                    "<sys/param.h>",
                    "<sys/poll.h>",
                    "<sys/select.h>",
                    "<sys/socket.h>",
                    "<sys/statvfs.h>",
                    "<sys/sysctl.h>",
                    "<sys/types.h>",
                    "<sys/uio.h>",
                    "<sys/wait.h>",
                    "<termios.h>",
                    "<zlib.h>",
    };

    private static final String[] darwinLibs = new String[]{
                    "<sys/event.h>",
                    "<sys/ucontext.h>",
                    "<netinet6/in6_var.h>",
                    "<net/if_dl.h>",
    };

    private static final String[] linuxLibs = new String[]{
                    "<arpa/inet.h>",
                    "<sys/epoll.h>",
                    "<sys/sendfile.h>",
                    "<link.h>",
    };

    @Override
    public List<String> getHeaderFiles() {
        List<String> result = super.getHeaderFiles();

        result.addAll(Arrays.asList(commonLibs));
        if (Platform.includedIn(InternalPlatform.LINUX_JNI_AND_SUBSTITUTIONS.class)) {
            result.addAll(Arrays.asList(linuxLibs));
        } else if (Platform.includedIn(InternalPlatform.DARWIN_JNI_AND_SUBSTITUTIONS.class)) {
            result.addAll(Arrays.asList(darwinLibs));
        } else {
            throw VMError.shouldNotReachHere("Unsupported OS");
        }
        return result;
    }
}
