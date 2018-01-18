/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.debug.TTY;

import java.util.ArrayList;
import java.util.List;

public class InliningLog {
    public static final class BytecodePositionWithId extends BytecodePosition {
        private long id;

        public BytecodePositionWithId(BytecodePositionWithId caller, ResolvedJavaMethod method, int bci, long id) {
            super(caller, method, bci);
            this.id = id;
        }

        public BytecodePositionWithId(BytecodePosition position, long id) {
            super(toPositionWithId(position.getCaller()), position.getMethod(), position.getBCI());
            this.id = id;
        }

        public BytecodePositionWithId addCallerWithId(BytecodePositionWithId caller) {
            if (getCaller() == null) {
                return new BytecodePositionWithId(caller, getMethod(), getBCI(), id);
            } else {
                return new BytecodePositionWithId(getCaller().addCallerWithId(caller), getMethod(), getBCI(), id);
            }
        }

        private static BytecodePositionWithId toPositionWithId(BytecodePosition position) {
            if (position == null) {
                return null;
            }
            return new BytecodePositionWithId(toPositionWithId(position.getCaller()), position.getMethod(), position.getBCI(), 0);
        }

        @Override
        public BytecodePositionWithId getCaller() {
            return (BytecodePositionWithId) super.getCaller();
        }

        public long getId() {
            return id;
        }
    }

    public static final class Decision {
        private final boolean positive;
        private final String reason;
        private final String phase;
        private final BytecodePositionWithId position;
        private final InliningLog childLog;

        private Decision(boolean positive, String reason, String phase, BytecodePositionWithId position, InliningLog childLog) {
            assert position != null;
            this.positive = positive;
            this.reason = reason;
            this.phase = phase;
            this.position = position;
            this.childLog = childLog;
        }

        public boolean isPositive() {
            return positive;
        }

        public String getReason() {
            return reason;
        }

        public String getPhase() {
            return phase;
        }

        public BytecodePosition getPosition() {
            return position;
        }

        public InliningLog getChildLog() {
            return childLog;
        }
    }

    private final List<Decision> decisions;

    public InliningLog() {
        this.decisions = new ArrayList<>();
    }

    public List<Decision> getDecisions() {
        return decisions;
    }

    public void addDecision(boolean positive, String reason, String phase, BytecodePositionWithId position, InliningLog calleeLog) {
        Decision decision = new Decision(positive, reason, phase, position, calleeLog);
        decisions.add(decision);
    }

    public String formatAsList() {
        StringBuilder builder = new StringBuilder();
        formatAsList("", decisions, builder);
        return builder.toString();
    }

    private void formatAsList(String phasePrefix, List<Decision> decisions, StringBuilder builder) {
        for (Decision decision : decisions) {
            String phaseStack = phasePrefix.equals("") ? decision.getPhase() : phasePrefix + "-" + decision.getPhase();
            String positive = decision.isPositive() ? "inline" : "do not inline";
            String position = "  " + decision.getPosition().toString().replaceAll("\n", "\n  ");
            String line = String.format("<%s> %s: %s\n%s", phaseStack, positive, decision.getReason(), position);
            builder.append(line);
            builder.append(System.lineSeparator());
            if (decision.getChildLog() != null) {
                formatAsList(phaseStack, decision.getChildLog().getDecisions(), builder);
            }
        }
    }
}
