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
package com.oracle.svm.core.posix;

import java.io.IOException;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Poll;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Unistd;

/* Do not reformat commented-out code: @formatter:off */

public final class PosixSunNioSubstitutions {

    /** Translations of jdk/src/solaris/native/sun/nio/ch/PollArrayWrapper.c?v=Java_1.8.0_40_b10. */
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    @TargetClass(className = "sun.nio.ch.PollArrayWrapper", onlyWith = JDK8OrEarlier.class)
    static final class Target_sun_nio_ch_PollArrayWrapper {

        // 035 #define RESTARTABLE(_cmd, _result) do { \
        // 036   do { \
        // 037     _result = _cmd; \
        // 038   } while((_result == -1) && (errno == EINTR)); \
        // 039 } while(0)

        // 071 JNIEXPORT jint JNICALL
        // 072 Java_sun_nio_ch_PollArrayWrapper_poll0(JNIEnv *env, jobject this,
        // 073                                        jlong address, jint numfds,
        // 074                                        jlong timeout)
        // 075 {
        @Substitute
        @SuppressWarnings("static-method")
        int poll0(long address, int numfds, long timeout) throws IOException {
            // 076     struct pollfd *a;
            Poll.pollfd a;
            // 077     int err = 0;
            int err = 0;
            // 078
            // 079     a = (struct pollfd *) jlong_to_ptr(address);
            a = WordFactory.pointer(address);
            // 080
            // 081     if (timeout <= 0) {           /* Indefinite or no wait */
            if (timeout <= 0) {
                // 082         RESTARTABLE (poll(a, numfds, timeout), err);
                // 035 #define RESTARTABLE(_cmd, _result) do { \
                do {
                // 036   do { \
                    do {
                // 037     _result = _cmd; \
                        err = Poll.poll(a, numfds, (int) timeout);
                // 038   } while((_result == -1) && (errno == EINTR)); \
                    } while ((err == -1) && (Errno.errno() == Errno.EINTR()));
                // 039 } while(0)
                } while (false);
            } else {                     /* Bounded wait; bounded restarts */
                // 084         err = ipoll(a, numfds, timeout);
                err = Util_sun_nio_ch_PollArrayWrapper.ipoll(a, numfds, (int) timeout);
            }
            // 086
            // 087     if (err < 0) {
            if (err < 0) {
                // 088         JNU_ThrowIOExceptionWithLastError(env, "Poll failed");
                throw new IOException("Poll failed");
            }
            // 090     return (jint)err;
            return err;
        }

        // 093 JNIEXPORT void JNICALL
        // 094 Java_sun_nio_ch_PollArrayWrapper_interrupt(JNIEnv *env, jobject this, jint fd)
        // 095 {
        @Substitute
        static void interrupt(int fd) throws IOException {
            // 096     int fakebuf[1];
            CIntPointer fakebuf = StackValue.get(1, CIntPointer.class);
            // 097     fakebuf[0] = 1;
            fakebuf.write(0, 1);
            // 098     if (write(fd, fakebuf, 1) < 0) {
            if (Unistd.write(fd, fakebuf, WordFactory.unsigned(1)).lessThan(0)) {
                // 099          JNU_ThrowIOExceptionWithLastError(env,
                // 100                                           "Write to interrupt fd failed");
                throw new IOException("Write to interrupt fd failed");
            }
        }
    }

    static class Util_sun_nio_ch_PollArrayWrapper {

        // 041 static int
        // 042 ipoll(struct pollfd fds[], unsigned int nfds, int timeout)
        // 043 {
        static int ipoll(Poll.pollfd fds, int nfds, int timeout) {
            // 044     jlong start, now;
            long start;
            long now;
            // 045     int remaining = timeout;
            int remaining = timeout;
            // 046     struct timeval t;
            Time.timeval t = StackValue.get(Time.timeval.class);
            // 047     int diff;
            long diff;
            // 048
            // 049     gettimeofday(&t, NULL);
            Time.gettimeofday(t, WordFactory.nullPointer());
            // 050     start = t.tv_sec * 1000 + t.tv_usec / 1000;
            start = t.tv_sec() * 1000 + t.tv_usec() / 1000;
            // 051
            // 052     for (;;) {
            for (;;) {
                // 053         int res = poll(fds, nfds, remaining);
                int res = Poll.poll(fds, nfds, remaining);
                // 054         if (res < 0 && errno == EINTR) {
                if (res < 0 && Errno.errno() == Errno.EINTR()) {
                    // 055             if (remaining >= 0) {
                    if (remaining >= 0) {
                        // 056                 gettimeofday(&t, NULL);
                        Time.gettimeofday(t, WordFactory.nullPointer());
                        // 057                 now = t.tv_sec * 1000 + t.tv_usec / 1000;
                        now = t.tv_sec() * 1000 + t.tv_usec() / 1000;
                        // 058                 diff = now - start;
                        diff = now - start;
                        // 059                 remaining -= diff;
                        remaining -= diff;
                        // 060                 if (diff < 0 || remaining <= 0) {
                        if (diff < 0 || remaining <= 0) {
                            // 061                     return 0;
                            return 0;
                        }
                        // 063                 start = now;
                        start = now;
                    }
                } else {
                    // 066             return res;
                    return res;
                }
            }
        }
    }
}
