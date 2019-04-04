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
package com.oracle.svm.configure.trace;

import java.util.function.Supplier;

public class AccessAdvisor {
    private boolean ignoreInternalAccesses = true;
    private boolean isInLivePhase = false;
    private boolean previousIsGetApplicationClass = false;

    public void setIgnoreInternalAccesses(boolean enabled) {
        ignoreInternalAccesses = enabled;
    }

    public void setInLivePhase(boolean live) {
        isInLivePhase = live;
    }

    private static boolean isInternalClass(String qualifiedClass) {
        assert qualifiedClass == null || qualifiedClass.indexOf('/') == -1 : "expecting Java-format qualifiers, not internal format";
        return qualifiedClass != null && (qualifiedClass.startsWith("java.") || qualifiedClass.startsWith("javax.") || qualifiedClass.startsWith("sun.") ||
                        qualifiedClass.startsWith("com.sun.") || qualifiedClass.startsWith("jdk."));
    }

    public boolean shouldIgnore(Supplier<String> callerClass) {
        return ignoreInternalAccesses && (!isInLivePhase || isInternalClass(callerClass.get()));
    }

    public boolean shouldIgnoreJniMethodLookup(Supplier<String> queriedClass, Supplier<String> name, Supplier<String> signature, Supplier<String> callerClass) {
        if (!ignoreInternalAccesses) {
            return false;
        }
        if (shouldIgnore(callerClass)) {
            return true;
        }
        // Heuristic: filter LauncherHelper as well as a lookup of main(String[]) that
        // immediately follows a lookup of LauncherHelper.getApplicationClass()
        if ("sun.launcher.LauncherHelper".equals(queriedClass.get())) {
            previousIsGetApplicationClass = "getApplicationClass".equals(name.get()) && "()Ljava/lang/Class;".equals(signature.get());
            return true;
        }
        if (previousIsGetApplicationClass) {
            if ("main".equals(name.get()) && "([Ljava/lang/String;)V".equals(signature.get())) {
                return true;
            }
            previousIsGetApplicationClass = false;
        }
        /*
         * NOTE: JVM invocations cannot be reliably filtered with callerClass == null because these
         * could also be calls in a manually launched thread which is attached to JNI, but is not
         * executing Java code (yet).
         */
        return false;
    }
}
