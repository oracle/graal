/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posixsubst.headers;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file unistd.h.
 */
@CContext(PosixSubstDirectives.class)
public class Unistd {

    @CConstant
    public static native int R_OK();

    @CConstant
    public static native int W_OK();

    @CConstant
    public static native int X_OK();

    @CConstant
    public static native int F_OK();

    @CConstant
    public static native int STDIN_FILENO();

    @CFunction
    public static native int access(CCharPointer name, int type);

    @CConstant
    public static native short SEEK_SET();

    @CConstant
    public static native short SEEK_CUR();

    @CConstant
    public static native short SEEK_END();

    @CFunction
    public static native SignedWord lseek(int fd, SignedWord offset, int whence);

    @CFunction
    public static native int close(int fd);

    @CFunction
    public static native SignedWord read(int fd, PointerBase buf, UnsignedWord nbytes);

    @CFunction
    public static native SignedWord write(int fd, PointerBase buf, UnsignedWord n);

    @CFunction
    public static native SignedWord pread(int fd, PointerBase buf, UnsignedWord nbytes, long offset);

    @CFunction
    public static native SignedWord pwrite(int fd, PointerBase buf, UnsignedWord n, long offset);

    @CFunction
    public static native int pipe(CIntPointer pipedes);

    @CFunction
    public static native /* unsigned */ int sleep(/* unsigned */ int seconds);

    @CFunction
    public static native int chown(CCharPointer file, /* unsigned */ int owner, /* unsigned */ int group);

    @CFunction
    public static native int fchown(int fd, /* unsigned */ int owner, /* unsigned */ int group);

    @CFunction
    public static native int lchown(CCharPointer file, /* unsigned */ int owner, /* unsigned */ int group);

    @CFunction
    public static native int chdir(CCharPointer path);

    @CFunction
    public static native CCharPointer getcwd(CCharPointer buf, UnsignedWord size);

    @CFunction
    public static native int dup(int fd);

    @CFunction
    public static native int dup2(int fd, int fd2);

    @CFunction
    public static native int execve(CCharPointer path, CCharPointerPointer argv, CCharPointerPointer envp);

    @CFunction
    public static native int execv(CCharPointer path, CCharPointerPointer argv);

    @CConstant
    public static native int _PC_NAME_MAX();

    @CConstant
    public static native int _SC_CLK_TCK();

    @CConstant
    public static native int _SC_OPEN_MAX();

    @CConstant
    public static native int _SC_PAGESIZE();

    @CConstant
    public static native int _SC_PAGE_SIZE();

    @CConstant
    public static native int _SC_IOV_MAX();

    @CConstant
    public static native int _SC_GETGR_R_SIZE_MAX();

    @CConstant
    public static native int _SC_GETPW_R_SIZE_MAX();

    @CConstant
    public static native int _SC_NPROCESSORS_ONLN();

    @CConstant
    @Platforms(InternalPlatform.LINUX_JNI_AND_SUBSTITUTIONS.class)
    public static native int _SC_PHYS_PAGES();

    @CConstant
    @Platforms(InternalPlatform.DARWIN_JNI_AND_SUBSTITUTIONS.class)
    public static native int _CS_DARWIN_USER_TEMP_DIR();

    @CFunction
    public static native long pathconf(CCharPointer path, int name);

    @CFunction
    public static native long sysconf(int name);

    @CFunction
    public static native UnsignedWord confstr(int name, CCharPointer buf, UnsignedWord len);

    @CFunction
    public static native int getpid();

    @CFunction
    public static native int getppid();

    @CFunction
    public static native int getpgrp();

    @CFunction
    public static native int getpgid(int pid);

    @CFunction
    public static native int setpgid(int pid, int pgid);

    @CFunction
    public static native int setsid();

    @CFunction
    public static native int getuid();

    @CFunction
    public static native int geteuid();

    @CFunction
    public static native int getgid();

    @CFunction
    public static native int getegid();

    @CFunction
    public static native int getgroups(int size, CIntPointer list);

    @CFunction
    public static native int setuid(int uid);

    @CFunction
    public static native int seteuid(int uid);

    @CFunction
    public static native int setgid(int gid);

    @CFunction
    public static native int setegid(int gid);

    @CFunction
    public static native int fork();

    @CFunction
    public static native int isatty(int fd);

    @CFunction
    public static native int link(CCharPointer from, CCharPointer to);

    @CFunction
    public static native int symlink(CCharPointer from, CCharPointer to);

    @CFunction
    public static native SignedWord readlink(CCharPointer path, CCharPointer buf, UnsignedWord len);

    @CFunction
    public static native int unlink(CCharPointer name);

    @CFunction
    public static native int unlinkat(int fd, CCharPointer name, int flag);

    @CFunction
    public static native int rmdir(CCharPointer path);

    @CFunction
    public static native CCharPointer getlogin();

    @CFunction
    public static native int gethostname(CCharPointer name, UnsignedWord len);

    @CFunction
    public static native int daemon(int nochdir, int noclose);

    @CFunction
    public static native int fsync(int fd);

    @CFunction
    public static native int getpagesize();

    @CFunction
    public static native int getdtablesize();

    @CFunction
    public static native int truncate(CCharPointer file, SignedWord length);

    @CFunction
    public static native int ftruncate(int fd, long length);

    @CFunction
    public static native int fdatasync(int fildes);

    @CFunction
    public static native CCharPointer crypt(CCharPointer key, CCharPointer salt);

    @CFunction
    public static native SignedWord recvmsg(int socket, Socket.msghdr message, int flags);

    @CFunction
    public static native SignedWord sendmsg(int socket, Socket.msghdr message, int flags);
}
