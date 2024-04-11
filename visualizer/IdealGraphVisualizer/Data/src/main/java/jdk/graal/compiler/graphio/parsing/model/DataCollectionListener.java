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
package jdk.graal.compiler.graphio.parsing.model;

import java.util.EventListener;

/**
 * Informs that data has been removed from the collection. This interface is work in progress - it
 * will be changed/extended in future versions.
 *
 * @author sdedic
 */
public interface DataCollectionListener extends EventListener {
    /**
     * Certain data has been loaded. This event is sent when some data has been materialized from
     * the dump. The data is either new, or even had existed before, but was GCed.
     *
     * @param evt description of data added
     */
    default void dataLoaded(DataCollectionEvent evt) {
    }

    /**
     * Some data has been removed from the collection.
     *
     * @param ev description of removed data
     */
    void dataRemoved(DataCollectionEvent ev);
}
