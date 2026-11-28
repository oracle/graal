/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.pgo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Registry of the instrumented switches. Their successors are counted in the
 * {@link CallCountProfileRecorder} tables (see {@link SwitchProfileKey}).
 */
public final class SwitchProfileRecorder {
    private static final ConcurrentMap<Integer, SwitchProfileKey> SWITCHES = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_SWITCH = new AtomicInteger();
    private static boolean enabled;
    /**
     * Switches are created after analysis, during compilation. This placeholder, created when the
     * class is initialized at build time, lets the analysis see a {@link SwitchProfileKey} in
     * {@link #SWITCHES} with all its fields set; {@link #getKeys()} leaves it out.
     */
    private static final SwitchProfileKey REACHABILITY_PLACEHOLDER = create(new ProfileFrame[]{new ProfileFrame("Lcom/oracle/svm/core/pgo/SwitchProfileRecorder;.__unused__()V", 0)},
                    new int[]{-1, -2});

    private SwitchProfileRecorder() {
    }

    public static void enable() {
        enabled = true;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Creates a switch with every successor numbered right away, as if compiled code already used them. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static SwitchProfileKey create(ProfileFrame[] context, int[] successorBcis) {
        SwitchProfileKey key = createUnnumbered(context, successorBcis);
        for (int i = 0; i < successorBcis.length; i++) {
            key.successorSlot(i).counterIndexForCode();
        }
        return key;
    }

    /**
     * Creates the key of one physical switch. Each successor is numbered when its counter node
     * is lowered, and the switch is registered with the first of them.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static SwitchProfileKey createUnnumbered(ProfileFrame[] context, int[] successorBcis) {
        if (context.length == 0 || successorBcis.length < 2) {
            throw new IllegalArgumentException("PGO switch profiler: a switch needs a calling context and at least two successors");
        }
        return new SwitchProfileKey(context, successorBcis);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static void register(SwitchProfileKey key) {
        SWITCHES.put(NEXT_SWITCH.getAndIncrement(), key);
    }

    /** The registered switches in a deterministic order, for the writer. */
    public static List<SwitchProfileKey> getKeys() {
        List<SwitchProfileKey> result = new ArrayList<>();
        for (SwitchProfileKey key : SWITCHES.values()) {
            if (key != REACHABILITY_PLACEHOLDER) {
                result.add(key);
            }
        }
        result.sort(Comparator.comparing(SwitchProfileKey::context, ProfileFrame.CONTEXT_ORDER)
                        .thenComparing(SwitchProfileKey::successorCount));
        return result;
    }
}
