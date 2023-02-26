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
package com.oracle.svm.core.graal.code;

import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstrateCompilationIdentifier implements CompilationIdentifier {

    private static final AtomicLong uniqueIds = new AtomicLong();

    private final long id;
    private final ResolvedJavaMethod method;

    public SubstrateCompilationIdentifier(ResolvedJavaMethod method) {
        this.id = uniqueIds.getAndIncrement();
        this.method = method;
    }

    public SubstrateCompilationIdentifier() {
        this(null);
    }

    @Override
    public final String toString() {
        return toString(Verbosity.DETAILED);
    }

    @Override
    public String toString(Verbosity verbosity) {
        return buildString(new StringBuilder(), verbosity).toString();
    }

    protected StringBuilder buildString(StringBuilder sb, Verbosity verbosity) {
        if (method == null || verbosity == Verbosity.ID) {
            buildID(sb);
        } else if (verbosity == Verbosity.NAME) {
            buildName(sb);
        } else {
            GraalError.guarantee(verbosity == Verbosity.DETAILED, "unknown verbosity: %s", verbosity);
            buildID(sb);
            sb.append('[');
            buildName(sb);
            sb.append(']');
        }
        return sb;
    }

    protected void buildName(StringBuilder sb) {
        sb.append(method.format("%H.%n(%p)"));
    }

    protected void buildID(StringBuilder sb) {
        sb.append("SubstrateCompilation-").append(id);
    }
}
