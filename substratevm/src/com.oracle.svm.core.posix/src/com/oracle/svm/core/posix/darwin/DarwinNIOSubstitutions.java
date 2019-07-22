/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.headers.Errno;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Socket;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.darwin.DarwinEvent;

@Platforms({Platform.DARWIN.class})
public final class DarwinNIOSubstitutions {

    /* Private constructor: No instances. */
    private DarwinNIOSubstitutions() {
    }

    /* { Do not reformat commented-out code: @formatter:off */
    /** Translations of jdk/src/solaris/native/sun/nio/ch/KQueue.c?v=Java_1.8.0_40_b10. */
    @Platforms({Platform.DARWIN.class})
    @TargetClass(className = "sun.nio.ch.KQueue")
    static final class Target_sun_nio_ch_KQueue {

        // 039 JNIEXPORT jint JNICALL
        // 040 Java_sun_nio_ch_KQueue_keventSize(JNIEnv* env, jclass this)
        // 041 {
        @Substitute
        static int keventSize() {
            // 042     return sizeof(struct kevent);
            return SizeOf.get(DarwinEvent.kevent.class);
        }

        // 045 JNIEXPORT jint JNICALL
        // 046 Java_sun_nio_ch_KQueue_identOffset(JNIEnv* env, jclass this)
        // 047 {
        @Substitute
        static int identOffset() {
            // 048     return offsetof(struct kevent, ident);
            return DarwinEvent.kevent.offsetOf_ident();
        }

        // 051 JNIEXPORT jint JNICALL
        // 052 Java_sun_nio_ch_KQueue_filterOffset(JNIEnv* env, jclass this)
        // 053 {
        @Substitute
        static int filterOffset() {
            // 054     return offsetof(struct kevent, filter);
            return DarwinEvent.kevent.offsetOf_filter();
        }

        // 057 JNIEXPORT jint JNICALL
        // 058 Java_sun_nio_ch_KQueue_flagsOffset(JNIEnv* env, jclass this)
        // 059 {
        @Substitute
        static int flagsOffset() {
            // 060     return offsetof(struct kevent, flags);
            return DarwinEvent.kevent.offsetOf_flags();
        }

        // static native int kqueue() throws IOException;
        @Substitute
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        static int kqueue() throws IOException {
            return Util_sun_nio_ch_KQueue.create();
        }

        // static native int create() throws IOException;
        @Substitute
        @TargetElement(onlyWith = JDK11OrLater.class)
        static int create() throws IOException {
            return Util_sun_nio_ch_KQueue.create();
        }

        // static native int keventRegister(int kqpfd, int fd, int filter, int flags);
        @Substitute
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        static int keventRegister(int kqfd, int fd, int filter, int flags) {
            return Util_sun_nio_ch_KQueue.register(kqfd, fd, filter, flags);
        }

        // static native int register(int kqfd, int fd, int filter, int flags);
        @Substitute
        @TargetElement(onlyWith = JDK11OrLater.class)
        static int register(int kqfd, int fd, int filter, int flags) {
            return Util_sun_nio_ch_KQueue.register(kqfd, fd, filter, flags);
        }

        // static native int keventPoll(int kqpfd, long pollAddress, int nevents) throws IOException;
        @Substitute
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        static int keventPoll(int kqfd, long address, int nevents) throws IOException {
            return Util_sun_nio_ch_KQueue.poll(kqfd, address, nevents);
        }

        // static native int poll(int kqfd, long pollAddress, int nevents, long timeout) throws IOException;
        @Substitute
        @TargetElement(onlyWith = JDK11OrLater.class)
        @SuppressWarnings({"unused"})
        static int poll(int kqfd, long pollAddress, int nevents, long timeout) throws IOException {
            return Util_sun_nio_ch_KQueue.poll(kqfd, pollAddress, nevents);
        }
    }
    /* } Do not reformat commented-out code: @formatter:off */

    /**
     * Using the method names from JDK-11. E.g., open/src/java.base/macosx/native/libnio/ch/KQueue.c
     */
    static class Util_sun_nio_ch_KQueue {

        /* { Do not reformat commented-out code: @formatter:off */
        // 063 JNIEXPORT jint JNICALL
        // 064 Java_sun_nio_ch_KQueue_create(JNIEnv *env, jclass c) {
        static int create() throws IOException {
            // 065     int kqfd = kqueue();
            int kqfd = DarwinEvent.kqueue();
            // 066     if (kqfd < 0) {
            if (kqfd < 0) {
                // 067         JNU_ThrowIOExceptionWithLastError(env, "kqueue failed");
                throw PosixUtils.newIOExceptionWithLastError("kqueue failed");
            }
            // 069     return kqfd;
            return kqfd;
        }
        /* } Do not reformat commented-out code: @formatter:on */

        /* { Do not reformat commented-out code: @formatter:off */
        // 071
        // 072 JNIEXPORT jint JNICALL
        // 073 Java_sun_nio_ch_KQueue_register(JNIEnv *env, jclass c, jint kqfd,
        // 074                                       jint fd, jint filter, jint flags)
        // 075
        // 076 {
        @Substitute
        static int register(int kqfd, int fd, int filter, int flags) {
            // 077     struct kevent changes[1];
            DarwinEvent.kevent changes = StackValue.get(1, DarwinEvent.kevent.class);
            //  078     struct timespec timeout = {0, 0};
            Time.timespec timeout = StackValue.get(Time.timespec.class);
            timeout.set_tv_sec(0);
            timeout.set_tv_nsec(0);
            // 079     int res;
            int res;
            // 081     EV_SET(&changes[0], fd, filter, flags, 0, 0, 0);
            DarwinEvent.EV_SET(changes.addressOf(0), WordFactory.unsigned(fd), (short) filter, (short) flags, 0, WordFactory.signed(0), WordFactory.nullPointer());
            // 082     RESTARTABLE(kevent(kqfd, &changes[0], 1, NULL, 0, &timeout), res);
            do {
                res = DarwinEvent.kevent(kqfd, changes, 1, WordFactory.nullPointer(), 0, timeout);
            } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
            // 083     return (res == -1) ? errno : 0;
            return (res == -1) ? Errno.errno() : 0;
        }
        /* } Do not reformat commented-out code: @formatter:on */

        /* { Do not reformat commented-out code: @formatter:off */
        // 086 JNIEXPORT jint JNICALL
        // 087 Java_sun_nio_ch_KQueue_poll(JNIEnv *env, jclass c,
        // 088                                   jint kqfd, jlong address, jint nevents)
        // 089 {
        @Substitute
        static int poll(int kqfd, long address, int nevents) throws IOException {
            // 090     struct kevent *events = jlong_to_ptr(address);
            DarwinEvent.kevent events = WordFactory.pointer(address);
            // 091     int res;
            int res;
            // 093     RESTARTABLE(kevent(kqfd, NULL, 0, events, nevents, NULL), res);
            do {
                res = DarwinEvent.kevent(kqfd, WordFactory.nullPointer(), 0, events, nevents, WordFactory.nullPointer());
            } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
            // 094     if (res < 0) {
            if (res < 0) {
                // 095         JNU_ThrowIOExceptionWithLastError(env, "kqueue failed");
                throw PosixUtils.newIOExceptionWithLastError("kqueue failed");
            }
            // 097     return res;
            return res;
        }
        /* } Do not reformat commented-out code: @formatter:on */
    }

    /* { Do not reformat commented-out code: @formatter:off */
    /** Translations of jdk/src/macosx/native/sun/nio/ch/KQueueArrayWrapper.c?v=Java_1.8.0_40_b10. */
    @Platforms({Platform.DARWIN.class})
    @TargetClass(className = "sun.nio.ch.KQueueArrayWrapper", onlyWith = JDK8OrEarlier.class)
    static final class Target_sun_nio_ch_KQueueArrayWrapper {

        // 097 JNIEXPORT jint JNICALL
        // 098 Java_sun_nio_ch_KQueueArrayWrapper_init(JNIEnv *env, jobject this)
        // 099 {
        @SuppressWarnings("static-method")
        @Substitute
        int init() throws IOException {
            // 100     int kq = kqueue();
            int kq = DarwinEvent.kqueue();
            // 101     if (kq < 0) {
            if (kq < 0) {
                // 102         JNU_ThrowIOExceptionWithLastError(env, "KQueueArrayWrapper: kqueue() failed");
                throw PosixUtils.newIOExceptionWithLastError("KQueueArrayWrapper: kqueue() failed");
            }
            // 104     return kq;
            return kq;
        }

        // 108 JNIEXPORT void JNICALL
        // 109 Java_sun_nio_ch_KQueueArrayWrapper_register0(JNIEnv *env, jobject this,
        // 110                                              jint kq, jint fd, jint r, jint w)
        // 111 {
        @SuppressWarnings("static-method")
        @Substitute
        void register0(int kq, int fd, int r, int w) {
            // 112     struct kevent changes[2];
            DarwinEvent.kevent changes = StackValue.get(2, DarwinEvent.kevent.class);
            // 113     struct kevent errors[2];
            DarwinEvent.kevent errors = StackValue.get(2, DarwinEvent.kevent.class);
            // 114     struct timespec dontBlock = {0, 0};
            Time.timespec dontBlock = StackValue.get(Time.timespec.class);
            dontBlock.set_tv_sec(0);
            dontBlock.set_tv_nsec(0);
            // 115
            // 116     // if (r) then { register for read } else { unregister for read }
            // 117     // if (w) then { register for write } else { unregister for write }
            // 118     // Ignore errors - they're probably complaints about deleting non-
            // 119     //   added filters - but provide an error array anyway because
            // 120     //   kqueue behaves erratically if some of its registrations fail.
            // 121     EV_SET(&changes[0], fd, EVFILT_READ,  r ? EV_ADD : EV_DELETE, 0, 0, 0);
            DarwinEvent.EV_SET(changes.addressOf(0),
                            WordFactory.unsigned(fd),
                            (short) DarwinEvent.EVFILT_READ(),
                            (short) (CTypeConversion.toBoolean(r) ? DarwinEvent.EV_ADD() : DarwinEvent.EV_DELETE()),
                            0,
                            WordFactory.zero(),
                            WordFactory.nullPointer());
            // 122     EV_SET(&changes[1], fd, EVFILT_WRITE, w ? EV_ADD : EV_DELETE, 0, 0, 0);
            DarwinEvent.EV_SET(changes.addressOf(1),
                            WordFactory.unsigned(fd),
                            (short) DarwinEvent.EVFILT_WRITE(),
                            (short) (CTypeConversion.toBoolean(w) ? DarwinEvent.EV_ADD() : DarwinEvent.EV_DELETE()),
                            0,
                            WordFactory.zero(),
                            WordFactory.nullPointer());
            // 123     kevent(kq, changes, 2, errors, 2, &dontBlock);
            DarwinEvent.kevent(kq, changes, 2, errors, 2, dontBlock);
        }

        // 127 JNIEXPORT jint JNICALL
        // 128 Java_sun_nio_ch_KQueueArrayWrapper_kevent0(JNIEnv *env, jobject this, jint kq,
        // 129                                            jlong kevAddr, jint kevCount,
        // 130                                            jlong timeout)
        // 131 {
        @SuppressWarnings("static-method")
        @Substitute
        int kevent0(int kq, long kevAddr, int kevCount, long timeout) throws IOException {
            // 132     struct kevent *kevs = (struct kevent *)jlong_to_ptr(kevAddr);
            DarwinEvent.kevent keys = WordFactory.pointer(kevAddr);
            // 133     struct timespec ts;
            Time.timespec ts = StackValue.get(Time.timespec.class);
            // 134     struct timespec *tsp;
            Time.timespec tsp;
            // 135     int result;
            int result;
            // 136
            // 137     // Java timeout is in milliseconds. Convert to struct timespec.
            // 138     // Java timeout == -1 : wait forever : timespec timeout of NULL
            // 139     // Java timeout == 0  : return immediately : timespec timeout of zero
            // 140     if (timeout >= 0) {
            if (timeout >= 0) {
                // 141         ts.tv_sec = timeout / 1000;
                ts.set_tv_sec(timeout / 1000);
                // 142         ts.tv_nsec = (timeout % 1000) * 1000000; //nanosec = 1 million millisec
                ts.set_tv_nsec((timeout % 1000) * 1000000);
                // 143         tsp = &ts;
                tsp = ts;
            } else {
                // 145         tsp = NULL;
                tsp = WordFactory.nullPointer();
            }
            // 148     result = kevent(kq, NULL, 0, kevs, kevCount, tsp);
            result = DarwinEvent.kevent(kq, WordFactory.nullPointer(), 0, keys, kevCount, tsp);
            // 150     if (result < 0) {
            if (result < 0) {
                // 151         if (errno == EINTR) {
                if (Errno.errno() == Errno.EINTR()) {
                    // 152             // ignore EINTR, pretend nothing was selected
                    // 153             result = 0;
                    result = 0;

                } else {
                    // 155             JNU_ThrowIOExceptionWithLastError(env, "KQueueArrayWrapper: kqueue failed");
                    throw PosixUtils.newIOExceptionWithLastError("KQueueArrayWrapper: kqueue failed");
                }
            }
            // 159     return result;
            return result;
        }

        // 163 JNIEXPORT void JNICALL
        // 164 Java_sun_nio_ch_KQueueArrayWrapper_interrupt(JNIEnv *env, jclass cls, jint fd)
        // 165 {
        @Substitute
        static void interrupt(int fd) throws IOException {
            // 166     char c = 1;
            CCharPointer cPointer = StackValue.get(CCharPointer.class);
            cPointer.write((byte) 1);
            // 167     if (1 != write(fd, &c, 1)) {
            if (1 != (int) Unistd.write(fd, cPointer, WordFactory.unsigned(1)).rawValue()) {
                // 168         JNU_ThrowIOExceptionWithLastError(env, "KQueueArrayWrapper: interrupt failed");
                throw PosixUtils.newIOExceptionWithLastError("KQueueArrayWrapper: interrupt failed");
            }
        }
    }
    /* } @formatter:on */

    /* { Do not reformat commented-out code: @formatter:off */
    /** Translations of jdk/src/solaris/native/sun/nio/ch/KQueuePort.c?v=Java_1.8.0_40_b10. */
    @Platforms({Platform.DARWIN.class})
    @TargetClass(className = "sun.nio.ch.KQueuePort")
    static final class Target_sun_nio_ch_KQueuePort {

        // 038 JNIEXPORT void JNICALL
        // 039 Java_sun_nio_ch_KQueuePort_socketpair(JNIEnv* env, jclass clazz, jintArray sv) {
        @Substitute
        @TargetElement(onlyWith = JDK8OrEarlier.class)
       static void socketpair(int[] sv) throws IOException {
            // 040     int sp[2];
            CIntPointer sp = StackValue.get(2, CIntPointer.class);
            // 041     if (socketpair(PF_UNIX, SOCK_STREAM, 0, sp) == -1) {
            if (Socket.socketpair(Socket.PF_UNIX(), Socket.SOCK_STREAM(), 0, sp) == -1) {
                // 042         JNU_ThrowIOExceptionWithLastError(env, "socketpair failed");
                throw PosixUtils.newIOExceptionWithLastError("socketpair failed");
            } else {
                // 044         jint res[2];
                // 045         res[0] = (jint)sp[0];
                // 046         res[1] = (jint)sp[1];
                // 047         (*env)->SetIntArrayRegion(env, sv, 0, 2, &res[0]);
                sv[0] = sp.read(0);
                sv[1] = sp.read(1);
            }
        }

        // 051 JNIEXPORT void JNICALL
        // 052 Java_sun_nio_ch_KQueuePort_interrupt(JNIEnv *env, jclass c, jint fd) {
        @Substitute
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        static void interrupt(int fd) throws IOException {
            // 053     int res;
            int res;
            // 054     int buf[1];
            CIntPointer buf = StackValue.get(1, CIntPointer.class);
            // 055     buf[0] = 1;
            buf.write(0, 1);
            // 056     RESTARTABLE(write(fd, buf, 1), res);
            do {
                res = (int) Unistd.write(fd, buf, WordFactory.unsigned(1)).rawValue();
            } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
            // 057     if (res < 0) {
            if (res < 0) {
                // 058         JNU_ThrowIOExceptionWithLastError(env, "write failed");
                throw PosixUtils.newIOExceptionWithLastError("write failed");
            }
        }

        // 062 JNIEXPORT void JNICALL
        // 063 Java_sun_nio_ch_KQueuePort_drain1(JNIEnv *env, jclass cl, jint fd) {
        @Substitute
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        static void drain1(int fd) throws IOException {
            // 064     int res;
            int res;
            // 065     char buf[1];
            CIntPointer buf = StackValue.get(1, CIntPointer.class);
            // 066     RESTARTABLE(read(fd, buf, 1), res);
            do {
                res = (int) Unistd.read(fd, buf, WordFactory.unsigned(1)).rawValue();
            } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
            // 067     if (res < 0) {
            if (res < 0) {
                // 068         JNU_ThrowIOExceptionWithLastError(env, "drain1 failed");
                throw PosixUtils.newIOExceptionWithLastError("drain1 failed");
            }
        }

        // 072 JNIEXPORT void JNICALL
        // 073 Java_sun_nio_ch_KQueuePort_close0(JNIEnv *env, jclass c, jint fd) {
        @Substitute
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
