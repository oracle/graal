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
package com.oracle.svm.core.posix.headers.linux;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CFieldOffset;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.posix.headers.PosixDirectives;

/* Allow underscores in names: Checkstyle: stop. */

@CContext(PosixDirectives.class)
@Platforms({Platform.LINUX.class})
public class LinuxEPoll {

    /* { Do not reformat commented-out code: @formatter:off */
    // typedef union epoll_data
    // {
    //   void     *ptr;
    //   int      fd;
    //   uint32_t u32;
    //   uint64_t u64;
    // } epoll_data_t;
    /* } @formatter:on */
    @CStruct("union epoll_data")
    public interface epoll_data extends PointerBase {

        @CField
        WordPointer ptr();

        @CField
        int fd();

        @CField
        void fd(int value);

        @CField
        int u32();

        @CField
        long u64();
    }

    /* { Do not reformat commented-out code: @formatter:off */
    // struct epoll_event
    // {
    //   uint32_t     events;  /* Epoll events */
    //   epoll_data_t data;    /* User data variable */
    // }
    /* } @formatter:on */
    @CStruct(addStructKeyword = true)
    public interface epoll_event extends PointerBase {

        @CField
        int events();

        @CField
        void events(int value);

        @CFieldOffset
        static int offsetOfevents() {
            /* Ignored method body. */
            return 0;
        }

        /*
         * epoll_event.data is an inline union. Since the C interface deals in pointers, I have
         * an @CFieldAddress that returns a pointer to the data field, and then I can use the
         * accessor methods on epoll_data to access the data from the union.
         */
        @CFieldAddress
        epoll_data addressOfdata();

        @CFieldOffset
        static int offsetOfdata() {
            /* Ignored method body. */
            return 0;
        }
    }

    @CFunction
    public static native int epoll_create(int size);

    @CFunction
    public static native int epoll_ctl(int epfd, int op, int fd, epoll_event event);

    @CFunction
    public static native int epoll_wait(int epfd, epoll_event events, int maxevents, int timeout);
}
