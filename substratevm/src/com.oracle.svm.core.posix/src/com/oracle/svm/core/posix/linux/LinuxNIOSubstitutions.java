/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.headers.Errno;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.posix.PosixJavaNIOSubstitutions;
import com.oracle.svm.core.posix.headers.Socket;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.linux.LinuxEPoll;

@Platforms({Platform.LINUX.class})
public final class LinuxNIOSubstitutions {

    /* Private constructor: No instances. */
    private LinuxNIOSubstitutions() {
    }

    /* { Do not reformat commented-out code: @formatter:off */
    /** Translations of jdk/src/solaris/native/sun/nio/ch/EPoll.c?v=Java_1.8.0_40_b10. */
    @Platforms({Platform.LINUX.class})
    @TargetClass(className = "sun.nio.ch.EPoll")
    static final class Target_sun_nio_ch_EPoll {

        // 039 JNIEXPORT jint JNICALL
        // 040 Java_sun_nio_ch_EPoll_eventSize(JNIEnv* env, jclass this)
        // 041 {
        @Substitute
        static int eventSize() {
            // 042     return sizeof(struct epoll_event);
            return SizeOf.get(LinuxEPoll.epoll_event.class);
        }

        // 045 JNIEXPORT jint JNICALL
        // 046 Java_sun_nio_ch_EPoll_eventsOffset(JNIEnv* env, jclass this)
        // 047 {
        @Substitute
        static int eventsOffset() {
            // 048     return offsetof(struct epoll_event, events);
            return LinuxEPoll.epoll_event.offsetOfevents();
        }

        // 051 JNIEXPORT jint JNICALL
        // 052 Java_sun_nio_ch_EPoll_dataOffset(JNIEnv* env, jclass this)
        // 053 {
        @Substitute
        static int dataOffset() {
            // 054     return offsetof(struct epoll_event, data);
            return LinuxEPoll.epoll_event.offsetOfdata();
        }

        /* { Do not reformat commented-out code: @formatter:off */
        // 057 JNIEXPORT jint JNICALL
        // 058 Java_sun_nio_ch_EPoll_epollCreate(JNIEnv *env, jclass c) {
        @Substitute //
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        static int epollCreate() throws IOException {
            // 059     /*
            // 060      * epoll_create expects a size as a hint to the kernel about how to
            // 061      * dimension internal structures. We can't predict the size in advance.
            // 062      */
            // 063     int epfd = epoll_create(256);
            int epfd = LinuxEPoll.epoll_create(256);
            // 064     if (epfd < 0) {
            if (epfd < 0) {
                // 065        JNU_ThrowIOExceptionWithLastError(env, "epoll_create failed");
                throw new IOException("epoll_create failed");
            }
            // 067     return epfd;
            return epfd;
        }
        /* } Do not reformat commented-out code: @formatter:on */

        /* { Do not reformat commented-out code: @formatter:off */
        //    58  JNIEXPORT jint JNICALL
        //    59  Java_sun_nio_ch_EPoll_create(JNIEnv *env, jclass clazz) {
        @Substitute //
        @TargetElement(onlyWith = JDK11OrLater.class) //
        static int create() throws IOException {
            //    60      /* size hint not used in modern kernels */
            //    61      int epfd = epoll_create(256);
            int epfd = LinuxEPoll.epoll_create(256);
            //    62      if (epfd < 0) {
            if (epfd < 0) {
                //    63          JNU_ThrowIOExceptionWithLastError(env, "epoll_create failed");
                throw new IOException("epoll_create failed");
            }
            //    64      }
            //    65      return epfd;
            return epfd;
        }
        /* } Do not reformat commented-out code: @formatter:on */

        /* { Do not reformat commented-out code: @formatter:off */
        // 070 JNIEXPORT jint JNICALL
        // 071 Java_sun_nio_ch_EPoll_epollCtl(JNIEnv *env, jclass c, jint epfd,
        // 072                                    jint opcode, jint fd, jint events)
        // 073 {
        @Substitute //
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        static int epollCtl(int epfd, int opcode, int fd, int events) {
            // 074     struct epoll_event event;
            LinuxEPoll.epoll_event event = StackValue.get(LinuxEPoll.epoll_event.class);
            // 075     int res;
            int res;
            // 076
            // 077     event.events = events;
            event.events(events);
            // 078     event.data.fd = fd;
            event.addressOfdata().fd(fd);
            // 079
            // 080     RESTARTABLE(epoll_ctl(epfd, (int)opcode, (int)fd, &event), res);
            do {
                res = LinuxEPoll.epoll_ctl(epfd, opcode, fd, event);
            } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
            // 082     return (res == 0) ? 0 : errno;
            return (res == 0) ? 0 : Errno.errno();
        }
        /* } Do not reformat commented-out code: @formatter:on */

        // 68 JNIEXPORT jint JNICALL
        // 69 Java_sun_nio_ch_EPoll_ctl(JNIEnv *env, jclass clazz, jint epfd,
        // 70 jint opcode, jint fd, jint events)
        // 71 {
        @Substitute //
        @TargetElement(onlyWith = JDK11OrLater.class) //
        static int ctl(int epfd, int opcode, int fd, int events) {
            // 72 struct epoll_event event;
            LinuxEPoll.epoll_event event = StackValue.get(LinuxEPoll.epoll_event.class);
            // 73 int res;
            int res;
            // 74
            // 75 event.events = events;
            event.events(events);
            // 76 event.data.fd = fd;
            event.addressOfdata().fd(fd);
            // 77
            // 78 res = epoll_ctl(epfd, (int)opcode, (int)fd, &event);
            res = LinuxEPoll.epoll_ctl(epfd, opcode, fd, event);
            // 79 return (res == 0) ? 0 : errno;
            return (res == 0) ? 0 : Errno.errno();
            // 80 }
        }

        /* { Do not reformat commented-out code: @formatter:off */
        // 085 JNIEXPORT jint JNICALL
        // 086 Java_sun_nio_ch_EPoll_epollWait(JNIEnv *env, jclass c,
        // 087                                     jint epfd, jlong address, jint numfds)
        // 088 {
        @Substitute //
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        static int epollWait(int epfd, long address, int numfds) throws IOException {
            // 089     struct epoll_event *events = jlong_to_ptr(address);
            LinuxEPoll.epoll_event events = WordFactory.pointer(address);
            // 090     int res;
            int res;
            // 091
            // 092     RESTARTABLE(epoll_wait(epfd, events, numfds, -1), res);
            do {
                res = LinuxEPoll.epoll_wait(epfd, events, numfds, -1);
            } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
            // 093     if (res < 0) {
            if (res < 0) {
                // 094         JNU_ThrowIOExceptionWithLastError(env, "epoll_wait failed");
                throw new IOException("epoll_wait failed");
            }
            // 096     return res;
            return res;
        }
        /* } Do not reformat commented-out code: @formatter:on */

        // 82 JNIEXPORT jint JNICALL
        // 83 Java_sun_nio_ch_EPoll_wait(JNIEnv *env, jclass clazz, jint epfd,
        // 84 jlong address, jint numfds, jint timeout)
        // 85 {
        @Substitute //
        @TargetElement(onlyWith = JDK11OrLater.class) //
        @SuppressWarnings({"unused"})
        static int wait(int epfd, long address, int numfds, int timeout) throws IOException {
            // 86 struct epoll_event *events = jlong_to_ptr(address);
            LinuxEPoll.epoll_event events = WordFactory.pointer(address);
            // 87 int res = epoll_wait(epfd, events, numfds, timeout);
            int res = LinuxEPoll.epoll_wait(epfd, events, numfds, timeout);
            // 88 if (res < 0) {
            if (res < 0) {
                // 89 if (errno == EINTR) {
                if (Errno.errno() == Errno.EINTR()) {
                    // 90 return IOS_INTERRUPTED;
                    return PosixJavaNIOSubstitutions.Target_sun_nio_ch_IOStatus.IOS_INTERRUPTED;
                    // 91 } else {
                } else {
                    // 92 JNU_ThrowIOExceptionWithLastError(env, "epoll_wait failed");
                    throw new IOException("epoll_wait failed");
                    // 93 return IOS_THROWN;
                    // not reached
                    // 94 }
                }
                // 95 }
            }
            // 96 return res;
            return res;
            // 97 }
        }

        /* This method appears in EPoll.c, but is not declared in EPoll.java. */
        /* { Do not reformat commented-out code: @formatter:off */
        // 099 JNIEXPORT void JNICALL
        // 100 Java_sun_nio_ch_EPoll_close0(JNIEnv *env, jclass c, jint epfd) {
        // @Substitute
        // static void close0(int epfd) {
        //     // 101     int res;
        //     int res;
        //     // 102     RESTARTABLE(close(epfd), res);
        //     do {
        //         do {
        //             res = Unistd.close(epfd);
        //         } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
        //     } while (false);
        // }
        /* } Do not reformat commented-out code: @formatter:on */
    }
    /* } @formatter:on */

    /* { Do not reformat commented-out code: @formatter:off */
    /** Translations of jdk/src/solaris/native/sun/nio/ch/EPollArrayWrapper.c?v=Java_1.8.0_40_b10. */
    @Platforms({Platform.LINUX.class})
    @TargetClass(className = "sun.nio.ch.EPollArrayWrapper", onlyWith = JDK8OrEarlier.class)
    static final class Target_sun_nio_ch_EPollArrayWrapper {

        /* The translation of RESTARTABLE is to expand the body without the wrapper
         *     do { .... } while (0)
         * whose purpose is to make the macro expansion into a single C statement.
         */
        // 037 #define RESTARTABLE(_cmd, _result) do { \
        // 038   do { \
        // 039     _result = _cmd; \
        // 040   } while((_result == -1) && (errno == EINTR)); \
        // 041 } while(0)

        // 074 JNIEXPORT void JNICALL
        // 075 Java_sun_nio_ch_EPollArrayWrapper_init(JNIEnv *env, jclass this)
        // 076 {
        @Substitute
        static void init() {
        }

        // 079 JNIEXPORT jint JNICALL
        // 080 Java_sun_nio_ch_EPollArrayWrapper_epollCreate(JNIEnv *env, jobject this)
        // 081 {
        @Substitute
        @SuppressWarnings("static-method")
        int epollCreate() throws IOException {
            // 082     /*
            // 083      * epoll_create expects a size as a hint to the kernel about how to
            // 084      * dimension internal structures. We can't predict the size in advance.
            // 085      */
            // 086     int epfd = epoll_create(256);
            int epfd = LinuxEPoll.epoll_create(256);
            // 087     if (epfd < 0) {
            if (epfd < 0) {
                // 088        JNU_ThrowIOExceptionWithLastError(env, "epoll_create failed");
                throw new IOException("epoll_create failed");
            }
            // 090     return epfd;
            return epfd;
        }

        // 093 JNIEXPORT jint JNICALL
        // 094 Java_sun_nio_ch_EPollArrayWrapper_sizeofEPollEvent(JNIEnv* env, jclass this)
        // 095 {
        @Substitute
        static int sizeofEPollEvent() {
            // 096     return sizeof(struct epoll_event);
            return SizeOf.get(LinuxEPoll.epoll_event.class);
        }

        // 099 JNIEXPORT jint JNICALL
        // 100 Java_sun_nio_ch_EPollArrayWrapper_offsetofData(JNIEnv* env, jclass this)
        // 101 {
        @Substitute
        static int offsetofData() {
            // 102     return offsetof(struct epoll_event, data);
            return LinuxEPoll.epoll_event.offsetOfdata();
        }

        // 105 JNIEXPORT void JNICALL
        // 106 Java_sun_nio_ch_EPollArrayWrapper_epollCtl(JNIEnv *env, jobject this, jint epfd,
        // 107                                            jint opcode, jint fd, jint events)
        // 108 {
        @SuppressWarnings("static-method")
        @Substitute
        void epollCtl(int epfd, int opcode, int fd, int events) throws IOException {
            // 109     struct epoll_event event;
            LinuxEPoll.epoll_event event = StackValue.get(LinuxEPoll.epoll_event.class);
            // 110     int res;
            int res;
            // 111
            // 112     event.events = events;
            event.events(events);
            // 113     event.data.fd = fd;
            event.addressOfdata().fd(fd);
            // 114
            // 115     RESTARTABLE(epoll_ctl(epfd, (int)opcode, (int)fd, &event), res);
            do {
                res = LinuxEPoll.epoll_ctl(epfd, opcode, fd, event);
            } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
            // 117     /*
            // 118      * A channel may be registered with several Selectors. When each Selector
            // 119      * is polled a EPOLL_CTL_DEL op will be inserted into its pending update
            // 120      * list to remove the file descriptor from epoll. The "last" Selector will
            // 121      * close the file descriptor which automatically unregisters it from each
            // 122      * epoll descriptor. To avoid costly synchronization between Selectors we
            // 123      * allow pending updates to be processed, ignoring errors. The errors are
            // 124      * harmless as the last update for the file descriptor is guaranteed to
            // 125      * be EPOLL_CTL_DEL.
            // 126      */
            // 127     if (res < 0 && errno != EBADF && errno != ENOENT && errno != EPERM) {
            if (res < 0 && Errno.errno() != Errno.EBADF() && Errno.errno() != Errno.ENOENT() && Errno.errno() != Errno.EPERM()) {
                // 128         JNU_ThrowIOExceptionWithLastError(env, "epoll_ctl failed");
                throw new IOException("epoll_ctl failed");
            }
        }

        // 132 JNIEXPORT jint JNICALL
        // 133 Java_sun_nio_ch_EPollArrayWrapper_epollWait(JNIEnv *env, jobject this,
        // 134                                             jlong address, jint numfds,
        // 135                                             jlong timeout, jint epfd)
        // 136 {
        @SuppressWarnings("static-method")
        @Substitute
        int epollWait(long address, int numfds, long timeout, int epfd) throws IOException {
            // 137     struct epoll_event *events = jlong_to_ptr(address);
            LinuxEPoll.epoll_event events = WordFactory.pointer(address);
            // 138     int res;
            int res;
            // 139
            // 140     if (timeout <= 0) {           /* Indefinite or no wait */
            if (timeout <= 0) {
                // 141         RESTARTABLE(epoll_wait(epfd, events, numfds, timeout), res);
                do {
                    res = LinuxEPoll.epoll_wait(epfd, events, numfds, (int) timeout);
                } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
            } else {                      /* Bounded wait; bounded restarts */
                // 143         res = iepoll(epfd, events, numfds, timeout);
                res = Util_sun_nio_ch_EPollArrayWrapper.iepoll(epfd, events, numfds, timeout);
            }
            // 146     if (res < 0) {
            if (res < 0) {
                // 147         JNU_ThrowIOExceptionWithLastError(env, "epoll_wait failed");
                throw new IOException("epoll_wait failed");
            }
            // 149     return res;
            return res;
        }

        // 152 JNIEXPORT void JNICALL
        // 153 Java_sun_nio_ch_EPollArrayWrapper_interrupt(JNIEnv *env, jobject this, jint fd)
        // 154 {
        @Substitute
        static void interrupt(int fd) throws IOException {
            // 155     int fakebuf[1];
            CIntPointer fakebuf = StackValue.get(1, CIntPointer.class);
            // 156     fakebuf[0] = 1;
            fakebuf.write(0, 1);
            // 157     if (write(fd, fakebuf, 1) < 0) {
            if (Unistd.write(fd, fakebuf, WordFactory.unsigned(1)).lessThan(0)) {
                // 158         JNU_ThrowIOExceptionWithLastError(env,"write to interrupt fd failed");
                throw new IOException("write to interrupt fd failed");
            }
        }
    }
    /* } @formatter:on */

    /* { Do not reformat commented-out code: @formatter:off */
    /** Translations of jdk/src/solaris/native/sun/nio/ch/EPollArrayWrapper.c?v=Java_1.8.0_40_b10. */
    static final class Util_sun_nio_ch_EPollArrayWrapper {

        // 044 static int
        // 045 iepoll(int epfd, struct epoll_event *events, int numfds, jlong timeout)
        // 046 {
        static int iepoll(int epfd, LinuxEPoll.epoll_event events, int numfds, long timeout) {
            // 047 jlong start, now;
            long start;
            long now;
            // 048 int remaining = timeout;
            long remaining = timeout;
            // 049 struct timeval t;
            Time.timeval t = StackValue.get(Time.timeval.class);
            // 050 int diff;
            long diff;
            // 051
            // 052 gettimeofday(&t, NULL);
            Time.gettimeofday(t, WordFactory.nullPointer());
            // 053 start = t.tv_sec * 1000 + t.tv_usec / 1000;
            start = t.tv_sec() * 1000 + t.tv_usec() / 1000;
            // 055 for (;;) {
            for (;;) {
                // 056 int res = epoll_wait(epfd, events, numfds, timeout);
                int res = LinuxEPoll.epoll_wait(epfd, events, numfds, (int) timeout);
                // 057 if (res < 0 && errno == EINTR) {
                if (res < 0 && Errno.errno() == Errno.EINTR()) {
                    // 058 if (remaining >= 0) {
                    if (remaining >= 0) {
                        // 059 gettimeofday(&t, NULL);
                        Time.gettimeofday(t, WordFactory.nullPointer());
                        // 060 now = t.tv_sec * 1000 + t.tv_usec / 1000;
                        now = t.tv_sec() * 1000 + t.tv_usec() / 1000;
                        // 061 diff = now - start;
                        diff = now - start;
                        // 062 remaining -= diff;
                        remaining -= diff;
                        // 063 if (diff < 0 || remaining <= 0) {
                        if (diff < 0 || remaining <= 0) {
                            // 064 return 0;
                            return 0;
                        }
                        // 066 start = now;
                        start = now;
                    }
                } else {
                    // 069 return res;
                    return res;
                }
            }
        }
    }
    /* } @formatter:on */

    /**
     * Re-run the class initialization for {@code sun.nio.ch.EPollArrayWrapper} so that static
     * fields are re-initialized from the platform running the image.
     * <p>
     * The static initializer for {@code sun.nio.ch.EPollArrayWrapper} captures the number of file
     * descriptors available on the platform in {@code EPollArrayWrapper.OPEN_MAX} using
     * {@code IOUtil,fdLimit()}. Based on that the constructor for
     * {@code sun.nio.ch.EPollArrayWrapper} does or does not create an overflow table,
     * {@code EPollArrayWrapper.eventsHigh}. If the number of file descriptors increases between the
     * build platform and the execution platform, attempting to use the uninitialized overflow table
     * may cause a {@code NullPointerException}. Re-initializing the static fields should allow the
     * overflow table to be created if it is needed on the execution platform.
     */
    @AutomaticFeature
    static final class EPollArrayWrapperFeature implements Feature {

        @Override
        public void duringSetup(DuringSetupAccess access) {
            if (JavaVersionUtil.JAVA_SPEC <= 8) {
                /* This class only exists on JDK-8 and earlier platforms. */
                ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("sun.nio.ch.EPollArrayWrapper"), "required for substitutions");
            }
        }
    }

    /* { Do not reformat commented-out code: @formatter:off */
    /** Translations of jdk/src/solaris/native/sun/nio/ch/EPollPort.c?v=Java_1.8.0_40_b10. */
    @Platforms({Platform.LINUX.class})
    @TargetClass(className = "sun.nio.ch.EPollPort")
    static final class Target_sun_nio_ch_EPollPort {

        // 038 JNIEXPORT void JNICALL
        // 039 Java_sun_nio_ch_EPollPort_socketpair(JNIEnv* env, jclass clazz, jintArray sv) {
        @Substitute //
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        static void socketpair(int[] sv) throws IOException {
            // 040     int sp[2];
            CIntPointer sp = StackValue.get(2, CIntPointer.class);
            // 041     if (socketpair(PF_UNIX, SOCK_STREAM, 0, sp) == -1) {
            if (Socket.socketpair(Socket.PF_UNIX(), Socket.SOCK_STREAM(), 0, sp) == -1) {
                // 042         JNU_ThrowIOExceptionWithLastError(env, "socketpair failed");
                throw new IOException("socketpair failed");
            } else {
                // 044         jint res[2];
                CIntPointer res = StackValue.get(2, CIntPointer.class);
                // 045         res[0] = (jint)sp[0];
                res.write(0, sp.read(0));
                // 046         res[1] = (jint)sp[1];
                res.write(1, sp.read(1));
                // 047         (*env)->SetIntArrayRegion(env, sv, 0, 2, &res[0]);
                sv[0] = res.read(0);
                sv[1] = res.read(1);
            }
        }

        // 051 JNIEXPORT void JNICALL
        // 052 Java_sun_nio_ch_EPollPort_interrupt(JNIEnv *env, jclass c, jint fd) {
        @Substitute //
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        static void interrupt(int fd) throws IOException {
            // 053     int res;
            int res;
            // 054     int buf[1];
            CIntPointer buf = StackValue.get(CIntPointer.class);
            // 055     buf[0] = 1;
            buf.write(0, 1);
            // 056     RESTARTABLE(write(fd, buf, 1), res);
            do {
                res = (int) Unistd.write(fd, buf, WordFactory.unsigned(1)).rawValue();
            } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
            // 057     if (res < 0) {
            if (res < 0) {
                // 058         JNU_ThrowIOExceptionWithLastError(env, "write failed");
                throw new IOException("write failed");
            }
        }

        // 062 JNIEXPORT void JNICALL
        // 063 Java_sun_nio_ch_EPollPort_drain1(JNIEnv *env, jclass cl, jint fd) {
        @Substitute //
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        static void drain1(int fd) throws IOException {
            // 064     int res;
            int res;
            // 065     char buf[1];
            CCharPointer buf = StackValue.get(CCharPointer.class);
            // 066     RESTARTABLE(read(fd, buf, 1), res);
            do {
                res = (int) Unistd.read(fd, buf, WordFactory.unsigned(1)).rawValue();
            } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
            // 067     if (res < 0) {
            if (res < 0) {
                // 068         JNU_ThrowIOExceptionWithLastError(env, "drain1 failed");
                throw new IOException("drain1 failed");
            }
        }

        // 072 JNIEXPORT void JNICALL
        // 073 Java_sun_nio_ch_EPollPort_close0(JNIEnv *env, jclass c, jint fd) {
        @Substitute //
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        static void close0(int fd) {
            // 074     int res;
            int res;
            // 075     RESTARTABLE(close(fd), res);
            do {
                res = Unistd.close(fd);
            } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
        }
    }
    /* } @formatter:on */
}
