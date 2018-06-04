/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.word.PointerBase;

/** Definitions hand-translated from <spawn.h>. */
@Platforms({DARWIN.class, LINUX.class})
@CContext(PosixDirectives.class)
public class Spawn {
    /* Allow lower-case type names: Checkstyle: stop. */

    /**
     * A pointer to a process identifier.
     *
     * Process identifiers are 32-bit signed integers on both Darwin and Linux.
     */
    @CPointerTo(nameOfCType = "pid_t")
    public interface pid_tPointer extends PointerBase {

        int read();

        void write(int value);
    }

    /** A pointer to an opaque action type. */
    @CStruct
    public interface posix_spawn_file_actions_t extends PointerBase {
    }

    /** A pointer to an opaque attribute type. */
    @CStruct
    public interface posix_spawnattr_t extends PointerBase {
    }

    /**
     * <pre>
     * int
     * posix_spawn(pid_t *restrict pid,
     *             const char *restrict path,
     *             const posix_spawn_file_actions_t *file_actions,
     *             const posix_spawnattr_t *restrict attrp,
     *             char *const argv[restrict],
     *             char *const envp[restrict]);
     * </pre>
     */
    @CFunction
    public static native int posix_spawn(
                    pid_tPointer pid,
                    CCharPointer path,
                    posix_spawn_file_actions_t file_action,
                    posix_spawnattr_t attr,
                    CCharPointerPointer argv,
                    CCharPointerPointer envp);

    /**
     * <pre>
     * int
     * posix_spawnp(pid_t* restrict pid,
     *              const char* restrict file,
     *              const posix_spawn_file_actions_t* file_actions,
     *              const posix_spawnattr_t* restrict attrp,
     *              char* const argv[restrict],
     *              char* const envp[restrict]);
     * </pre>
     */
    @CFunction
    public static native int posix_spawnp(
                    pid_tPointer pid,
                    CCharPointer file,
                    posix_spawn_file_actions_t file_action,
                    posix_spawnattr_t attr,
                    CCharPointerPointer argv,
                    CCharPointerPointer envp);

    @CFunction
    public static native int posix_spawn_file_actions_addclose(
                    posix_spawn_file_actions_t file_actions,
                    int filedes);

    @CFunction
    public static native int posix_spawn_file_actions_adddup2(
                    posix_spawn_file_actions_t file_actions,
                    int filedes,
                    int newfiledes);

    @CFunction
    public static native int posix_spawn_file_actions_addopen(
                    posix_spawn_file_actions_t file_actions,
                    int filedes,
                    CCharPointer path,
                    int oflag,
                    int mode);

    @CFunction
    public static native int posix_spawn_file_actions_init(posix_spawn_file_actions_t file_actions);

    @CFunction
    public static native int posix_spawn_file_actions_destroy(posix_spawn_file_actions_t file_actions);

    @CFunction
    public static native int posix_spawnattr_init(posix_spawnattr_t attr);

    @CFunction
    public static native int posix_spawnattr_destroy(posix_spawnattr_t attr);

    @CFunction
    public static native int posix_spawnattr_setsigdefault(posix_spawnattr_t attr, Signal.sigset_tPointer sigdefault);

    @CFunction
    public static native int posix_spawnattr_getsigdefault(posix_spawnattr_t attr, Signal.sigset_tPointer sigdefault);

    @CFunction
    public static native int posix_spawnattr_setflags(posix_spawnattr_t attr, short flags);

    @CFunction
    public static native int posix_spawnattr_getflags(posix_spawnattr_t attr, CShortPointer flags);

    @CFunction
    public static native int posix_spawnattr_setpgroup(posix_spawnattr_t attr, int pgroup);

    @CFunction
    public static native int posix_spawnattr_getpgroup(posix_spawnattr_t attr, pid_tPointer pgroup);

    @CFunction
    public static native int posix_spawnattr_setsigmask(posix_spawnattr_t attr, Signal.sigset_tPointer sigmask);

    @CFunction
    public static native int posix_spawnattr_getsigmask(posix_spawnattr_t attr, Signal.sigset_tPointer sigmask);

    /* Allow lower-case type names: Checkstyle: resume. */
}
