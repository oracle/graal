/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot;

import org.graalvm.compiler.hotspot.HotSpotCompilationIdentifier;
import org.graalvm.compiler.truffle.compiler.TruffleCompilationIdentifier;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;

import jdk.vm.ci.hotspot.HotSpotCompilationRequest;

/**
 * A {@link HotSpotCompilationIdentifier} for Truffle compilations.
 */
public final class HotSpotTruffleCompilationIdentifier extends HotSpotCompilationIdentifier implements TruffleCompilationIdentifier {

    private final TruffleCompilationTask task;
    private final TruffleCompilable compilable;

    public HotSpotTruffleCompilationIdentifier(HotSpotCompilationRequest request, TruffleCompilationTask task, TruffleCompilable compilable) {
        super(request);
        this.task = task;
        this.compilable = compilable;
    }

    @Override
    public TruffleCompilationTask getTask() {
        return task;
    }

    @Override
    public TruffleCompilable getCompilable() {
        return compilable;
    }

    @Override
    public String toString(Verbosity verbosity) {
        return buildString(new StringBuilder(), verbosity).toString();
    }

    @Override
    protected StringBuilder buildName(StringBuilder sb) {
        return sb.append(compilable.toString());
    }

    @Override
    protected StringBuilder buildID(StringBuilder sb) {
        return super.buildID(sb.append("Truffle"));
    }

}
