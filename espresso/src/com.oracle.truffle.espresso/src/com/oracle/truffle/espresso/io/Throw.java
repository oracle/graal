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
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;

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
        Class<?> exceptionClass = e.getClass();
        String message = getMessageBoundary(e);
        if (exceptionClass == ClosedByInterruptException.class) {
            throw throwClosedByInterruptException(message, context);
        }

        if (exceptionClass == AsynchronousCloseException.class) {
            throw throwAsynchronousCloseException(message, context);
        }

        if (exceptionClass == ClosedChannelException.class) {
            throw throwClosedChannelException(message, context);
        }

        if (exceptionClass == FileAlreadyExistsException.class) {
            throw throwFileAlreadyExistsException(message, context);
        }

        if (exceptionClass == NoSuchFileException.class) {
            throw throwNoSuchFileException(message, context);
        }

        if (exceptionClass == DirectoryNotEmptyException.class) {
            throw throwDirectoryNotEmptyException(message, context);
        }

        if (exceptionClass == AtomicMoveNotSupportedException.class) {
            throw throwAtomicMoveNotSupportedException(message, context);
        }

        if (exceptionClass == NotLinkException.class) {
            throw throwNotLinkException(message, context);
        }

        if (exceptionClass == AccessDeniedException.class) {
            throw throwAccessDeniedException(message, context);
        }

        if (exceptionClass == NotDirectoryException.class) {
            throw throwNotDirectoryException(message, context);
        }

        if (exceptionClass != IOException.class) {
            context.getLogger().warning(() -> "Not exact translation of IOException: " + exceptionClass);
        }
        throw throwIOException(message, context);
    }

    public static EspressoException throwClosedByInterruptException(String message, EspressoContext context) {
        throw context.getMeta().throwExceptionWithMessage(context.getTruffleIO().java_nio_channels_ClosedByInterruptException, message);
    }

    public static EspressoException throwAsynchronousCloseException(String message, EspressoContext context) {
        throw context.getMeta().throwExceptionWithMessage(context.getTruffleIO().java_nio_channels_AsynchronousCloseException, message);
    }

    public static EspressoException throwClosedChannelException(String message, EspressoContext context) {
        throw context.getMeta().throwExceptionWithMessage(context.getTruffleIO().java_nio_channels_ClosedChannelException, message);
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

    public static EspressoException throwFileAlreadyExistsException(String message, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_nio_file_FileAlreadyExistsException, message);
    }

    public static EspressoException throwDirectoryNotEmptyException(String message, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_nio_file_DirectoryNotEmptyException, message);
    }

    public static EspressoException throwAtomicMoveNotSupportedException(String message, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_nio_file_AtomicMoveNotSupportedException, message);
    }

    public static EspressoException throwAccessDeniedException(String message, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_nio_file_AccessDeniedException, message);
    }

    public static EspressoException throwNoSuchFileException(String filePath, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_nio_file_NoSuchFileException, filePath);
    }

    public static EspressoException throwNotDirectoryException(String message, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_nio_file_NotDirectoryException, message);
    }

    public static EspressoException throwIllegalStateException(String message, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_IllegalStateException, message);
    }

    public static EspressoException throwNotLinkException(String message, EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_nio_file_NotLinkException, message);
    }
}
