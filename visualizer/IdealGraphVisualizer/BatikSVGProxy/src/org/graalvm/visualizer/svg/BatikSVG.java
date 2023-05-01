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
package org.graalvm.visualizer.svg;

import java.awt.Graphics2D;
import java.io.Writer;
import java.io.IOException;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;

public class BatikSVG {

    private BatikSVG() {
    }

    /**
     * Creates a graphics object that allows to be exported to SVG data using the
     * {@link #printToStream(Graphics2D, Writer, boolean) printToStream} method.
     * 
     * @return the newly created Graphics2D object or null if the library does not exist
     */
    public static Graphics2D createGraphicsObject() {
        DOMImplementation dom = GenericDOMImplementation.getDOMImplementation();
        org.w3c.dom.Document document = dom.createDocument("http://www.w3.org/2000/svg", "svg", null);
        SVGGeneratorContext ctx = SVGGeneratorContext.createDefault(document);
        ctx.setEmbeddedFontsOn(true);
        Graphics2D svgGenerator = new SVGGraphics2D(ctx, true);
        return svgGenerator;
    }

    /**
     * Serializes a graphics object to a stream in SVG format.
     * 
     * @param svgGenerator the graphics object. Only graphics objects created by the
     *            {@link #createGraphicsObject() createGraphicsObject} method are valid.
     * @param stream the stream to which the data is written
     * @param useCSS whether to use CSS styles in the SVG output
     */
    public static void printToStream(Graphics2D svgGenerator, Writer stream, boolean useCSS) throws IOException {
        assert svgGenerator instanceof SVGGraphics2D;
        SVGGraphics2D svgGraphics = (SVGGraphics2D)svgGenerator;
        svgGraphics.stream(stream, useCSS);
    }
}
