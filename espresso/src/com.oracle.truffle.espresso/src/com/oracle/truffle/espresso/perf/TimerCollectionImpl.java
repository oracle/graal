/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.perf;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.TruffleLogger;

class TimerCollectionImpl extends TimerCollection {
    private Map<DebugTimer, DebugTimer.DebugTimerImpl> mapping = new ConcurrentHashMap<>();

    @Override
    DebugCloseable scope(DebugTimer timer) {
        DebugTimer.DebugTimerImpl impl = mapping.get(timer);
        if (impl == null) {
            DebugTimer.DebugTimerImpl created = DebugTimer.spawn();
            impl = mapping.putIfAbsent(timer, created);
            if (impl == null) {
                impl = created;
            }
        }
        impl.enter();
        return DebugTimer.AutoTimer.scope(impl);
    }

    @Override
    public void report(TruffleLogger logger) {
        Set<DebugTimer> visited = new HashSet<>();
        report(mapping.keySet(), logger, visited, "");
    }

    private void report(Iterable<DebugTimer> timers, TruffleLogger logger, Set<DebugTimer> visited, String prefix) {
        for (DebugTimer timer : timers) {
            if (shouldProcess(visited, timer)) {
                visited.add(timer);
                mapping.get(timer).report(logger, prefix + timer.name);
                if (timer.children() != null) {
                    report(timer.children(), logger, visited, prefix + "    ");
                }
            }
        }
    }

    private static boolean shouldProcess(Set<DebugTimer> visited, DebugTimer timer) {
        if (visited.contains(timer)) {
            return false;
        }
        if (timer.parent() == null) {
            return true;
        }
        if (visited.contains(timer.parent())) {
            return true;
        }
        return false;
    }

    static final class NoTimer extends TimerCollection {
        @Override
        DebugCloseable scope(DebugTimer timer) {
            return DebugTimer.AutoTimer.NO_TIMER;
        }

        @Override
        public void report(TruffleLogger logger) {
        }
    }
}
