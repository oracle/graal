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
package com.oracle.svm.core.posix;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.headers.Errno;
import com.oracle.svm.core.posix.headers.Poll;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Socket;
import com.oracle.svm.core.posix.headers.Uio;

/**
 * Interrupt blocking operations when a file descriptor is closed.
 *
 * This class is a translation of the mechanism in
 * /jdk8u-dev/jdk/src/solaris/native/java/net/bsd_close.c and
 * /jdk8u-dev/jdk/src/solaris/native/java/net/linux_close.c that implements interruption of blocking
 * operations. This translation is not a direct translation of the methods. Rather the mechanism is
 * implemented using Java data structures.
 */
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public abstract class PosixJavaNetClose {

    /**
     * A ThreadEntry is created for each blocking operation, to record the thread that is blocking,
     * and whether the operation has been interrupted.
     */
    protected static class ThreadEntry {

        private Pthread.pthread_t pThread;
        private boolean intr;

        /**
         * Fields are initialized in {@link PosixJavaNetClose#startOp(FdEntry, ThreadEntry)} to
         * match the JDK code.
         */
        public ThreadEntry() {
            /* Nothing to do. */
        }

        public Pthread.pthread_t getPThread() {
            return pThread;
        }

        public void setPThread(Pthread.pthread_t pThreadArg) {
            pThread = pThreadArg;
        }

        public boolean getIntr() {
            return intr;
        }

        public void setIntr(boolean value) {
            intr = value;
        }
    }

    /**
     * An FdEntry is created for each file descriptor that is used in a blocking operation, to hold
     * a list of threads that are blocked on the file descriptor. Once created, a FdEntry persists
     * past the end of the operation, and is reused if the same file descriptor is re-opened.
     */
    protected static class FdEntry {

        /** A list of threads blocking on a file descriptor. */
        private final List<ThreadEntry> threadList;

        public FdEntry() {
            /* Most file descriptors have one thread blocked on them. */
            this.threadList = new ArrayList<>(2);
        }

        public List<ThreadEntry> getThreadList() {
            return threadList;
        }
    }

    /**
     * A map from file descriptors to a list of threads that are blocked on that file descriptor.
     */
    private static final Map<Integer, FdEntry> fdTable = new ConcurrentHashMap<>();

    /**
     * Many threads will be looking up entries in the map. After a while it should be rare that a
     * new entry will need to be created, as an entry for a particular file descriptor persists in
     * the map, but adding a new entry to the map has to be atomic.
     */
    protected static FdEntry getFdEntry(int fd) {
        /* `fd` passed as the key is auto-boxed via Integer.valueOf. */
        return fdTable.computeIfAbsent(fd, unused -> new FdEntry());
    }

    /**
     * { @formatter:off
     * Start a blocking operation :-
     *    Insert thread onto thread list for the fd.
     * } @formatter:on
     */
    protected static void startOp(FdEntry fdEntry, ThreadEntry self) {
        self.setPThread(Pthread.pthread_self());
        self.setIntr(false);
        /* { Allow synchronization: Checkstyle: stop. */
        synchronized (fdEntry) {
            /* } Allow synchronization: Checkstyle: resume. */
            fdEntry.getThreadList().add(self);
        }
    }

    /**
     * { @formatter:off
     * End a blocking operation :-
     *    Remove thread from thread list for the fd.
     *    If fd has been interrupted then set errno to EBADF.
     * } @formatter:on
     */
    protected static void endOp(FdEntry fdEntry, ThreadEntry self) {
        boolean badErrno = false;
        /* { Allow synchronization: Checkstyle: stop. */
        synchronized (fdEntry) {
            /* } Allow synchronization: Checkstyle: resume. */
            fdEntry.getThreadList().remove(self);
            if (self.getIntr()) {
                badErrno = true;
            }
        }
        if (badErrno) {
            Errno.set_errno(Errno.EBADF());
        }
    }

    /*
     * Declarations of methods whose implementation differs by platform. These method are
     * implemented in, e.g., DarwinJavaNetCloseSupport and LinuxJavaNetCloseSupport.
     */

    protected abstract int closefd(int fd1, int fd2);

    /** Interrupt the given thread. */
    protected void interruptThread(Pthread.pthread_t pThread) {
        try {
            PosixInterruptSignalUtils.interruptPThread(pThread);
        } catch (IOException ioe) {
            /* Ignored to match the JDK code. */
        }
    }

    /*
     * Implementations that are identical between bsd_close.c and linux_close.c. Where line numbers
     * are given they are from bsd_close.c.
     */

    /* { Allow method names with underscores: Checkstyle: stop */

    /* { Do not re-wrap commented-out code.  @formatter:off */
    // 268  /************** Basic I/O operations here ***************/
    // 269
    // 270  /*
    // 271   * Macro to perform a blocking IO operation. Restarts
    // 272   * automatically if interrupted by signal (other than
    // 273   * our wakeup signal)
    // 274   */
    // 275  #define BLOCKING_IO_RETURN_INT(FD, FUNC) {      \
    // 276      int ret;                                    \
    // 277      threadEntry_t self;                         \
    // 278      fdEntry_t *fdEntry = getFdEntry(FD);        \
    // 279      if (fdEntry == NULL) {                      \
    // 280          errno = EBADF;                          \
    // 281          return -1;                              \
    // 282      }                                           \
    // 283      do {                                        \
    // 284          startOp(fdEntry, &self);                \
    // 285          ret = FUNC;                             \
    // 286          endOp(fdEntry, &self);                  \
    // 287      } while (ret == -1 && errno == EINTR);      \
    // 288      return ret;                                 \
    // 289  }
    /**
     * The differences from the above macro:
     * (1) Passing an IntSupplier instead of the text of a function application.
     *     The IntSupplier must capture all the state that it needs.
     * (2) Instead of a stack-value threadEntry_t, a new ThreadEntry is allocated
     *     on the heap.
     */
    protected int BLOCKING_IO_RETURN_INT(int FD, IntSupplier FUNC) {
        int ret;
        final FdEntry fdEntry = getFdEntry(FD);
        if (fdEntry == null) {
            Errno.set_errno(Errno.EBADF());
            return -1;
        }
        final ThreadEntry self = new ThreadEntry();
        do {
            startOp(fdEntry, self);
            ret = FUNC.getAsInt();
            endOp(fdEntry, self);
        } while ((ret == -1) && Errno.errno() == Errno.EINTR());
        return ret;
    }
    /* } Do not re-wrap commented-out code.  @formatter:on */

    /* { Do not re-wrap commented-out code.  @formatter:off */
    // 246  /*
    // 247   * Wrapper for dup2 - same semantics as dup2 system call except
    // 248   * that any threads blocked in an I/O system call on fd2 will be
    // 249   * preempted and return -1/EBADF;
    // 250   */
    // 251  int NET_Dup2(int fd, int fd2) {
     public int NET_Dup2(int fd, int fd2) {
         // 252      if (fd < 0) {
         if (fd < 0) {
             // 253          errno = EBADF;
             Errno.set_errno(Errno.EBADF());
             // 254          return -1;
             return -1;
         }
         // 256      return closefd(fd, fd2);
         return closefd(fd, fd2);
    }
    /* } Do not re-wrap commented-out code.  @formatter:on */

    /* { Do not re-wrap commented-out code.  @formatter:off */
    // 259  /*
    // 260   * Wrapper for close - same semantics as close system call
    // 261   * except that any threads blocked in an I/O on fd will be
    // 262   * preempted and the I/O system call will return -1/EBADF.
    // 263   */
    // 264  int NET_SocketClose(int fd) {
    public int NET_SocketClose(int fd) {
        // 265      return closefd(-1, fd);
        return closefd(-1, fd);
    }
    /* } Do not re-wrap commented-out code.  @formatter:on */

    // 291 int NET_Read(int s, void* buf, size_t len) {
    public int NET_Read(int s, PointerBase buf, long len) {
        // 292 BLOCKING_IO_RETURN_INT( s, recv(s, buf, len, 0) );
        return BLOCKING_IO_RETURN_INT(s, () -> {
            return (int) (Socket.recv(s, buf, WordFactory.unsigned(len), 0).rawValue());
        });
    }

    // 295 int NET_NonBlockingRead(int s, void* buf, size_t len) {
    public int NET_NonBlockingRead(int s, PointerBase buf, long len) {
        // 296 BLOCKING_IO_RETURN_INT( s, recv(s, buf, len, MSG_DONTWAIT));
        return BLOCKING_IO_RETURN_INT(s, () -> {
            return (int) (Socket.recv(s, buf, WordFactory.unsigned(len), Socket.MSG_DONTWAIT()).rawValue());
        });
    }

    // 299 int NET_ReadV(int s, const struct iovec * vector, int count) {
    public int NET_ReadV(int s, Uio.iovec vector, int count) {
        // 300 BLOCKING_IO_RETURN_INT( s, readv(s, vector, count) );
        return BLOCKING_IO_RETURN_INT(s, () -> {
            return (int) (Uio.readv(s, vector, count).rawValue());
        });
    }

    /* { Do not re-wrap commented-out code.  @formatter:off */
    // 303 int NET_RecvFrom(int s, void *buf, int len, unsigned int flags,
    // 304        struct sockaddr *from, int *fromlen) {
    public int NET_RecvFrom(int s, PointerBase buf, int len, int flags, Socket.sockaddr from, CIntPointer fromlen) {
        // 305 /* casting int *fromlen -> socklen_t* Both are ints */
        // 306 BLOCKING_IO_RETURN_INT( s, recvfrom(s, buf, len, flags, from, (socklen_t *)fromlen)
        // );
        return BLOCKING_IO_RETURN_INT(s, () -> {
            return (int) (Socket.recvfrom(s, buf, WordFactory.unsigned(len), flags, from, fromlen).rawValue());
        });
    }
    /* } Do not re-wrap commented-out code.  @formatter:on */

    // 309 int NET_Send(int s, void *msg, int len, unsigned int flags) {
    public int NET_Send(int s, PointerBase msg, int len, int flags) {
        // 310 BLOCKING_IO_RETURN_INT( s, send(s, msg, len, flags) );
        return BLOCKING_IO_RETURN_INT(s, () -> {
            return (int) (Socket.send(s, msg, WordFactory.unsigned(len), flags).rawValue());
        });
    }

    // 313 int NET_WriteV(int s, const struct iovec * vector, int count) {
    public int NET_WriteV(int s, Uio.iovec vector, int count) {
        // 314 BLOCKING_IO_RETURN_INT( s, writev(s, vector, count) );
        return BLOCKING_IO_RETURN_INT(s, () -> {
            return (int) (Uio.writev(s, vector, count).rawValue());
        });
    }

    /* { Do not re-wrap commented-out code.  @formatter:off */
    // 317  int NET_SendTo(int s, const void *msg, int len,  unsigned  int
    // 318         flags, const struct sockaddr *to, int tolen) {
    public int NET_SendTo(int s, PointerBase msg, int len, int flags, Socket.sockaddr to, int tolen) {
    // 319      BLOCKING_IO_RETURN_INT( s, sendto(s, msg, len, flags, to, tolen) );
        return BLOCKING_IO_RETURN_INT(s, () -> {
            return (int) (Socket.sendto(s, msg, WordFactory.unsigned(len), flags, to, tolen).rawValue());
        });
    }
    /* } Do not re-wrap commented-out code.  @formatter:on */

    /* { Do not re-wrap commented-out code.  @formatter:off */
    // 330  int NET_Connect(int s, struct sockaddr *addr, int addrlen) {
    public int NET_Connect(int s, Socket.sockaddr addr, int addrlen) {
        // 331      BLOCKING_IO_RETURN_INT( s, connect(s, addr, addrlen) );
        return BLOCKING_IO_RETURN_INT(s, () -> {
            return Socket.connect(s,  addr, addrlen);
        });
    }
    /* } Do not re-wrap commented-out code.  @formatter:on */

    /* { Do not re-wrap commented-out code.  @formatter:off */
    // 334  #ifndef USE_SELECT
    /* USE_SELECT seems not to be defined on Darwin or Linux. */
    // 335  int NET_Poll(struct pollfd *ufds, unsigned int nfds, int timeout) {
    public int NET_Poll(Poll.pollfd ufds, int nfds, int timeout) {
        /* ufds is a C array of Poll.pollfd instances. */
        // 336      BLOCKING_IO_RETURN_INT( ufds[0].fd, poll(ufds, nfds, timeout) );
        return BLOCKING_IO_RETURN_INT(ufds.fd(), () -> {
            return Poll.poll(ufds, nfds, timeout);
        });
    }
    // 338  #else
    // 339  int NET_Select(int s, fd_set *readfds, fd_set *writefds,
    // 340                 fd_set *exceptfds, struct timeval *timeout) {
    // 341      BLOCKING_IO_RETURN_INT( s-1,
    // 342                              select(s, readfds, writefds, exceptfds, timeout) );
    // 343  }
    // 344  #endif
    /* } Do not re-wrap commented-out code.  @formatter:on */

    // 52 extern int NET_Timeout0(int s, long timeout, long currentTime);
    public abstract int NET_Timeout0(int s, long timeout, long currentTime);

    // 65 extern int NET_Accept(int s, struct sockaddr *addr, int *addrlen);
    public abstract int NET_Accept(int s, Socket.sockaddr addr, CIntPointer addrlen);

    /* } Do not reformat commented out C code: @formatter:on */
    /* } Allow method names with underscores: Checkstyle: resume */
}
