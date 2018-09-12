/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.intrinsics;

import java.io.IOException;
import java.io.OutputStream;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

import static com.oracle.truffle.espresso.meta.Meta.meta;

@EspressoIntrinsics
public class Target_java_io_FileOutputStream {
    @Intrinsic
    public static void initIDs() {
        /* nop */
    }



    @Intrinsic(hasReceiver = true)
    public static void writeBytes(StaticObject self, byte[] bytes, int offset, int len, boolean append) {
        int fd = (int) meta((StaticObject) meta(self).field("fd").get()).field("fd").get();
        if (fd == 1 || fd == 2) {
            OutputStream known = (fd == 1) ? Utils.getContext().out() : Utils.getContext().err();
            try {
                write(known, bytes, offset, len);
            } catch (IOException e) {
                // TODO(peterssen): Handle exception.
                e.printStackTrace();
            }
        } else {
            throw new RuntimeException("Cannot write to FD: " + fd + " operation not supported");
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static void write(OutputStream out, byte[] bytes, int offset, int len) throws IOException {
        out.write(bytes, offset, len);
    }
}
