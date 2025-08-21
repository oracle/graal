/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package sun.nio.fs;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.util.Set;

public class NewFileChannelHelper {
    public static FileChannel open(FileDescriptor fd, String path,
                    boolean readable, boolean writable,
                    boolean sync, boolean direct, Closeable parent) {
        // should be implemented in the overlay project since its version specific.
        throw new IllegalStateException("Should not reach here!");
    }

    public static boolean getDirectOption(Set<? extends OpenOption> options) {
        // should be implemented in the overlay project since its version specific.
        throw new IllegalStateException("Should not reach here!");
    }
}
