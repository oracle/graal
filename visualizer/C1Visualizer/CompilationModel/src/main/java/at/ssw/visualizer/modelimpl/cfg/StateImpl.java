/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.modelimpl.cfg;

import at.ssw.visualizer.model.cfg.State;
import at.ssw.visualizer.model.cfg.StateEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Christian Wimmer
 */
public class StateImpl implements State {
    private String kind;
    private int size;
    private String method;
    private StateEntry[] entries;

    public StateImpl(String kind, int size, String method, StateEntryImpl[] entries) {
        this.kind = kind;
        this.size = size;
        this.method = method;
        this.entries = entries;
    }

    public String getKind() {
        return kind;
    }

    public int getSize() {
        return size;
    }

    public String getMethod() {
        return method;
    }

    public List<StateEntry> getEntries() {
        return Collections.unmodifiableList(Arrays.asList(entries));
    }
}
