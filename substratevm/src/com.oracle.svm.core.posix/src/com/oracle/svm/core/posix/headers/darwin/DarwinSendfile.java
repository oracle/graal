/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.posix.headers.PosixDirectives;
import com.oracle.svm.core.posix.headers.Uio.iovec;

//Checkstyle: stop

@CContext(PosixDirectives.class)
@Platforms(Platform.DARWIN.class)
public class DarwinSendfile {

    @CStruct(addStructKeyword = true)
    public interface sf_hdtr extends PointerBase {
        /* pointer to an array of header struct iovec's */
        @CField
        iovec headers();

        /* number of header iovec's */
        @CField
        int hdr_cnt();

        /* pointer to an array of trailer struct iovec's */
        @CField
        iovec trailers();

        /* number of trailer iovec's */
        @CField
        int trl_cnt();
    }

    @CFunction
    public static native int sendfile(int fd, int s, long offset, CLongPointer len, sf_hdtr hdtr, int flags);
}
