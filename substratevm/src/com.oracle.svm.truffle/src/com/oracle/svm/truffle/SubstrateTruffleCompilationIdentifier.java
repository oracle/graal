/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.truffle.TruffleCompilationIdentifier;

import com.oracle.svm.core.graal.code.SubstrateCompilationIdentifier;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;

public final class SubstrateTruffleCompilationIdentifier extends SubstrateCompilationIdentifier implements TruffleCompilationIdentifier {

    private final TruffleCompilationTask task;
    private final TruffleCompilable compilable;

    public SubstrateTruffleCompilationIdentifier(TruffleCompilationTask task, TruffleCompilable compilable) {
        this.task = task;
        this.compilable = compilable;
    }

    @Override
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
                throw new GraalError("Unknown verbosity: " + verbosity);
        }
        return sb;
    }

    @Override
    protected void buildName(StringBuilder sb) {
        sb.append(compilable.toString());
    }

    @Override
    public TruffleCompilationTask getTask() {
        return task;
    }

    @Override
    public TruffleCompilable getCompilable() {
        return compilable;
    }

}
