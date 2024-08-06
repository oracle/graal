/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Substrate VM re-uses much of the Graal option system for handling its own options. In the Graal
 * option system, an option is declared as a {@code static final} field that is annotated with the
 * annotation {@link jdk.graal.compiler.options.Option}. Values are stored in
 * {@link jdk.graal.compiler.options.OptionValues}.
 * <p>
 * Substrate VM has two distinct kinds of options:
 * <ul>
 * <li>Hosted options: configure the native image generation, i.e., influence what is put into the
 * image and how the image is built. They are set using the prefix <b>-H:</b> on the command line.
 * Options are defined using fields of the class {@link com.oracle.svm.core.option.HostedOptionKey}.
 * The option values are maintained by {@link com.oracle.svm.core.option.HostedOptionValues}. The
 * most convenient access to the value of a hosted option is
 * {@link com.oracle.svm.core.option.HostedOptionKey#getValue()}.
 * <p>
 * Hosted options cannot be changed at run time. Instead they are guaranteed to be constant folded
 * in the image. This is implemented using the {@link jdk.graal.compiler.api.replacements.Fold}
 * annotation on {@link com.oracle.svm.core.option.HostedOptionKey#getValue()}.</li>
 *
 * <li>Runtime options: get their initial value during native image generation, using the prefix
 * <b>-R:</b> on the command line. Options are defined using fields of the class
 * {@link com.oracle.svm.core.option.RuntimeOptionKey}. The option values are maintained by
 * {@link com.oracle.svm.core.option.RuntimeOptionValues}. The most convenient access to the value
 * of a runtime option is {@link com.oracle.svm.core.option.RuntimeOptionKey#getValue()}.
 * <p>
 * Runtime options can be changed at run time.
 * {@link com.oracle.svm.core.option.RuntimeOptionParser#parse} is a convenient helper to do option
 * parsing at run time.</li>
 * </ul>
 *
 * Substrate VM re-uses much of the Graal option system for handling. However, Graal itself is
 * stateless, i.e, every Graal compilation can be configured with its own set of
 * {@link jdk.graal.compiler.options.OptionValues}. Therefore, access of a Graal option using
 * {@link jdk.graal.compiler.options.OptionKey#getValue(OptionValues)} requires to explicitly
 * specify one of the two option values of Substrate VM:
 * {@link com.oracle.svm.core.option.HostedOptionValues#singleton()} or
 * {@link com.oracle.svm.core.option.RuntimeOptionValues#singleton()}.
 */
package com.oracle.svm.core.option;

import jdk.graal.compiler.options.OptionValues;