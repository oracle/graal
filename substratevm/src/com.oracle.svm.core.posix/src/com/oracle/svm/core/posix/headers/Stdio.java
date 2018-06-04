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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;

//Checkstyle: stop

/**
 * Contains the definitions from stdio.h that we actually needed.
 */
@CContext(PosixDirectives.class)
public class Stdio {

    /** The opaque type of streams. */
    public interface FILE extends PointerBase {
    }

    /** Rename file OLD to NEW. */
    @CFunction
    public static native int rename(CCharPointer old, CCharPointer _new);

    /** Rename file OLD relative to OLDFD to NEW relative to NEWFD. */
    @CFunction
    public static native int renameat(int oldfd, CCharPointer old, int newfd, CCharPointer _new);

    /** End of file character. Some things throughout the library rely on this being -1. */
    @CConstant
    public static native int EOF();

    /** Open a file and create a new stream for it. */
    @CFunction
    public static native FILE fopen(CCharPointer filename, CCharPointer modes);

    /** Close STREAM. */
    @CFunction
    public static native int fclose(FILE stream);

    @CFunction
    public static native CCharPointer fgets(CCharPointer str, int n, FILE f);

    @CFunction
    public static native FILE popen(CCharPointer command, CCharPointer type);

    @CFunction
    public static native int pclose(FILE stream);

    @CFunction
    public static native int remove(CCharPointer path);

    public static class NoTransitions {
        @CFunction(transition = Transition.NO_TRANSITION)
        public static native FILE fopen(CCharPointer filename, CCharPointer modes);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int fclose(FILE stream);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int fgetc(FILE f);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int ungetc(int c, FILE f);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native SignedWord getline(CCharPointerPointer lineptr, WordPointer n, FILE f);
    }
}
