/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument.impl;

import java.util.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.source.*;

/**
 * A mapping from {@link LineLocation} (a line number in a specific piece of {@link Source} code) to
 * a collection of {@link SourceSection}s that exist on that line. This class assumes that all nodes
 * are instrumented as it uses the {@link ProbeListener} interface to determine the source sections
 * that exist in the file.
 */
public class LineLocationToSourceSectionCollectionMap implements ProbeListener {

    /**
     * Map: Source line ==> source sections that exist on the line.
     */
    private final Map<LineLocation, Collection<SourceSection>> lineToSourceSectionsMap = new HashMap<>();

    public LineLocationToSourceSectionCollectionMap() {

    }

    public void newProbeInserted(SourceSection sourceSection, Probe probe) {
        if (sourceSection != null && !(sourceSection instanceof NullSourceSection)) {
            this.addSourceSectionToLine(sourceSection.getLineLocation(), sourceSection);
        }
    }

    public void probeTaggedAs(Probe probe, SyntaxTag tag) {
        // This map ignores tags, but this subclasses can override this method to operate on tags.
    }

    /**
     * Adds a source section to the given line.
     * <p>
     * If the line already exists in the internal {@link #lineToSourceSectionsMap}, this source
     * section will be added to the existing collection. If no line already exists in the internal
     * map, then a new key is added along with a new collection containing the source section.
     * <p>
     * This class does not check if a source section has already been added to a line.
     *
     * @param line The {@link LineLocation} to attach the source section to.
     * @param sourceSection The {@link SourceSection} to attach for that line location.
     */
    protected void addSourceSectionToLine(LineLocation line, SourceSection sourceSection) {
        if (!lineToSourceSectionsMap.containsKey(line)) {
            // Key does not exist, add new source section list
            final ArrayList<SourceSection> newSourceSectionList = new ArrayList<>(2);
            newSourceSectionList.add(sourceSection);
            lineToSourceSectionsMap.put(line, newSourceSectionList);
        } else {
            // Source section list exists, add to existing
            final Collection<SourceSection> existingSourceSectionList = lineToSourceSectionsMap.get(line);
            existingSourceSectionList.add(sourceSection);
        }
    }

    /**
     * Returns a collection of {@link SourceSection}s at the given {@link LineLocation}. If there
     * are no source sections at that line, a new empty list of size 1 is returned.
     *
     * @param line The line to check.
     * @return A iterable collection of source sections at the given line.
     */
    public Collection<SourceSection> getSourceSectionsAtLine(LineLocation line) {
        Collection<SourceSection> sourceSectionList = lineToSourceSectionsMap.get(line);

        if (sourceSectionList == null)
            sourceSectionList = new ArrayList<>(1);

        return sourceSectionList;
    }

    /**
     * Convenience method to get source sections according to a int line number. Returns a
     * collection of {@link SourceSection}s at the given line number. If there are no source
     * sections at that line, a new empty list is returned.
     *
     * @param lineNumber The line number to check.
     * @return A iterable collection of source sections at the given line.
     */
    public Collection<SourceSection> getSourceSectionsAtLineNumber(int lineNumber) {
        ArrayList<SourceSection> sourceSections = new ArrayList<>();

        for (LineLocation line : lineToSourceSectionsMap.keySet()) {
            if (line.getLineNumber() == lineNumber)
                sourceSections.addAll(lineToSourceSectionsMap.get(line));
        }

        return sourceSections;
    }
}
