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

/*
 @ApiInfo(
 group="Obsolete soon"
 )
 */

/**
 * <strong>This framework has been superseded and will be removed in a subsequent release.</strong>
 * <p>
 * <h4>Truffle Instrumentation: access to execution events for Debuggers and other tools.</h4>
 * <p>
 * This framework permits client tools, either builtin or third-party, to observe with
 * <em>very low overhead</em> the execution of a Truffle Language program at the level of
 * <em>AST execution events</em>:
 * <ul>
 * <li>a Truffle Node is about to be executed, or</li>
 * <li>a Truffle Node execution has just completed.</li>
 * </ul>
 * The framework supports many kinds of tools, for example simple collectors of data such as the
 * CoverageTracker. It also supports Truffle's built-in debugging services, as well as utilities
 * that maintain maps of source code locations for other tools such as debugging.
 *
 * <h4>Instrumentation Services</h4>
 * <p>
 * API access to instrumentation services is provided by the Instrumenter that is part of the
 * Truffle execution environment. These services fall into several categories:
 *
 * <ul>
 *
 * <li><strong>AST Markup: probing</strong>
 * <ul>
 * <li>Execution events can only be observed at AST locations that have been probed, which results
 * in the creation of a Probe that is permanently associated with a particular segment of source
 * code, e.g. a "statement", that corresponds to an AST location.</li>
 * <li>Probing is only supported at Nodes where supported by specific language implementations.</li>
 * <li>The relationship between a Probe and a source code location persists across Truffle
 * <em>cloning</em> of ASTs, which is to say, a single Probe corresponds to (and keeps track of) the
 * equivalent location in every clone of the original AST.</li>
 * <li>Probing is specified by registering an instance of ASTProber. A default prober provided by
 * each TruffelLanguage will be registered automatically.</li>
 * <li>All AST probing must be complete before any AST execution has started.</li>
 * </ul>
 * </li>
 *
 * <li><strong>AST Markup: tagging</strong>
 * <ul>
 * <li>Additional information can be added at any time to a Probe by adding to its set of tags.</li>
 *
 * <li>Standard tags should be used for common, language-agnostic concepts such as STATEMENT, and
 * this is usually done by each language's default ASTProber while Probes are being created.</li>
 * <li>Tags serve to configure the behavior of clients during program execution. For example,
 * knowing which Probes are on STATEMENT nodes informs both the CoverageTracker and the debugger
 * while "stepping".</li>
 * <li>tags can also be added at any time by any client for any purpose, including private Tags
 * about which other clients can learn nothing.</li>
 * </ul>
 * </li>
 *
 * <li><strong>Markup Discovery: finding probes</strong>
 * <ul>
 * <li>Clients can be observe Probes being created and Tags being added by registering a
 * ProbeListener.</li>
 * <li>Clients can also find all existing Probes, possibly filtering the search by tag.</li>
 * </ul>
 * </li>
 *
 * <li><strong>Observing Execution: event listening:</strong>
 * <ul>
 * <li>Clients can be notified of <em>AST execution events</em> by creating one of several kinds of
 * <em>event listener</em> and <em>attaching</em> it to a Probe. This creates an Instrument that
 * notifies the listener of every subsequent execution event at the AST location corresponding to
 * the Probe.</li>
 * <li>An Instrument can be disposed, at which time it is removed from service at every clone of the
 * AST, incurs no further overhead, and is permanently unusable.</li>
 * <li>Many clients need only implement a SimpleInstrumentListener, whose notification methods
 * provide only the instance of the Probe to which it was attached. This provides access to the
 * corresponding source code location and any tags that had been applied there.</li>
 * <li>Clients that require deeper access to execution state implement a StandardInstrumentListener
 * whose notification methods provide access to the concrete AST location and current stack frame.
 * </li>
 * <li>Clients can also create an Instrument (whose design is currently under revision) that
 * supports (effectively) inserting a Truffle AST fragment into the AST location, where it will be
 * executed subject to full Truffle optimization.</li>
 * </ul>
 * </li>
 *
 * <li><strong>Wide-area Instrumentation: TagInstruments</strong>
 * <ul>
 * <li>A specialized form of Instrumentation is provided that efficiently attaches a single listener
 * called a StandardBeforeInstrumentListener to every Probe containing a specified tag.</li>
 * <li>One (but no more than one) StandardBeforeInstrumentListener may optionally be attached for
 * notification <em>before</em> every <em>AST execution event</em> where the specified tag is
 * present.</li>
 * <li>One (but no more than one) StandardAfterInstrumentListener may optionally be attached for
 * notification <em>after</em> every <em>AST execution event</em> where the specified tag is
 * present.</li>
 * <li>The TagInstrument mechanism is independent of listeners that may be attached to Probes. It is
 * especially valuable for applications such as the debugger, where during "stepping" the program
 * should be halted at any node tagged with STATEMENT.</li>
 * </ul>
 * </li>
 *
 * <li><strong>Data Gathering Utilities: tools</strong>
 * <ul>
 * <li>The Instrumentation Framework supports extensible set of utilities that can be installed, at
 * which time they start collecting information that can be queried at any time.</li>
 * <li>Once installed, these tools can be dynamically disabled and re-enabled and eventually
 * disposed when no longer needed.</li>
 * <li>A useful example is the LineToProbesMap, which incrementally builds and maintains a map of
 * Probes indexed by source code line. Truffle debugging services depend heavily on this utility.
 * </li>
 * <li>The CoverageTracker maintains counts of execution events where a Probe has been tagged with
 * STATEMENT, indexed by source code line.</li>
 * </ul>
 * </li>
 * </ul>
 *
 */
package com.oracle.truffle.api.instrument;
