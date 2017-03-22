/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck;

import java.util.concurrent.ScheduledExecutorService;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = CountAndKill.class)
final class CountAndKill implements TruffleObject {
    final ScheduledExecutorService onZero;
    int countDown;
    int lastParameter;

    CountAndKill(int counter, ScheduledExecutorService onZero) {
        this.onZero = onZero;
        this.countDown = counter;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return CountAndKillForeign.ACCESS;
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof CountAndKill;
    }

    @Resolve(message = "EXECUTE")
    abstract static class AddOne extends Node {
        protected boolean access(CountAndKill counter, Object... arguments) {
            if (counter.countDown == 0) {
                counter.onZero.shutdownNow();
            } else {
                counter.countDown--;
            }
            counter.lastParameter = ((Number) arguments[0]).intValue();
            return counter.lastParameter < 1000000;
        }
    }
}
