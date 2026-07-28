/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.test;

import java.util.Set;

import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.svm.shared.util.ClassUtil;
import com.oracle.svm.util.AnnotationUtil;

import jdk.graal.compiler.core.test.VerifyPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class VerifySafeCCalls extends VerifyPhase<CoreProviders> {
    /** POSIX functions that need not be thread-safe: System Interfaces / General / Threads. */
    private static final Set<String> POSIX_NON_THREAD_SAFE_FUNCTIONS = Set.of(
                    // POSIX.1-2024
                    "asctime", "atomic_init", "catgets", "crypt", "ctime", "dbm_clearerr", "dbm_close", "dbm_delete", "dbm_error", "dbm_fetch", "dbm_firstkey", "dbm_nextkey", "dbm_open", "dbm_store",
                    "drand48", "encrypt", "endgrent", "endpwent", "endutxent", "getdate", "getgrent", "getgrgid", "getgrnam", "gethostent", "getlogin", "getnetbyaddr", "getnetbyname",
                    "getnetent", "getopt", "getprotobyname", "getprotobynumber", "getprotoent", "getpwent", "getpwnam", "getpwuid", "getservbyname", "getservbyport", "getservent", "getutxent",
                    "getutxid", "getutxline", "gmtime", "hcreate", "hdestroy", "hsearch", "inet_ntoa", "l64a", "localeconv", "localtime", "lrand48", "mblen", "mbtowc", "mrand48", "nftw",
                    "nl_langinfo", "ptsname", "putenv", "pututxline", "rand", "setenv", "setgrent", "setkey", "setlocale", "setpwent", "setutxent", "srand", "strerror", "strsignal", "strtok",
                    "ttyname", "unsetenv", "wctomb",
                    // "dlerror": no alternative, modern implementations are thread-safe

                    // POSIX.1-2018 without the above
                    "basename", "dirname", "ftw", "lgamma", "lgammaf", "lgammal", "system",
                    // "getenv": no alternative, implementations are thread-safe wrt other getenv
                    // "readdir": modern implementations are thread-safe, readdir_r is deprecated

                    // POSIX.1-2008 without the above
                    "getc_unlocked", "getchar_unlocked");

    /** Functions considered not safe by the CMU SEI CERT C Coding Standard, without Annex K. */
    private static final Set<String> SEI_CERT_C_MSC24C_FUNCTIONS = Set.of(
                    "gets", // deprecated in C99 and eliminated from C11

                    // obsolescent
                    "asctime", "ctime", // non-reentrant
                    // "fopen", "freopen", // no exclusive access to file
                    "atof", "atoi", "atol", "atoll", "rewind", "setbuf" // no error detection
    );

    /** Other functions commonly considered not safe. */
    private static final Set<String> OTHER_NONSAFE_FUNCTIONS = Set.of("bcmp", "bcopy", "bzero", "getpw", "strcpy", "strncpy", "stpncpy", "strcat", "strncat", "strtok",
                    "vfork", "mktemp", "tempnam", "tmpnam");

    /** C23 library varargs functions, without Annex K. */
    private static final Set<String> C_VARARG_FUNCTIONS = Set.of(
                    "printf", "scanf", "snprintf", "sprintf", "sscanf", "fprintf", "fscanf", "fwprintf", "fwscanf", "swprintf", "swscanf", "wscanf", "wprintf");

    /** POSIX.1-2024 library varargs functions (without C library). */
    private static final Set<String> POSIX_VARARG_FUNCTIONS = Set.of(
                    "asprintf", "open", "openat", "fcntl", "ioctl", "strfmon", "strfmon_l", "mq_open", "sem_open", "dprintf", "syslog", "semctl", "execl", "execle", "execlp");

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod target = t.targetMethod();
            CFunction fun = AnnotationUtil.getAnnotation(target, CFunction.class);
            if (fun == null) {
                continue;
            }
            String name = fun.value();
            if (name == null || name.isEmpty()) {
                name = target.getName();
            }
            if (POSIX_NON_THREAD_SAFE_FUNCTIONS.contains(name)) {
                throw new VerificationError(t.invoke(), "Call to non-thread-safe POSIX function '%s', use a safe alternative (usually suffixed '_r') instead.",
                                name, ClassUtil.getUnqualifiedName(getClass()));
            }
            if (SEI_CERT_C_MSC24C_FUNCTIONS.contains(name)) {
                throw new VerificationError(t.invoke(), "Call to C function '%s' deemed insecure by SEI CERT C Coding Standard, use a safe alternative instead.",
                                name, ClassUtil.getUnqualifiedName(getClass()));
            }
            if (OTHER_NONSAFE_FUNCTIONS.contains(name)) {
                throw new VerificationError(t.invoke(), "Call to C function '%s' which is considered not safe, use a safe alternative instead.",
                                name, ClassUtil.getUnqualifiedName(getClass()));
            }
            if (C_VARARG_FUNCTIONS.contains(name) || POSIX_VARARG_FUNCTIONS.contains(name)) {
                throw new VerificationError(t.invoke(), "Call to C function '%s' which uses varargs. Vararg calls differ from regular calls with some ABIs, which we don't implement.",
                                name, ClassUtil.getUnqualifiedName(getClass()));
            }
        }
    }
}
