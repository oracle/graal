/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.inlining.policy;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.phases.common.inlining.info.InlineInfo;
import jdk.graal.compiler.phases.common.inlining.walker.MethodInvocation;

public interface InliningPolicy {
    final class Decision {
        public static final Decision YES = new Decision(true, "(unknown reason)");
        public static final Decision NO = new Decision(false, "(unknown reason)");

        private final boolean shouldInline;
        private final String reason;

        private Decision(boolean shouldInline, String reason) {
            this.shouldInline = shouldInline;
            this.reason = reason;
        }

        public boolean shouldInline() {
            return shouldInline;
        }

        public String getReason() {
            return reason;
        }

        /**
         * This constructor avoids the need to box arguments when the message is a simple string.
         */
        public Decision withReason(boolean isTracing, String newReason) {
            if (isTracing) {
                return new Decision(shouldInline, newReason);
            } else {
                return this;
            }
        }

        public Decision withReason(boolean isTracing, String newReason, Object... args) {
            if (isTracing) {
                return new Decision(shouldInline, String.format(newReason, args));
            } else {
                return this;
            }
        }
    }

    boolean continueInlining(StructuredGraph graph);

    Decision isWorthInlining(Replacements replacements, MethodInvocation invocation, InlineInfo calleeInfo, int inliningDepth, boolean fullyProcessed);
}
