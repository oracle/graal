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
package com.oracle.graal.truffle;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.lang.ref.*;
import java.util.*;
import java.util.stream.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;

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
        if (!isValid) {
            throw new InvalidAssumptionException();
        }
    }

    @Override
    public synchronized void invalidate() {
        if (isValid) {
            boolean invalidatedInstalledCode = false;
            Entry e = first;
            while (e != null) {
                InstalledCode installedCode = e.installedCode.get();
                if (installedCode != null && installedCode.getVersion() == e.version) {
                    installedCode.invalidate();

                    invalidatedInstalledCode = true;
                    if (TraceTruffleAssumptions.getValue()) {
                        logInvalidatedInstalledCode(installedCode);
                    }
                }
                e = e.next;
            }
            first = null;
            isValid = false;

            if (TraceTruffleAssumptions.getValue()) {
                if (invalidatedInstalledCode) {
                    logStackTrace();
                }
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
        final int limit = 20;
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        String suffix = stackTrace.length > skip + limit ? "\n  ..." : "";
        TTY.out().out().println(Arrays.stream(stackTrace).skip(skip).limit(limit).map(StackTraceElement::toString).collect(Collectors.joining("\n  ", "", suffix)));
    }
}
