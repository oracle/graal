/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.truffle;

import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TraceTruffleAssumptions;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TraceTruffleStackTraceLimit;

import java.lang.ref.WeakReference;

import org.graalvm.compiler.debug.TTY;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.impl.AbstractAssumption;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import jdk.vm.ci.code.InstalledCode;

public final class OptimizedAssumption extends AbstractAssumption {

    private static class Entry {
        WeakReference<InstalledCode> installedCode;
        long version;
        Entry next;
    }

    private Entry first;

    public OptimizedAssumption(String name) {
        super(name);
    }

    @Override
    public void check() throws InvalidAssumptionException {
        if (!this.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new InvalidAssumptionException();
        }
    }

    @Override
    public void invalidate() {
        if (isValid) {
            invalidateImpl();
        }
    }

    @TruffleBoundary
    private synchronized void invalidateImpl() {
        /*
         * Check again, now that we are holding the lock. Since isValid is defined volatile,
         * double-checked locking is allowed.
         */
        if (!isValid) {
            return;
        }

        boolean invalidatedInstalledCode = false;
        Entry e = first;
        while (e != null) {
            InstalledCode installedCode = e.installedCode.get();
            if (installedCode != null && installedCode.getVersion() == e.version) {
                invalidateWithReason(installedCode, "assumption invalidated");
                invalidatedInstalledCode = true;
                if (TruffleCompilerOptions.getValue(TraceTruffleAssumptions)) {
                    logInvalidatedInstalledCode(installedCode);
                }
            }
            e = e.next;
        }
        first = null;
        isValid = false;

        if (TruffleCompilerOptions.getValue(TraceTruffleAssumptions)) {
            if (invalidatedInstalledCode) {
                logStackTrace();
            }
        }
    }

    public synchronized void registerInstalledCode(InstalledCode installedCode) {
        if (isValid) {
            Entry e = new Entry();
            e.installedCode = new WeakReference<>(installedCode);
            e.version = installedCode.getVersion();
            e.next = first;
            first = e;
        } else {
            invalidateWithReason(installedCode, "assumption already invalidated when installing code");
            if (TruffleCompilerOptions.getValue(TraceTruffleAssumptions)) {
                logInvalidatedInstalledCode(installedCode);
                logStackTrace();
            }
        }
    }

    private void invalidateWithReason(InstalledCode installedCode, String reason) {
        if (installedCode instanceof OptimizedCallTarget) {
            ((OptimizedCallTarget) installedCode).invalidate(this, reason);
        } else {
            installedCode.invalidate();
        }
    }

    @Override
    public boolean isValid() {
        return isValid;
    }

    private void logInvalidatedInstalledCode(InstalledCode installedCode) {
        TTY.out().out().printf("assumption '%s' invalidated installed code '%s'\n", name, installedCode);
    }

    private static void logStackTrace() {
        final int skip = 1;
        final int limit = TruffleCompilerOptions.getValue(TraceTruffleStackTraceLimit);
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        StringBuilder strb = new StringBuilder();
        String sep = "";
        for (int i = skip; i < stackTrace.length && i < skip + limit; i++) {
            strb.append(sep).append("  ").append(stackTrace[i].toString());
            sep = "\n";
        }
        if (stackTrace.length > skip + limit) {
            strb.append("\n    ...");
        }

        TTY.out().out().println(strb);
    }
}
