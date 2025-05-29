/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.source.impl.editor;

import org.graalvm.visualizer.source.Location;
import org.openide.text.Annotation;
import org.openide.text.Line;
import org.openide.util.NbBundle;

/**
 * Stack annotation
 */
final class StackAnnotation extends Annotation {
    public static final String CURRENT = "CurrentPosition"; // NOI18N
    public static final String NODE = "NodePosition"; // NOI18N
    public static final String OUTER = "CallSite"; // NOI18N
    public static final String OUTER_CURRENT = "CallSiteCurrent"; // NOI18N
    public static final String NESTED = "CalledSite"; // NOI18N

    private final Location location;
    private final String type;
    private final Line line;

    public StackAnnotation(Line line, Location location, String type) {
        this.location = location;
        this.type = type;
        this.line = line;
    }

    @Override
    public String getAnnotationType() {
        return type;
    }

    public Location getLocation() {
        return location;
    }

    public Line getLine() {
        return line;
    }

    @Override
    public String getShortDescription() {
        return NbBundle.getMessage(StackAnnotation.class, "Annotation_" + type);
    }

}
