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
 * <h4>Truffle {@linkplain com.oracle.truffle.api.instrument.Instrumenter Instrumentation}: access to execution events for Debuggers and other tools.</h4>
 * <p>
 * This framework permits client
 * {@linkplain com.oracle.truffle.api.instrument.Instrumenter.Tool tools},
 * either builtin or third-party, to observe with <em>very low overhead</em> the execution of a
 * {@linkplain com.oracle.truffle.api.TruffleLanguage Truffle Language} program at the level of
 * <em>AST execution events</em>:
 * <ul>
 * <li>a Truffle
 * {@linkplain com.oracle.truffle.api.nodes.Node Node} is about to be executed, or</li>
 * <li>a Truffle
 * {@linkplain com.oracle.truffle.api.nodes.Node Node} execution has just completed.</li>
 * </ul>
 * The framework supports many kinds of tools, for example simple collectors of data such as the
 * CoverageTracker.
 * It also supports Truffle's built-in
 * {@linkplain com.oracle.truffle.api.debug.Debugger debugging services}, as well as utilities
 * that maintain {@linkplain com.oracle.truffle.api.debug.LineToProbesMap maps of source code locations}
 * for other tools such as {@linkplain com.oracle.truffle.api.debug.Debugger debugging}.
 *
 * <h4>Instrumentation Services</h4>
 * <p>
 * API access to instrumentation services is provided by the
 * {@linkplain com.oracle.truffle.api.instrument.Instrumenter Instrumenter} that is part of
 * the Truffle execution environment. These services fall into several categories:
 *
 * <ul>
 *
 * <li><strong>AST Markup: probing</strong>
 * <ul>
 * <li>Execution events can only be observed at AST locations that have been
 * {@linkplain com.oracle.truffle.api.instrument.Instrumenter#probe(com.oracle.truffle.api.nodes.Node) probed},
 * which results in the creation of a
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probe} that is permanently associated with a
 * particular segment of source code, e.g. a "statement", that corresponds to an AST location.</li>
 * <li>Probing is only supported at
 * {@linkplain com.oracle.truffle.api.nodes.Node Nodes} where supported by specific language implementations.</li>
 * <li>The relationship between a
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probe} and
 * a source code location persists across Truffle <em>cloning</em> of ASTs, which is to say, a single
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probe} corresponds to (and keeps track of)
 * the equivalent location in every clone of the original AST.</li>
 * <li>Probing is specified by
 * {@linkplain com.oracle.truffle.api.instrument.Instrumenter#registerASTProber(ASTProber) registering}
 * an instance of
 * {@linkplain com.oracle.truffle.api.instrument.ASTProber ASTProber}.
 * A default prober provided by each {@linkplain com.oracle.truffle.api.TruffleLanguage TruffelLanguage}
 * will be registered automatically.</li>
 * <li>All AST probing must be complete before any AST execution has started.</li>
 * </ul></li>
 *
 * <li><strong>AST Markup: tagging</strong>
 * <ul>
 * <li>Additional information can be added at any time to a
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probe} by
 * {@linkplain com.oracle.truffle.api.instrument.Probe#tagAs(SyntaxTag, Object) adding} to its set of
 * {@linkplain com.oracle.truffle.api.instrument.SyntaxTag tags}.</li>
 *
 * <li>{@linkplain com.oracle.truffle.api.instrument.StandardSyntaxTag Standard tags} should
 * be used for common, language-agnostic concepts such as
 * {@linkplain com.oracle.truffle.api.instrument.StandardSyntaxTag#STATEMENT STATEMENT}, and this
 * is usually done by each language's default
 * {@linkplain com.oracle.truffle.api.instrument.ASTProber ASTProber} while
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probes} are being created.</li>
 * <li>{@linkplain com.oracle.truffle.api.instrument.SyntaxTag Tags} serve to configure the behavior
 * of clients during program execution.  For example, knowing which
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probes} are on
 * {@linkplain com.oracle.truffle.api.instrument.StandardSyntaxTag#STATEMENT STATEMENT nodes}
 * informs both the CoverageTracker and the
 * {@linkplain com.oracle.truffle.api.debug.Debugger debugger} while "stepping".</li>
 * <li>{@linkplain com.oracle.truffle.api.instrument.SyntaxTag tags} can also be added at any
 * time by any client for any purpose, including private
 * {@linkplain com.oracle.truffle.api.instrument.SyntaxTag Tags} about which other clients
 * can learn nothing.</li>
 * </ul></li>
 *
 * <li><strong>Markup Discovery: finding probes</strong>
 * <ul>
 * <li>Clients can be observe {@linkplain com.oracle.truffle.api.instrument.Probe Probes}
 * being {@linkplain com.oracle.truffle.api.instrument.Instrumenter#probe(com.oracle.truffle.api.nodes.Node) created} and
 * {@linkplain com.oracle.truffle.api.instrument.SyntaxTag Tags} being
 * {@linkplain com.oracle.truffle.api.instrument.Probe#tagAs(SyntaxTag, Object) added} by
 * {@linkplain com.oracle.truffle.api.instrument.Instrumenter#addProbeListener(ProbeListener) registering} a
 * {@linkplain com.oracle.truffle.api.instrument.ProbeListener ProbeListener}.</li>
 * <li>Clients can also
 * {@linkplain com.oracle.truffle.api.instrument.Instrumenter#findProbesTaggedAs(SyntaxTag) find} all existing
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probes}, possibly filtering the search by
 * {@linkplain com.oracle.truffle.api.instrument.SyntaxTag tag}.</li>
 * </ul></li>
 *
 * <li><strong>Observing Execution: event listening:</strong>
 * <ul>
 * <li>Clients can be notified of <em>AST execution events</em> by creating one of several kinds of
 * <em>event listener</em> and <em>attaching</em> it to a
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probe}. This creates an
 * {@linkplain com.oracle.truffle.api.instrument.ProbeInstrument Instrument} that notifies the listener
 * of every subsequent execution event at the AST location corresponding to the
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probe}.</li>
 * <li>An
 * {@linkplain com.oracle.truffle.api.instrument.ProbeInstrument Instrument} can be
 * {@linkplain com.oracle.truffle.api.instrument.ProbeInstrument#dispose() disposed}, at which time
 * it is removed from service at every clone of the AST, incurs no further overhead, and is
 * permanently unusable.</li>
 * <li>Many clients need only implement a
 * {@linkplain com.oracle.truffle.api.instrument.SimpleInstrumentListener SimpleInstrumentListener},
 * whose notification methods provide only the instance of the
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probe} to which it was attached.  This
 * provides access to the corresponding
 * {@linkplain com.oracle.truffle.api.source.SourceSection source code location} and any
 * {@linkplain com.oracle.truffle.api.instrument.SyntaxTag tags} that had been applied there.</li>
 * <li>Clients that require deeper access to execution state implement a
 * {@linkplain com.oracle.truffle.api.instrument.StandardInstrumentListener StandardInstrumentListener}
 * whose notification methods provide access to the concrete
 * {@linkplain com.oracle.truffle.api.nodes.Node AST location} and current
 * {@linkplain com.oracle.truffle.api.frame.Frame stack frame}.</li>
 * <li>Clients can also create an
 * {@linkplain com.oracle.truffle.api.instrument.ProbeInstrument Instrument} (whose design is currently
 * under revision) that supports (effectively) inserting a Truffle AST fragment into the AST
 * location, where it will be executed subject to full Truffle optimization.</li>
 * </ul></li>
 *
 * <li><strong>Wide-area Instrumentation: TagInstruments</strong>
 * <ul>
 * <li>A specialized form of Instrumentation is provided that efficiently attaches a single
 * listener called a
 * {@linkplain com.oracle.truffle.api.instrument.StandardBeforeInstrumentListener StandardBeforeInstrumentListener} to every
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probe} containing a specified
 * {@linkplain com.oracle.truffle.api.instrument.SyntaxTag tag}.</li>
 * <li>One (but no more than one)
 * {@linkplain com.oracle.truffle.api.instrument.StandardBeforeInstrumentListener StandardBeforeInstrumentListener}
 * may optionally be attached for notification <em>before</em> every <em>AST execution event</em> where the specified
 * {@linkplain com.oracle.truffle.api.instrument.SyntaxTag tag} is present.</li>
 * <li>One (but no more than one)
 * {@linkplain com.oracle.truffle.api.instrument.StandardAfterInstrumentListener StandardAfterInstrumentListener}
 * may optionally be attached for notification <em>after</em> every <em>AST execution event</em> where the specified
 * {@linkplain com.oracle.truffle.api.instrument.SyntaxTag tag} is present.</li>
 * <li>The
 * {@linkplain com.oracle.truffle.api.instrument.TagInstrument TagInstrument} mechanism is independent
 * of listeners that may be attached to
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probes}.  It is especially valuable for
 * applications such as the debugger, where during "stepping" the program should be halted at
 * any node tagged with
 * {@linkplain com.oracle.truffle.api.instrument.StandardSyntaxTag#STATEMENT STATEMENT}.</li>
 * </ul></li>
 *
 * <li><strong>Data Gathering Utilities:  tools</strong>
 * <ul>
 * <li>The Instrumentation Framework supports extensible set of utilities that can be
 * {@linkplain com.oracle.truffle.api.instrument.Instrumenter#install(com.oracle.truffle.api.instrument.Instrumenter.Tool)
 * installed}, at which time they start collecting information that can be queried at any time.</li>
 * <li>Once installed, these
 * {@linkplain com.oracle.truffle.api.instrument.Instrumenter.Tool tools} can be dynamically
 * {@linkplain com.oracle.truffle.api.instrument.Instrumenter.Tool#setEnabled(boolean) disabled and re-enabled} and eventually
 * {@linkplain com.oracle.truffle.api.instrument.Instrumenter.Tool#dispose() disposed} when no longer needed.</li>
 * <li>A useful example is the
 * {@linkplain com.oracle.truffle.api.debug.LineToProbesMap LineToProbesMap}, which incrementally builds and maintains a map of
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probes} indexed by
 * {@linkplain com.oracle.truffle.api.source.LineLocation source code line}. Truffle
 * {@linkplain com.oracle.truffle.api.debug.Debugger debugging services} depend heavily on this utility.</li>
 * <li>The CoverageTracker maintains counts of execution events where a
 * {@linkplain com.oracle.truffle.api.instrument.Probe Probe} has been tagged with
 * {@linkplain com.oracle.truffle.api.instrument.StandardSyntaxTag#STATEMENT STATEMENT}, indexed by source code line.</li>
 * </ul></li>
 * </ul>
 *
 */
package com.oracle.truffle.api.instrument;

