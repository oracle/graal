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
package com.oracle.svm.core.posix.headers.darwin;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldOffset;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.posix.headers.PosixDirectives;
import com.oracle.svm.core.posix.headers.Time;

/* Allow underscores in names: Checkstyle: stop. */

@CContext(PosixDirectives.class)
@Platforms({Platform.DARWIN.class})
public class DarwinEvent {

    /* { Do not reformat commented-out code: @formatter:off */
    // struct kevent {
    //     uintptr_t   ident;      /* identifier for this event */
    //     int16_t     filter;     /* filter for event */
    //     uint16_t    flags;      /* general flags */
    //     uint32_t    fflags;     /* filter-specific flags */
    //     intptr_t    data;       /* filter-specific data */
    //     void        *udata;     /* opaque user data identifier */
    // };
    /* } @formatter:on */
    @CStruct("struct kevent")
    public interface kevent extends PointerBase {

        @CField
        Word get_ident();

        @CField
        void set_ident(Word value);

        @CFieldOffset
        static int offsetOf_ident() {
            /* Ignored method body. */
            return 0;
        }

        @CField
        short get_filter();

        @CField
        void set_filter(short value);

        @CFieldOffset
        static int offsetOf_filter() {
            /* Ignored method body. */
            return 0;
        }

        @CField
        short get_flags();

        @CField
        void set_flags(short value);

        @CFieldOffset
        static int offsetOf_flags() {
            /* Ignored method body. */
            return 0;
        }

        @CField
        int get_fflags();

        @CField
        void set_fflags(int value);

        @CField
        Word get_data();

        @CField
        void set_data(Word value);

        @CField
        PointerBase get_udata();

        @CField
        void set_udata(PointerBase value);

        /** Return a pointer to the requested element of a `struct event[]`. */
        kevent addressOf(int index);
    }

    @CConstant
    public static native int EVFILT_READ();

    @CConstant
    public static native int EVFILT_WRITE();

    @CConstant
    public static native int EV_ADD();

    @CConstant
    public static native int EV_DELETE();

    @CFunction
    public static native int kqueue();

    /* FIXME: Unused? */
    /* @formatter:off */
    @CFunction
    public static native int kevent(
                    int                kq,
                    DarwinEvent.kevent changelist,
                    int                nchanges,
                    DarwinEvent.kevent eventlist,
                    int                nevents,
                    Time.timespec      timeout);
    /* @formatter:on */

    /* { Do not reformat commented-out code: @formatter:off */
    // #define EV_SET(kevp, a, b, c, d, e, f) do { \
    //     struct kevent *__kevp__ = (kevp);       \
    //     __kevp__->ident = (a);                  \
    //     __kevp__->filter = (b);                 \
    //     __kevp__->flags = (c);                  \
    //     __kevp__->fflags = (d);                 \
    //     __kevp__->data = (e);                   \
    //     __kevp__->udata = (f);                  \
    // } while(0)
    /* } @ formatter:on */
    public static void EV_SET(DarwinEvent.kevent kevp, Word a, short b, short c, int d, Word e, WordPointer f) {
        kevp.set_ident(a);
        kevp.set_filter(b);
        kevp.set_flags(c);
        kevp.set_fflags(d);
        kevp.set_data(e);
        kevp.set_udata(f);
    }
}
