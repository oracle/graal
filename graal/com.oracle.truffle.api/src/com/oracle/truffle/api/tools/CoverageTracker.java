/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.tools;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.*;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.source.*;

/**
 * A {@link InstrumentationTool} that counts interpreter <em>execution calls</em> to AST nodes that hold a
 * specified {@linkplain SyntaxTag tag}, tabulated by source and line number associated with each
 * node. Tags are presumed to be applied external to the tool. If no tag is specified,
 * {@linkplain StandardSyntaxTag#STATEMENT STATEMENT} is used, corresponding to conventional
 * behavior for code coverage tools.
 * <p>
 * <b>Tool Life Cycle</b>
 * <p>
 * See {@link InstrumentationTool} for the life cycle common to all such tools.
 * <p>
 * <b>Execution Counts</b>
 * <p>
 * <ul>
 * <li>"Execution call" on a node is is defined as invocation of a node method that is instrumented
 * to produce the event {@link TruffleEventReceiver#enter(Node, VirtualFrame)};</li>
 * <li>Execution calls are tabulated only at <em>instrumented</em> nodes, i.e. those for which
 * {@linkplain Node#isInstrumentable() isInstrumentable() == true};</li>
 * <li>Execution calls are tabulated only at nodes present in the AST when originally created;
 * dynamically added nodes will not be instrumented.</li>
 * </ul>
 * </p>
 * <b>Results</b>
 * <p>
 * A modification-safe copy of the {@linkplain #getCounts() counts} can be retrieved at any time,
 * without effect on the state of the tool.
 * </p>
 * <p>
 * A "default" {@linkplain #print(PrintStream) print()} method can summarizes the current counts at
 * any time in a simple textual format, with no other effect on the state of the tool.
 * </p>
 *
 * @see Instrument
 * @see SyntaxTag
 */
public final class CoverageTracker extends InstrumentationTool {

    /** Counting data. */
    private final Map<LineLocation, CoverageCounter> counters = new HashMap<>();

    /** For disposal. */
    private final List<Instrument> instruments = new ArrayList<>();

    /**
     * Counting is restricted to nodes holding this tag.
     */
    private final SyntaxTag countingTag;

    private final ProbeListener probeListener;

    /**
     * Create a per-line coverage tool for nodes tagged as {@linkplain StandardSyntaxTag#STATEMENT
     * statements} in subsequently created ASTs.
     */
    public CoverageTracker() {
        this(StandardSyntaxTag.STATEMENT);
    }

    /**
     * Create a per-line coverage tool for nodes tagged as specified, presuming that tags applied
     * outside this tool.
     */
    public CoverageTracker(SyntaxTag tag) {
        this.probeListener = new CoverageProbeListener();
        this.countingTag = tag;
    }

    @Override
    protected boolean internalInstall() {
        Probe.addProbeListener(probeListener);
        return true;
    }

    @Override
    protected void internalReset() {
        counters.clear();
    }

    @Override
    protected void internalDispose() {
        Probe.removeProbeListener(probeListener);
        for (Instrument instrument : instruments) {
            instrument.dispose();
        }
    }

    /**
     * Gets a modification-safe summary of the current per-type node execution counts; does not
     * affect the counts.
     * <p>
     * The map holds an array for each source, and elements of the a array corresponding to the
     * textual lines of that source. An array entry contains null if the corresponding line of
     * source is associated with no nodes of the relevant type, i.e. where no count was made. A
     * numeric entry represents the execution count at the corresponding line of source.
     * <p>
     * <b>Note:</b> source line numbers are 1-based, so array index {@code i} corresponds to source
     * line number {@code i + 1}
     */
    public Map<Source, Long[]> getCounts() {

        /**
         * Counters for every {Source, line number} for which a counter was installed, i.e. for
         * every line associated with an appropriately tagged AST node; iterable in order of source
         * name, then line number.
         */
        final TreeSet<Entry<LineLocation, CoverageCounter>> entries = new TreeSet<>(new LineLocationEntryComparator());

        final Map<Source, Long[]> results = new HashMap<>();

        for (Entry<LineLocation, CoverageCounter> entry : counters.entrySet()) {
            entries.add(entry);
        }
        Source curSource = null;
        Long[] curLineTable = null;
        for (Entry<LineLocation, CoverageCounter> entry : entries) {
            final LineLocation key = entry.getKey();
            final Source source = key.getSource();
            final int lineNo = key.getLineNumber();
            if (source != curSource) {
                if (curSource != null) {
                    results.put(curSource, curLineTable);
                }
                curSource = source;
                curLineTable = new Long[source.getLineCount()];
            }
            curLineTable[lineNo - 1] = entry.getValue().count.longValue();
        }
        if (curSource != null) {
            results.put(curSource, curLineTable);
        }
        return results;
    }

    /**
     * A default printer for the current line counts, producing lines of the form " (<count>) <line
     * number> : <text of line>", grouped by source.
     */
    public void print(PrintStream out) {
        out.println();
        out.println(countingTag.name() + " coverage:");

        /**
         * Counters for every {Source, line number} for which a counter was installed, i.e. for
         * every line associated with an appropriately tagged AST node; iterable in order of source
         * name, then line number.
         */
        final TreeSet<Entry<LineLocation, CoverageCounter>> entries = new TreeSet<>(new LineLocationEntryComparator());

        for (Entry<LineLocation, CoverageCounter> entry : counters.entrySet()) {
            entries.add(entry);
        }
        Source curSource = null;
        int curLineNo = 1;
        for (Entry<LineLocation, CoverageCounter> entry : entries) {
            final LineLocation key = entry.getKey();
            final Source source = key.getSource();
            final int lineNo = key.getLineNumber();
            if (source != curSource) {
                if (curSource != null) {
                    while (curLineNo <= curSource.getLineCount()) {
                        displayLine(out, null, curSource, curLineNo++);
                    }
                }
                curSource = source;
                curLineNo = 1;
                out.println();
                out.println(source.getPath());
            }
            while (curLineNo < lineNo) {
                displayLine(out, null, curSource, curLineNo++);
            }
            displayLine(out, entry.getValue().count, curSource, curLineNo++);
        }
        if (curSource != null) {
            while (curLineNo <= curSource.getLineCount()) {
                displayLine(out, null, curSource, curLineNo++);
            }
        }
    }

    private static void displayLine(PrintStream out, AtomicLong value, Source source, int lineNo) {
        if (value == null) {
            out.format("%14s", " ");
        } else {
            out.format("(%12d)", value.longValue());
        }
        out.format(" %3d: ", lineNo);
        out.println(source.getCode(lineNo));
    }

    /**
     * A receiver for events at each instrumented AST location. This receiver counts
     * "execution calls" to the instrumented node and is <em>stateful</em>. State in receivers must
     * be considered carefully since ASTs, along with all instrumentation (including event receivers
     * such as this) are routinely cloned by the Truffle runtime. AST cloning is <em>shallow</em>
     * (for non- {@link Child} nodes), so in this case the actual count <em>is shared</em> among all
     * the clones; the count is also held in a table indexed by source line.
     * <p>
     * In contrast, a primitive field would <em>not</em> be shared among clones and resulting counts
     * would not be accurate.
     */
    private final class CoverageEventReceiver extends DefaultEventReceiver {

        /**
         * Shared by all clones of the associated instrument and by the table of counters for the
         * line.
         */
        private final AtomicLong count;

        CoverageEventReceiver(AtomicLong count) {
            this.count = count;
        }

        @Override
        @TruffleBoundary
        public void enter(Node node, VirtualFrame frame) {
            if (isEnabled()) {
                count.getAndIncrement();
            }
        }
    }

    private static final class LineLocationEntryComparator implements Comparator<Entry<LineLocation, CoverageCounter>> {

        public int compare(Entry<LineLocation, CoverageCounter> e1, Entry<LineLocation, CoverageCounter> e2) {
            return LineLocation.COMPARATOR.compare(e1.getKey(), e2.getKey());
        }
    }

    /**
     * Attach a counting instrument to each node that is assigned a specified tag.
     */
    private class CoverageProbeListener extends DefaultProbeListener {

        @Override
        public void probeTaggedAs(Probe probe, SyntaxTag tag, Object tagValue) {
            if (countingTag == tag) {

                final SourceSection srcSection = probe.getProbedSourceSection();
                if (srcSection == null) {
                    // TODO (mlvdv) report this?
                } else {
                    final LineLocation lineLocation = srcSection.getLineLocation();
                    CoverageCounter counter = counters.get(lineLocation);
                    if (counter != null) {
                        // Another node starts on same line; count only the first (textually)
                        if (srcSection.getCharIndex() > counter.srcSection.getCharIndex()) {
                            // Counter already in place, corresponds to code earlier on line
                            return;
                        } else {
                            // Counter already in place, corresponds to later code; replace it
                            counter.instrument.dispose();
                        }
                    }
                    final AtomicLong count = new AtomicLong();
                    final CoverageEventReceiver eventReceiver = new CoverageEventReceiver(count);
                    final Instrument instrument = Instrument.create(eventReceiver, CoverageTracker.class.getSimpleName());
                    instruments.add(instrument);
                    probe.attach(instrument);
                    counters.put(lineLocation, new CoverageCounter(srcSection, instrument, count));
                }
            }
        }
    }

    private class CoverageCounter {
        final SourceSection srcSection;
        final Instrument instrument;
        final AtomicLong count;

        CoverageCounter(SourceSection srcSection, Instrument instrument, AtomicLong count) {
            this.srcSection = srcSection;
            this.instrument = instrument;
            this.count = count;
        }
    }

}
