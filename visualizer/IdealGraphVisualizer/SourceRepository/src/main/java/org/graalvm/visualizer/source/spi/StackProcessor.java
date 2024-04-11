/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.graalvm.visualizer.source.spi;

import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.ProcessorContext;

import java.util.List;

/**
 * StackProcessor is initialized with ProcessorContext, then asked to process
 * zero to several InputNodes into ProcessingLocations.
 */
public interface StackProcessor {
    /**
     * Attaches the context information to the processor
     *
     * @param ctx
     */
    public void attach(ProcessorContext ctx);

    /**
     * Processes a single Node, returning Location objects. Note that before the Locations
     * are published to other clients, they may be canonicalized, so clients may see
     * different instances than (though they would be equals to) returned from the processor.
     *
     * @return stack, or {@code null} if unable to parse
     */
    public List<Location> processStack(InputNode node);

    public interface Factory {
        /**
         * MIME type of the handled language. The produced stack information will
         * be marked with this MIME type.
         *
         * @return mime type
         */
        public String[] getLanguageIDs();

        /**
         * Creates a processor instance based on graph/context information
         *
         * @param ctx the context
         * @return processor instance
         */
        public StackProcessor createProcessor(ProcessorContext ctx);
    }
}
