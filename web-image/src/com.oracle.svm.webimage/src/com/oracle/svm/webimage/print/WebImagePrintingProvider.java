/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.print;

import java.io.FileDescriptor;
import java.util.EnumMap;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Provides primitives to print to stdout or stderr.
 * <p>
 * Supports either printing of {@code char} or {@code byte}. Raw bytes are first decoded to
 * characters before being passed to the underlying implementation.
 */
public abstract class WebImagePrintingProvider {

    /**
     * Symbolic "file descriptor" to distinguish between stdout and stderr.
     */
    public enum Descriptor {
        OUT(1),
        ERR(2);

        public final int num;

        Descriptor(int num) {
            this.num = num;
        }

        public static Descriptor from(FileDescriptor fd) {
            if (fd == FileDescriptor.out) {
                return OUT;
            } else if (fd == FileDescriptor.err) {
                return ERR;
            } else {
                return null;
            }
        }
    }

    private final Map<Descriptor, JSDecodeBuffer> decoder = new EnumMap<>(Descriptor.class);

    public WebImagePrintingProvider() {
        for (Descriptor fd : Descriptor.values()) {
            decoder.put(fd, new JSDecodeBuffer());
        }
    }

    @Fold
    public static WebImagePrintingProvider singleton() {
        return ImageSingletons.lookup(WebImagePrintingProvider.class);
    }

    /**
     * Prints a single byte to the given file descriptor.
     */
    public void print(Descriptor fd, byte b) {
        decoder.get(fd).write(b);
        decodeAll(fd);
    }

    /**
     * Prints a slice of the byte array ({@code b[off, off + len)}) to the given file descriptor.
     */
    public void print(Descriptor fd, byte[] b, int off, int len) {
        decoder.get(fd).write(b, off, len);
        decodeAll(fd);
    }

    /**
     * Prints characters to the given file descriptor.
     */
    public abstract void print(Descriptor fd, char[] chars);

    /**
     * Prints a newline to the given file descriptor.
     */
    public void newline(Descriptor fd) {
        print(fd, new char[]{'\n'});
    }

    /**
     * Flushes output on the given file descriptor.
     */
    public abstract void flush(Descriptor fd);

    /**
     * Closes the given file descriptor.
     */
    public abstract void close(Descriptor fd);

    private void decodeAll(Descriptor fd) {
        JSDecodeBuffer buf = decoder.get(fd);
        if (buf.hasNext()) {
            print(fd, buf.popAll());
        }
    }

}
