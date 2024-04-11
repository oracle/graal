/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graphio.parsing;

import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.Properties;

/**
 * Creates or finds a GraphDocument suitable for a received (read) data. Creates a suitable Document
 * for the given Properties, or a Group. This method is called when the {@link ModelBuilder} does
 * not start with a document instance. When the first object is read from the input, it will reach
 * out to the Factory to create or find the Document.
 *
 * @author sdedic
 */
public interface DocumentFactory {
    /**
     * Find or create a suitable Document. The method is called when the first Group is found in the
     * input stream. The Group instance will be non-null, and Properties will be the same as Group's
     * properties.
     * <p>
     * The method may be also called to find/initialize a GraphDocument with document-level
     * properties, if they're found in the stream. In that case {@code null} will be passed as
     * Group.
     *
     * @param props properties
     * @param g the group instance, or {@code null}.
     * @param id id of the document stream, possibly null
     * @return GraphDocument instance
     */
    GraphDocument documentFor(Object id, Properties props, Group g);
}
