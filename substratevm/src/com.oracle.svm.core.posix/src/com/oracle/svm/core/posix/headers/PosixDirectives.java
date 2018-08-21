/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.CContext;

import com.oracle.svm.core.util.VMError;

public class PosixDirectives implements CContext.Directives {
    private static final String[] commonLibs = new String[]{
                    "<stdlib.h>",
                    "<dirent.h>",
                    "<dlfcn.h>",
                    "<fcntl.h>",
                    "<grp.h>",
                    "<ifaddrs.h>",
                    "<langinfo.h>",
                    "<limits.h>",
                    "<locale.h>",
                    "<net/if.h>",
                    "<netdb.h>",
                    "<netinet/in.h>",
                    "<netinet/ip.h>",
                    "<netinet/tcp.h>",
                    "<pthread.h>",
                    "<pwd.h>",
                    "<sched.h>",
                    "<semaphore.h>",
                    "<signal.h>",
                    "<stdio.h>",
                    "<spawn.h>",
                    "<sys/errno.h>",
                    "<sys/file.h>",
                    "<sys/ioctl.h>",
                    "<sys/mman.h>",
                    "<sys/poll.h>",
                    "<sys/resource.h>",
                    "<sys/socket.h>",
                    "<sys/stat.h>",
                    "<sys/statvfs.h>",
                    "<sys/sysctl.h>",
                    "<sys/time.h>",
                    "<sys/times.h>",
                    "<sys/uio.h>",
                    "<sys/utsname.h>",
                    "<sys/wait.h>",
                    "<termios.h>",
                    "<time.h>",
                    "<unistd.h>",
                    "<zlib.h>"
    };

    private static final String[] darwinLibs = new String[]{
                    "<CoreFoundation/CoreFoundation.h>",
                    "<sys/event.h>",
                    "<mach/mach_time.h>",
                    "<mach-o/dyld.h>",
                    "<netinet6/in6_var.h>"
    };

    private static final String[] linuxLibs = new String[]{
                    "<arpa/inet.h>",
                    "<sys/epoll.h>",
                    "<sys/sendfile.h>",
                    "<mntent.h>",
                    "<link.h>",
    };

    @Override
    public boolean isInConfiguration() {
        return Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class);
    }

    @Override
    public List<String> getHeaderFiles() {
        List<String> result = new ArrayList<>(Arrays.asList(commonLibs));
        if (Platform.includedIn(Platform.LINUX.class)) {
            result.addAll(Arrays.asList(linuxLibs));
        } else if (Platform.includedIn(Platform.DARWIN.class)) {
            result.addAll(Arrays.asList(darwinLibs));
        } else {
            throw VMError.shouldNotReachHere("Unsupported OS");
        }
        return result;
    }

    @Override
    public List<String> getMacroDefinitions() {
        return Arrays.asList("_GNU_SOURCE", "_LARGEFILE64_SOURCE");
    }
}
