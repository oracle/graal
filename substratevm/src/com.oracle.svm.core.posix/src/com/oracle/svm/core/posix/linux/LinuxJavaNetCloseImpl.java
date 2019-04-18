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
package com.oracle.svm.core.posix.linux;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.posix.PosixJavaNetClose;
import com.oracle.svm.core.headers.Errno;
import com.oracle.svm.core.posix.headers.Poll;
import com.oracle.svm.core.posix.headers.Socket;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Unistd;

/**
 * Translation of the mechanisms in /jdk8u-dev/jdk/src/solaris/native/java/net/linux_close.c that
 * implements interruption of blocking operations. The mechanism is not a direct translation of the
 * C functions, which maintain lists of pthreads, and file descriptors, etc. Rather the mechanism is
 * implemented in Java using Java threads and Thread.interrupt.
 *
 * Also in this class are more traditional translations of the C functions that do blocking
 * operations. Where the implementations are identical between platforms, the shared code lives in
 * {@link PosixJavaNetClose}.
 */
@Platforms({Platform.LINUX.class})
public class LinuxJavaNetCloseImpl extends PosixJavaNetClose {

    protected LinuxJavaNetCloseImpl() {
        /* Nothing to do. */
    }

    /**
     * { @formatter:off
     * 171   * Close or dup2 a file descriptor ensuring that all threads blocked on
     * 172   * the file descriptor are notified via a wakeup signal.
     * 173   *
     * 174   *      fd1 < 0    => close(fd2)
     * 175   *      fd1 >= 0   => dup2(fd1, fd2)
     * 176   *
     * 177   * Returns -1 with errno set if operation fails.
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
            /* And close/dup the file descriptor (restart if interrupted by signal) */
            do {
                if (fd1 < 0) {
                    rv = Unistd.close(fd2);
                } else {
                    rv = Unistd.dup2(fd1, fd2);
                }
            } while ((rv == -1) && (Errno.errno() == Errno.EINTR()));
            /* Send a wakeup signal to all threads blocked on this file descriptor. */
            fdEntry.getThreadList().forEach((threadEntry) -> {
                threadEntry.setIntr(true);
                interruptThread(threadEntry.getPThread());
            });
        }
        return rv;
    }

    /*
     * Implementations of the methods that use interruptible I/O.
     */

    /* { Allow names with underscores: Checkstyle: stop */

    /*
     * The implementation of NET_RecvFrom in linux_close.c has a bug in that it returns before
     * storing `socklen` in `fromlen`. The JDK has fixed the issue by using the same implementation
     * that bsd_close.c uses, so I am not providing a platform-specific implementation.
     */

    /*
     * The implementation of NET_Accept from linux_close.c has a bug in that it returns before
     * storing `socklen` into `addrlen`. But the implementation is different from the implementation
     * in bsd_close.c, so I am providing a platform-specific implementation that removes the bug.
     */

    /* { Do not re-wrap commented-out code.  @formatter:off */
    // 304  int NET_Accept(int s, struct sockaddr *addr, int *addrlen) {
    @Override
    public int NET_Accept(int s, Socket.sockaddr addr, CIntPointer addrlen) {
        // 305      socklen_t socklen = *addrlen;
        CIntPointer socklen = StackValue.get(CIntPointer.class);
        socklen.write(addrlen.read());
        // 306      BLOCKING_IO_RETURN_INT( s, accept(s, addr, &socklen) );
        int result = BLOCKING_IO_RETURN_INT(s, () -> {
            return Socket.accept(s, addr, socklen);
        });
        // 307      *addrlen = socklen;
        addrlen.write(socklen.read());
        return result;
    }
    /* } Do not re-wrap commented-out code.  @formatter:on */

    /* { Do not re-wrap commented-out code.  @formatter:off */
    // 326  /*
    // 327   * Wrapper for poll(s, timeout).
    // 328   * Auto restarts with adjusted timeout if interrupted by
    // 329   * signal other than our wakeup signal.
    // 330   */
    // 331  int NET_Timeout0(int s, long timeout, long currentTime) {
    @Override
    public int NET_Timeout0(int s, long timeoutArg, long currentTime) {
        /* Do not modify argument. */
        long timeout = timeoutArg;
        // 332      long prevtime = currentTime, newtime;
        long prevtime = currentTime;
        long newtime;
        // 333      struct timeval t;
        Time.timeval t = StackValue.get(Time.timeval.class);
        // 334      fdEntry_t *fdEntry = getFdEntry(s);
        PosixJavaNetClose.FdEntry fdEntry = getFdEntry(s);
        // 335
        // 336      /*
        // 337       * Check that fd hasn't been closed.
        // 338       */
        // 339      if (fdEntry == NULL) {
        if (fdEntry == null) {
            // 340          errno = EBADF;
            Errno.set_errno(Errno.EBADF());
            // 341          return -1;
            return -1;
        }
        // 343
        // 344      for(;;) {
        for (; /* return */;) {
            // 345          struct pollfd pfd;
            Poll.pollfd pfd = StackValue.get(Poll.pollfd.class);
            // 346          int rv;
            int rv;
            // 347          threadEntry_t self;
            PosixJavaNetClose.ThreadEntry self = new PosixJavaNetClose.ThreadEntry();
            // 348
            // 349          /*
            // 350           * Poll the fd. If interrupted by our wakeup signal
            // 351           * errno will be set to EBADF.
            // 352           */
            // 353          pfd.fd = s;
            pfd.set_fd(s);
            // 354          pfd.events = POLLIN | POLLERR;
            pfd.set_events(Poll.POLLIN() | Poll.POLLERR());
            // 355
            // 356          startOp(fdEntry, &self);
            startOp(fdEntry, self);
            // 357          rv = poll(&pfd, 1, timeout);
            /* Note the cast from `long timeout` to `int`. */
            rv = Poll.poll(pfd, 1, (int) timeout);
            // 358          endOp(fdEntry, &self);
            endOp(fdEntry, self);
            // 359
            // 360          /*
            // 361           * If interrupted then adjust timeout. If timeout
            // 362           * has expired return 0 (indicating timeout expired).
            // 363           */
            // 364          if (rv < 0 && errno == EINTR) {
            if ((rv < 0) && (Errno.errno() == Errno.EINTR())) {
                // 365              if (timeout > 0) {
                if (timeout > 0) {
                    // 366                  gettimeofday(&t, NULL);
                    Time.gettimeofday(t, WordFactory.nullPointer());
                    // 367                  newtime = t.tv_sec * 1000  +  t.tv_usec / 1000;
                    newtime = ((t.tv_sec() * 1000) + (t.tv_usec() / 1000));
                    // 368                  timeout -= newtime - prevtime;
                    timeout -= newtime - prevtime;
                    // 369                  if (timeout <= 0) {
                    if (timeout <= 0) {
                        // 370                      return 0;
                        return 0;
                    }
                    // 372                  prevtime = newtime;
                    prevtime = newtime;
                }
            } else {
                // 375              return rv;
                return rv;
            }
            // 377
        }
    }
    /* } Do not re-wrap commented-out code.  @formatter:on */

    /* } Allow names with underscores: Checkstyle: resume */
}

@Platforms({Platform.LINUX.class})
@AutomaticFeature
class LinuxJavaNetCloseFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(PosixJavaNetClose.class, new LinuxJavaNetCloseImpl());
    }
}
