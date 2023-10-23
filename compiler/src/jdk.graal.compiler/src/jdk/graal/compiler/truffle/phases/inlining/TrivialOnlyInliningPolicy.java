/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.phases.inlining;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.truffle.TruffleCompilerOptions;

final class TrivialOnlyInliningPolicy implements InliningPolicy {
    private final OptionValues options;

    TrivialOnlyInliningPolicy(OptionValues options) {
        this.options = options;
    }

    @Override
    public void run(CallTree tree) {
        String inlineOnly = TruffleCompilerOptions.InlineOnly.getValue(options);
        for (CallNode child : tree.getRoot().getChildren()) {
            if (!InliningPolicy.acceptForInline(child, inlineOnly)) {
                continue;
            }
            if (child.isTrivial()) {
                child.expand();
                if (child.getState() == CallNode.State.Expanded) {
                    child.inline();
                }
            }
        }
    }
}
