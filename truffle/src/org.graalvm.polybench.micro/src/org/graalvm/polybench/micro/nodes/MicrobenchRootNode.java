/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench.micro.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.graalvm.polybench.micro.Microbench;
import org.graalvm.polybench.micro.MicrobenchLanguage;
import org.graalvm.polybench.micro.expr.Expression;

public class MicrobenchRootNode extends RootNode {

    @Child Expression benchmark;
    final LoopConditionProfile loopCondition = LoopConditionProfile.create();

    private final int repeat;

    public MicrobenchRootNode(MicrobenchLanguage language, Microbench spec) {
        super(language);
        this.benchmark = spec.benchmark;
        this.repeat = spec.repeat;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            loopCondition.profileCounted(repeat);
            for (int i = 0; loopCondition.inject(i < repeat); i++) {
                CompilerDirectives.blackhole(benchmark.execute(frame));
                TruffleSafepoint.poll(this);
            }
            return null;
        } finally {
            LoopNode.reportLoopCount(this, repeat);
        }
    }
}
