/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.locks;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.SubstrateDiagnostics.DiagnosticThunk;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.log.Log;

public abstract class VMLockSupport {
    public VMMutex[] getMutexes() {
        return null;
    }

    public VMCondition[] getConditions() {
        return null;
    }

    public static class DumpVMMutexes extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            log.string("Locked VM mutexes:").indent(true);

            VMLockSupport support = ImageSingletons.lookup(VMLockSupport.class);
            VMMutex[] mutexes = support.getMutexes();
            if (mutexes == null) {
                log.string("No mutex information is available.");
            } else {
                for (int i = 0; i < mutexes.length; i++) {
                    VMMutex mutex = mutexes[i];
                    IsolateThread owner = mutex.owner;
                    // TEMP (chaeubl):
// if (owner.isNonNull()) {
                    log.string(mutex.getName()).string(" is locked by ");
                    if (owner.equal(VMMutex.UNSPECIFIED_OWNER)) {
                        log.string("an unspecified thread.");
                    } else {
                        log.string("thread ").zhex(owner);
                    }
                    log.newline();
// }
                }
            }

            log.indent(false);
        }
    }
}
