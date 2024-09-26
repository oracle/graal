/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.search;

import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.MissingResourceException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * @author sdedic
 */
@NbBundle.Messages({
        "# DO NOT TRANSLATE - contains column ids transmitted from the server",
        "DEFAULT_VisibleColumnsAndPositions=id, field, targetMethod, reason",
        "DEFAULT_RelativeColumnSize.0=100",
        "DEFAULT_RelativeColumnSize.id=20",
        "DEFAULT_RelativeColumnSize.name=100",
        "DEFAULT_RelativeColumnSize.field=100",
        "DEFAULT_RelativeColumnSize.targetMethod=100",
        "DEFAULT_RelativeColumnSize.reason=70",
})
@ServiceProvider(service = SearchResultColumns.class)
public final class SearchResultColumns {
    private static final String PROPNAME_VISIBLE_COLUMNS = "visibleColumns"; // NOI18N
    private static final String NODE_COLUMN_WEIGHT = "weights"; // NOI18N
    private static final float DEFAULT_WEIGHT = 20.0f;

    private final Preferences prefs;

    public SearchResultColumns() {
        prefs = NbPreferences.forModule(SearchResultColumns.class);
    }

    public List<String> getInitialVisibleColumns() {
        String s = Bundle.DEFAULT_VisibleColumnsAndPositions();
        return Arrays.asList(s.split(", *")); // NOI18N
    }

    public List<String> getDefaultVisibleColumns() {
        String s = prefs.get(PROPNAME_VISIBLE_COLUMNS, "");
        return s.isEmpty() ?
                getInitialVisibleColumns() : Arrays.asList(s.split(", *"));
    }

    public void setDefaultVisibleColumns(List<String> cols) {
        prefs.put(PROPNAME_VISIBLE_COLUMNS, String.join(", ", cols));
    }

    public void removeVisibleColumn(String colId) {
        List<String> visCols = getDefaultVisibleColumns();
        if (!visCols.remove(colId)) {
            return;
        }
        setDefaultVisibleColumns(visCols);
    }

    public void addVisibleColumn(String colId) {
        List<String> visCols = getDefaultVisibleColumns();
        if (visCols.contains(colId)) {
            return;
        }
        visCols = new ArrayList<>(visCols);
        visCols.add(colId);
        setDefaultVisibleColumns(visCols);
    }

    public float getColumnRelativeSize(String col) {
        float w = prefs.node(NODE_COLUMN_WEIGHT).getFloat(col, -1);
        if (w >= 0) {
            return w;
        }
        try {
            String s = NbBundle.getBundle(SearchResultColumns.class).getString("DEFAULT_RelativeColumnSize." + col); // NOI18N
            return Float.parseFloat(s);
        } catch (MissingResourceException ex) {
            return DEFAULT_WEIGHT;
        }
    }

    public void setColumnRelativeSize(String col, float size) {
        prefs.node(NODE_COLUMN_WEIGHT).putFloat(col, size);
    }

    public void resetSizes() {
        try {
            prefs.node(NODE_COLUMN_WEIGHT).removeNode();
        } catch (BackingStoreException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
