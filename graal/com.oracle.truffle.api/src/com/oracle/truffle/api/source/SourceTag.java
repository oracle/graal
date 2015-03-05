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
package com.oracle.truffle.api.source;

/**
 * Categorical information (best implemented as enums} about particular sources of Guest Language
 * code that can be useful to configure behavior of both the language runtime and external tools.
 * These might include {@linkplain StandardSourceTag standard tags} noting, for example, whether the
 * source was read from a file and whether it should be considered library code.
 * <p>
 * An untagged {@link Source} should by default be considered application code.
 * <p>
 * The need for additional tags is likely to arise, in some cases because of issue specific to a
 * Guest Language, but also for help configuring the behavior of particular tools.
 *
 * @see Source
 * @see StandardSourceTag
 */
public interface SourceTag {

    /**
     * Human-friendly name of a category of code sources, e.g. "file", or "library".
     *
     */
    String name();

    /**
     * Criteria and example uses for the tag.
     */
    String getDescription();
}
