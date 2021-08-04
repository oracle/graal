/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 group="Insight"
 )
 */

/**
 * Support for generating {@code .hprof} files in <a target="_blank" href=
 * "http://hg.openjdk.java.net/jdk6/jdk6/jdk/raw-file/tip/src/share/demo/jvmti/hprof/manual.html">
 * Java Profiler Heap Dump Format</a> and a
 * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument} to capture <em>heap dumps</em>
 * from Truffle languages.
 * 
 * <h3>Embedding into Java Applications</h3>
 * 
 * When embedding GraalVM dynamic languages in Java applications via
 * {@link org.graalvm.polyglot.Context} one can enabled not only GraalVM
 * {@link org.graalvm.tools.insight.Insight} scripts. One can also enable {@code heap} object in the
 * Insight scripts and capture the generated heaps in supplied {@link java.io.OutputStream} use:
 * 
 * {@codesnippet org.graalvm.tools.insight.test.heap.HeapObjectStreamTest}
 * 
 * Whenever {@code heap.dump(...)} is called to <a target="_blank" href=
 * "https://github.com/oracle/graal/blob/master/tools/docs/Insight-Manual.md#heap-dumping">perform
 * cooperative heap dumping</a>, the output is sent to the registered {@link java.io.OutputStream}
 */
package org.graalvm.tools.insight.heap;
