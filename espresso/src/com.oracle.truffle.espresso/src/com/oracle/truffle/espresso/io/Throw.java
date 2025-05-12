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
package com.oracle.truffle.espresso.io;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;

public final class Throw {
    private Throw() {
    }

    @TruffleBoundary
    private static String getMessageBoundary(Throwable e) {
        return e.getMessage();
    }

    public static EspressoException throwFileNotFoundException(Throwable e, EspressoContext context) {
        throw context.getMeta().throwExceptionWithMessage(context.getTruffleIO().java_io_FileNotFoundException, getMessageBoundary(e));
    }

    public static EspressoException throwFileNotFoundException(String message, EspressoContext context) {
        throw context.getMeta().throwExceptionWithMessage(context.getTruffleIO().java_io_FileNotFoundException, message);
    }

    public static EspressoException throwIOException(String message, EspressoContext context) {
        throw context.getMeta().throwExceptionWithMessage(context.getTruffleIO().java_io_IOException, message);
    }

    public static EspressoException throwIOException(IOException e, EspressoContext context) {
        if (e.getClass() != IOException.class) {
            context.getLogger().warning(() -> "Not exact translation of IOException: " + e.getClass());
        }
        throw throwIOException(getMessageBoundary(e), context);
    }

    public static EspressoException throwNonReadable(EspressoContext context) {
        throw throwIOException("non-readable", context);
    }

    public static EspressoException throwNonWritable(EspressoContext context) {
        throw throwIOException("non-writable", context);
    }

    public static EspressoException throwNonSeekable(EspressoContext context) {
        throw throwIOException("non-seekable", context);
    }

    public static EspressoException throwUnsupported(String message, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedOperationException, message);
    }

    public static EspressoException throwSecurityException(String message, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_SecurityException, message);
    }

    public static EspressoException throwSecurityException(SecurityException e, EspressoContext context) {
        throw throwSecurityException(getMessageBoundary(e), context);
    }

    public static EspressoException throwFileAlreadyExists(FileAlreadyExistsException e, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_nio_file_FileAlreadyExistsException, getMessageBoundary(e));
    }

    public static EspressoException throwDirectoryNotEmpty(DirectoryNotEmptyException e, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_nio_file_DirectoryNotEmptyException, getMessageBoundary(e));
    }

    public static EspressoException throwAtomicMoveNotSupported(EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwException(meta.java_nio_file_AtomicMoveNotSupportedException);
    }

    public static EspressoException throwAccessDenied(EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwException(meta.java_nio_file_AccessDeniedException);
    }

    public static EspressoException throwNoSuchFile(String filePath, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_nio_file_NoSuchFileException, filePath);
    }

    public static EspressoException throwNotDirectory(String message, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_nio_file_NotDirectoryException, message);
    }

    public static EspressoException throwIllegalState(String message, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_IllegalStateException, message);
    }

    public static EspressoException throwNotLink(String file, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_nio_file_NotLinkException, file);
    }
}
