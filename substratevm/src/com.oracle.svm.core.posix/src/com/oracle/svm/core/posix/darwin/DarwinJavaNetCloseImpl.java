/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.darwin;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.posix.PosixJavaNetClose;
import com.oracle.svm.core.headers.Errno;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Socket;
import com.oracle.svm.core.posix.headers.SysParam;
import com.oracle.svm.core.posix.headers.SysSelect;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Unistd;

/**
 * Translation of the mechanisms in /jdk8u-dev/jdk/src/solaris/native/java/net/bsd_close.c that
 * implements interruption of blocking operations. The mechanism is not a direct translation of the
 * C functions, which maintain lists of pthreads, and file descriptors, etc. Rather the mechanism is
 * implemented in Java using Java threads and Thread.interrupt.
 *
 * Also in this class are more traditional translations of the C functions that do blocking
 * operations. Where the implementations are identical between platforms, the shared code lives in
 * {@link PosixJavaNetClose}.
 */
@Platforms({Platform.DARWIN.class})
public final class DarwinJavaNetCloseImpl extends PosixJavaNetClose {

    protected DarwinJavaNetCloseImpl() {
        /* Nothing to do. */
    }

    /**
     * { @formatter:off
     * 189   * Close or dup2 a file descriptor ensuring that all threads blocked on
     * 190   * the file descriptor are notified via a wakeup signal.
     * 191   *
     * 192   *      fd1 < 0    => close(fd2)
     * 193   *      fd1 >= 0   => dup2(fd1, fd2)
     * 194   *
     * 195   * Returns -1 with errno set if operation fails.
     * } @formatter:on
     */
    @Override
    protected int closefd(int fd1, int fd2) {
        PosixJavaNetClose.FdEntry fdEntry = getFdEntry(fd2);
        if (fdEntry == null) {
            Errno.set_errno(Errno.EBADF());
            return -1;
        }
        int rv;
        /* Lock the fd to hold-off additional I/O on this fd. */
        /* { Allow synchronization: Checkstyle: stop. */
        synchronized (fdEntry) {
            /* } Allow synchronization: Checkstyle: resume. */
            /* Send a wakeup signal to all threads blocked on this file descriptor. */
            fdEntry.getThreadList().forEach((threadEntry) -> {
                threadEntry.setIntr(true);
                interruptThread(threadEntry.getPThread());
            });
            /* And close/dup the file descriptor (restart if interrupted by signal) */
            do {
                if (fd1 < 0) {
                    rv = Unistd.close(fd2);
                } else {
                    rv = Unistd.dup2(fd1, fd2);
                }
            } while ((rv == -1) && (Errno.errno() == Errno.EINTR()));
        }
        return rv;
    }

    /*
     * Implementations of the methods that use interruptible I/O.
     */

    /* { Allow names with underscores: Checkstyle: stop */

    /* { Do not re-wrap commented-out code.  @formatter:off */
    // 322  int NET_Accept(int s, struct sockaddr *addr, int *addrlen) {
    @Override
    public int NET_Accept(int s, Socket.sockaddr addr, CIntPointer addrlen) {
        // 323      socklen_t len = *addrlen;
        CIntPointer len_Pointer = StackValue.get(CIntPointer.class);
        len_Pointer.write(addrlen.read());
        // 324      int error = accept(s, addr, &len);
        int error = Socket.accept(s, addr, len_Pointer);
        // 325      if (error != -1)
        if (error != -1) {
            // 326          *addrlen = (int)len;
            addrlen.write(len_Pointer.read());
        }
        // 327      BLOCKING_IO_RETURN_INT( s, error );
        return BLOCKING_IO_RETURN_INT(s, () -> {
            return error;
        });
    }
    /* } Do not re-wrap commented-out code.  @formatter:on */

    /* { Do not re-wrap commented-out code.  @formatter:off */
    // 346  /*
    // 347   * Wrapper for select(s, timeout). We are using select() on Mac OS due to Bug 7131399.
    // 348   * Auto restarts with adjusted timeout if interrupted by
    // 349   * signal other than our wakeup signal.
    // 350   */
    // 351  int NET_Timeout0(int s, long timeout, long currentTime) {
    @Override
    public int NET_Timeout0(int s, long timeoutArg, long currentTime) {
        /* Do not modify argument. */
        long timeout = timeoutArg;
        // 352      long prevtime = currentTime, newtime;
        long prevtime = currentTime;
        long newtime;
        // 353      struct timeval t, *tp = &t;
        Time.timeval t = StackValue.get(Time.timeval.class);
        Time.timeval tp;
        tp = t;
        // 354      fd_set fds;
        SysSelect.fd_set fds = StackValue.get(SysSelect.fd_set.class);
        // 355      fd_set* fdsp = NULL;
        SysSelect.fd_set fdsp = WordFactory.nullPointer();
        // 356      int allocated = 0;
        int allocated = 0;
        // 357      threadEntry_t self;
        ThreadEntry self = new ThreadEntry();
        // 358      fdEntry_t *fdEntry = getFdEntry(s);
        FdEntry fdEntry = getFdEntry(s);
        // 359
        // 360      /*
        // 361       * Check that fd hasn't been closed.
        // 362       */
        // 363      if (fdEntry == NULL) {
        if (fdEntry == null) {
            // 364          errno = EBADF;
            Errno.set_errno(Errno.EBADF());
            // 365          return -1;
            return -1;
        }
        // 367
        // 368      /*
        // 369       * Pick up current time as may need to adjust timeout
        // 370       */
        // 371      if (timeout > 0) {
        if (timeout > 0) {
            // 372          /* Timed */
            // 373          t.tv_sec = timeout / 1000;
            t.set_tv_sec(timeout / 1000);
            // 374          t.tv_usec = (timeout % 1000) * 1000;
            t.set_tv_usec((timeout % 1000) * 1000);
            // 375      } else if (timeout < 0) {
        } else if (timeout < 0) {
            // 376          /* Blocking */
            // 377          tp = 0;
            tp = WordFactory.nullPointer();
        } else {
            // 379          /* Poll */
            // 380          t.tv_sec = 0;
            t.set_tv_sec(0);
            // 381          t.tv_usec = 0;
            t.set_tv_usec(0);
        }
        // 383
        // 384      if (s < FD_SETSIZE) {
        if (s < SysSelect.FD_SETSIZE()) {
            // 385          fdsp = &fds;
            fdsp = fds;
            // 386          FD_ZERO(fdsp);
            SysSelect.FD_ZERO(fdsp);
        } else {
            // 388          int length = (howmany(s+1, NFDBITS)) * sizeof(int);
            int length = (SysParam.howmany(s+1, SysSelect.NFDBITS())) * SizeOf.get(CIntPointer.class);
            // 389          fdsp = (fd_set *) calloc(1, length);
            fdsp = (SysSelect.fd_set) LibC.calloc(WordFactory.unsigned(1), WordFactory.unsigned(length));
            // 390          if (fdsp == NULL) {
            if (fdsp.isNull()) {
                // 391              return -1;   // errno will be set to ENOMEM
                return -1;
            }
            // 393          allocated = 1;
            allocated = 1;
        }
        // 395      FD_SET(s, fdsp);
        SysSelect.FD_SET(s, fdsp);
        // 396
        // 397      for(;;) {
        for (; /* return */;) {
            // 398          int rv;
            int rv;
            // 399
            // 400          /*
            // 401           * call select on the fd. If interrupted by our wakeup signal
            // 402           * errno will be set to EBADF.
            // 403           */
            // 404
            // 405          startOp(fdEntry, &self);
            startOp(fdEntry, self);
            // 406          rv = select(s+1, fdsp, 0, 0, tp);
            rv = SysSelect.select(s + 1, fdsp, WordFactory.nullPointer(), WordFactory.nullPointer(), tp);
            // 407          endOp(fdEntry, &self);
            endOp(fdEntry, self);
            // 408
            // 409          /*
            // 410           * If interrupted then adjust timeout. If timeout
            // 411           * has expired return 0 (indicating timeout expired).
            // 412           */
            // 413          if (rv < 0 && errno == EINTR) {
            if ((rv < 0) && (Errno.errno() == Errno.EINTR())) {
                // 414              if (timeout > 0) {
                if (timeout > 0) {
                    // 415                  struct timeval now;
                    Time.timeval now = StackValue.get(Time.timeval.class);
                    // 416                  gettimeofday(&now, NULL);
                    Time.gettimeofday(now, WordFactory.nullPointer());
                    // 417                  newtime = now.tv_sec * 1000  +  now.tv_usec / 1000;
                    newtime = ((now.tv_sec() * 1000) + (now.tv_usec() / 1000));
                    // 418                  timeout -= newtime - prevtime;
                    timeout -= newtime - prevtime;
                    // 419                  if (timeout <= 0) {
                    if (timeout <= 0) {
                        // 420                      if (allocated != 0)
                        if (allocated != 0) {
                            // 421                          free(fdsp);
                            LibC.free(fdsp);
                        }
                        // 422                      return 0;
                        return 0;
                    }
                    // 424                  prevtime = newtime;
                    prevtime = newtime;
                    // 425                  t.tv_sec = timeout / 1000;
                    t.set_tv_sec(timeout / 1000);
                    // 426                  t.tv_usec = (timeout % 1000) * 1000;
                    t.set_tv_usec((timeout % 1000) * 1000);
                }
            } else {
                // 429              if (allocated != 0)
                if (allocated != 0) {
                    // 430                  free(fdsp);
                    LibC.free(fdsp);
                }
                // 431              return rv;
                return rv;
            }
            // 433
        }
    }
    /* } Do not re-wrap commented-out code.  @formatter:on */

    /* } Allow names with underscores: Checkstyle: resume */
}

@Platforms({Platform.DARWIN.class})
@AutomaticFeature
class DarwinJavaNetCloseFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(PosixJavaNetClose.class, new DarwinJavaNetCloseImpl());
    }
}
