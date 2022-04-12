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
package com.oracle.svm.core.jdk;

import org.graalvm.compiler.api.replacements.Fold;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import sun.security.util.Debug;

import java.security.AccessControlContext;
import java.security.ProtectionDomain;

@TargetClass(java.security.AccessControlContext.class)
final class Target_java_security_AccessControlContext {

    @Alias //
    boolean isPrivileged;

    @Alias //
    protected boolean isAuthorized;

    @Alias //
    @SuppressWarnings("unused") //
    Target_java_security_AccessControlContext(ProtectionDomain[] context, AccessControlContext privilegedContext) {
    }

    /**
     * Avoid making the code for debug printing reachable. We do not need it, and it only increases
     * code size. If we ever want to enable debug printing, several classes related to it need to be
     * initialized at run time because configuration parsing is in a class initializer.
     */
    @Substitute
    @Fold
    static Debug getDebug() {
        return null;
    }
}
