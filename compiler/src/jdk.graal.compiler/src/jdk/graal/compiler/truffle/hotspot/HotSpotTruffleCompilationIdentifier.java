/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.hotspot;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.hotspot.HotSpotCompilationIdentifier;
import jdk.graal.compiler.truffle.TruffleCompilationIdentifier;
import jdk.graal.compiler.truffle.TruffleDebugJavaMethod;
import jdk.graal.compiler.truffle.TruffleGraphContext;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.meta.JavaMethod;

/**
 * A {@link HotSpotCompilationIdentifier} for Truffle compilations.
 */
public final class HotSpotTruffleCompilationIdentifier extends HotSpotCompilationIdentifier implements TruffleCompilationIdentifier {

    private final TruffleCompilationTask task;
    private final TruffleCompilable compilable;
    /** The identifier of the graph from which this graph was derived, or {@code null} for the root. */
    private final CompilationIdentifier parentCompilationId;
    private final TruffleGraphContext graphContext;

    public HotSpotTruffleCompilationIdentifier(HotSpotCompilationRequest request, TruffleCompilationTask task, TruffleCompilable compilable) {
        this(request, task, compilable, null, TruffleGraphContext.createRoot(compilable));
    }

    private HotSpotTruffleCompilationIdentifier(HotSpotCompilationRequest request, TruffleCompilationTask task, TruffleCompilable compilable,
                    CompilationIdentifier parentCompilationId, TruffleGraphContext graphContext) {
        super(request);
        this.task = task;
        this.compilable = compilable;
        this.parentCompilationId = parentCompilationId;
        this.graphContext = graphContext;
    }

    /**
     * Creates an identifier for a guest graph that will be inlined into a host compilation.
     *
     * @param request the host compilation request
     * @param task the synthetic guest compilation task
     * @param compilable the guest call target
     * @param hostCompilationId the identifier of the host graph
     * @return an identifier whose parent is the host compilation
     */
    public static HotSpotTruffleCompilationIdentifier createForHostToGuestInlining(HotSpotCompilationRequest request, TruffleCompilationTask task, TruffleCompilable compilable,
                    CompilationIdentifier hostCompilationId) {
        return new HotSpotTruffleCompilationIdentifier(request, task, compilable, hostCompilationId, TruffleGraphContext.createRoot(compilable));
    }

    @Override
    public HotSpotTruffleCompilationIdentifier createGraphIdentifier(TruffleGraphContext context) {
        return new HotSpotTruffleCompilationIdentifier(getRequest(), task, compilable, this, context);
    }

    @Override
    public TruffleGraphContext getGraphContext() {
        return graphContext;
    }

    @Override
    public CompilationIdentifier getParentCompilationIdentifier() {
        return parentCompilationId;
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

    @Override
    public long getTruffleCompilationId() {
        return getRequest().getId();
    }

    @Override
    public JavaMethod asJavaMethod() {
        return new TruffleDebugJavaMethod(getTask(), getCompilable());
    }
}
