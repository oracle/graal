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
package com.oracle.truffle.api.instrument;

/**
 * Program element "tags", presumed to be singletons (best implemented as enums) that define
 * user-visible behavior for debugging and other simple tools. These categories should correspond to
 * program structures, for example "statement" and "assignment", that are meaningful
 * ("human-sensible") to guest language programmers.
 * <p>
 * An untagged Truffle node should be understood as an artifact of the guest language implementation
 * and should not be visible to guest language programmers. Nodes may also have more than one tag,
 * for example a variable assignment that is also a statement. Finally, the assignment of tags to
 * nodes could depending on the use-case of whatever tool is using them.
 * <p>
 * <strong>Disclaimer:</strong> experimental interface under development.
 *
 * @see Probe
 * @see Wrapper
 * @see StandardSyntaxTag
 */
public interface SyntaxTag {

    /**
     * Human-friendly name of guest language program elements belonging to the category, e.g.
     * "statement".
     */
    String name();

    /**
     * Criteria and example uses for the tag.
     */
    String getDescription();

}
