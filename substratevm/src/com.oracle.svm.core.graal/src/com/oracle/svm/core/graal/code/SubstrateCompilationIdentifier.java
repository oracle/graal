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

public class SubstrateCompilationIdentifier implements CompilationIdentifier {

    private static final AtomicLong uniqueIds = new AtomicLong();

    private final long id;

    public SubstrateCompilationIdentifier() {
        this.id = uniqueIds.getAndIncrement();
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
        switch (verbosity) {
            case ID:
                buildID(sb);
                break;
            case NAME:
                buildName(sb);
                break;
            case DETAILED:
                buildID(sb);
                sb.append('[');
                buildName(sb);
                sb.append(']');
                break;
            default:
                throw new GraalError("unknown verbosity: " + verbosity);
        }
        return sb;
    }

    protected StringBuilder buildID(StringBuilder sb) {
        sb.append("SubstrateCompilation-");
        return sb.append(id);
    }

    protected StringBuilder buildName(StringBuilder sb) {
        return buildID(sb);
    }
}
