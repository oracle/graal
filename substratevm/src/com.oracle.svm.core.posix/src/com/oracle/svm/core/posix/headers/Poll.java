/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Platform.DARWIN;
import org.graalvm.nativeimage.Platform.LINUX;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

//Allow methods with non-standard names: Checkstyle: stop

/*
 * The definitions I need, manually translated from the C header file.
 */

@Platforms({DARWIN.class, LINUX.class})
@CContext(PosixDirectives.class)
public class Poll {

    /* @formatter:off */
    // struct pollfd
    // {
    //         int     fd;
    //         short   events;
    //         short   revents;
    // };
    /* @formatter:on */
    @CStruct(addStructKeyword = true)
    public interface pollfd extends PointerBase {

        @CField
        int fd();

        @CField
        void set_fd(int value);

        @CField
        @AllowWideningCast
        int events();

        @CField
        @AllowNarrowingCast
        void set_events(int value);

        @CField
        @AllowWideningCast
        int revents();

        @CField
        @AllowNarrowingCast
        void set_revents(int value);
    }

    /*
     * Requestable events. If poll(2) finds any of these set, they are copied to revents on return.
     */
    @CConstant
    public static native int POLLIN();

    @CConstant
    public static native int POLLPRI();

    @CConstant
    public static native int POLLOUT();

    @CConstant
    public static native int POLLRDNORM();

    @CConstant
    public static native int POLLWRNORM();

    @CConstant
    public static native int POLLRDBAND();

    @CConstant
    public static native int POLLWRBAND();

    /*
     * These events are set if they occur regardless of whether they were requested.
     */
    @CConstant
    public static native int POLLERR();

    @CConstant
    public static native int POLLHUP();

    @CConstant
    public static native int POLLNVAL();

    // @formatter:off
    // typedef unsigned int nfds_t;
    // int poll(struct pollfd *fds, nfds_t nfds, int timeout);
    @CFunction
    public static native int poll(pollfd fds, int nfds, int timeout);
    // @formatter:on
}
