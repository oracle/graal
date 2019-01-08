/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import static org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.runtime;

import java.util.Iterator;
import java.util.List;

import org.graalvm.compiler.truffle.common.TruffleInliningPlan;

import jdk.vm.ci.meta.JavaConstant;

public final class TruffleInliningDecision extends TruffleInlining implements Comparable<TruffleInliningDecision>, TruffleInliningPlan.Decision {

    private final OptimizedCallTarget target;
    private final TruffleInliningProfile profile;
    private boolean inline;

    public TruffleInliningDecision(OptimizedCallTarget target, TruffleInliningProfile profile, List<TruffleInliningDecision> children) {
        super(children);
        this.target = target;
        this.profile = profile;
    }

    @Override
    public String getTargetName() {
        return target.toString();
    }

    public OptimizedCallTarget getTarget() {
        return target;
    }

    void setInline(boolean inline) {
        this.inline = inline;
    }

    @Override
    public boolean shouldInline() {
        return inline;
    }

    public TruffleInliningProfile getProfile() {
        return profile;
    }

    @Override
    public int compareTo(TruffleInliningDecision o) {
        return Double.compare(o.getProfile().getScore(), getProfile().getScore());
    }

    public boolean isSameAs(TruffleInliningDecision other) {
        if (getTarget() != other.getTarget()) {
            return false;
        } else if (shouldInline() != other.shouldInline()) {
            return false;
        } else if (!shouldInline()) {
            assert !other.shouldInline();
            return true;
        } else {
            Iterator<TruffleInliningDecision> i1 = iterator();
            Iterator<TruffleInliningDecision> i2 = other.iterator();
            while (i1.hasNext() && i2.hasNext()) {
                if (!i1.next().isSameAs(i2.next())) {
                    return false;
                }
            }
            return !i1.hasNext() && !i2.hasNext();
        }
    }

    @Override
    public String toString() {
        return String.format("TruffleInliningDecision(callNode=%s, inline=%b)", profile.getCallNode(), inline);
    }

    @Override
    public boolean isTargetStable() {
        return target == getProfile().getCallNode().getCurrentCallTarget();
    }

    @Override
    public JavaConstant getNodeRewritingAssumption() {
        return runtime().forObject(target.getNodeRewritingAssumption());
    }
}
