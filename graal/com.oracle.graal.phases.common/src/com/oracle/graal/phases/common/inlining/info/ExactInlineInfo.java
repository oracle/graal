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
package com.oracle.graal.phases.common.inlining.info;

import com.oracle.graal.api.code.Assumptions;
import com.oracle.graal.api.meta.MetaAccessProvider;
import com.oracle.graal.api.meta.MetaUtil;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.phases.common.inlining.info.elem.Inlineable;
import com.oracle.graal.phases.util.Providers;

/**
 * Represents an inlining opportunity where the compiler can statically determine a monomorphic
 * target method and therefore is able to determine the called method exactly.
 */
public class ExactInlineInfo extends AbstractInlineInfo {

    protected final ResolvedJavaMethod concrete;
    private Inlineable inlineableElement;
    private boolean suppressNullCheck;

    public ExactInlineInfo(Invoke invoke, ResolvedJavaMethod concrete) {
        super(invoke);
        this.concrete = concrete;
        assert concrete != null;
    }

    public void suppressNullCheck() {
        suppressNullCheck = true;
    }

    @Override
    public void inline(Providers providers, Assumptions assumptions) {
        inline(invoke, concrete, inlineableElement, assumptions, !suppressNullCheck);
    }

    @Override
    public void tryToDevirtualizeInvoke(MetaAccessProvider metaAccess, Assumptions assumptions) {
        // nothing todo, can already be bound statically
    }

    @Override
    public int numberOfMethods() {
        return 1;
    }

    @Override
    public ResolvedJavaMethod methodAt(int index) {
        assert index == 0;
        return concrete;
    }

    @Override
    public double probabilityAt(int index) {
        assert index == 0;
        return 1.0;
    }

    @Override
    public double relevanceAt(int index) {
        assert index == 0;
        return 1.0;
    }

    @Override
    public String toString() {
        return "exact " + MetaUtil.format("%H.%n(%p):%r", concrete);
    }

    @Override
    public Inlineable inlineableElementAt(int index) {
        assert index == 0;
        return inlineableElement;
    }

    @Override
    public void setInlinableElement(int index, Inlineable inlineableElement) {
        assert index == 0;
        this.inlineableElement = inlineableElement;
    }

    public boolean shouldInline() {
        return concrete.shouldBeInlined();
    }
}
