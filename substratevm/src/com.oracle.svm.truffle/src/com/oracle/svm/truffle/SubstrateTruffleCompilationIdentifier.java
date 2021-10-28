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

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.compiler.TruffleCompilationIdentifier;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;

import com.oracle.svm.core.graal.code.SubstrateCompilationIdentifier;

public class SubstrateTruffleCompilationIdentifier extends SubstrateCompilationIdentifier implements TruffleCompilationIdentifier {

    private final OptimizedCallTarget optimizedCallTarget;

    public SubstrateTruffleCompilationIdentifier(OptimizedCallTarget optimizedCallTarget) {
        this.optimizedCallTarget = optimizedCallTarget;
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
                throw new GraalError("unknown verbosity: " + verbosity);
        }
        return sb;
    }

    protected void buildName(StringBuilder sb) {
        sb.append(optimizedCallTarget.toString());
    }

    @Override
    public CompilableTruffleAST getCompilable() {
        return optimizedCallTarget;
    }

    @Override
    public void close() {
    }
}
