/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.graal.filters;

import org.graalvm.visualizer.filter.AbstractFilter;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * A more efficient bulk coloring implementation.
 */
public class PropertyColorFilter extends AbstractFilter {
    Map<String, Map<String, Color>> stringColorMap = new HashMap<>();

    public PropertyColorFilter() {
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public void apply(Diagram diagram) {
        applyColorMap(diagram);
    }

    private void applyColorMap(Diagram diagram) {
        Set<String> keys = stringColorMap.keySet();
        for (Figure f : diagram.getFigures()) {
            for (String property : keys) {
                Object value = f.getProperties().get(property);
                if (value == null) {
                    continue;
                }
                Color color = stringColorMap.get(property).get(value.toString());
                if (color != null) {
                    f.setColor(color);
                }
            }
        }
    }

    /**
     * Color any {@link Figure} with the {@code property} equals to {@code value} with the color {@code color}.
     */
    public void addRule(String property, String value, Color color) {
        stringColorMap.computeIfAbsent(property, x -> new HashMap<>()).put(value, color);
    }
}
