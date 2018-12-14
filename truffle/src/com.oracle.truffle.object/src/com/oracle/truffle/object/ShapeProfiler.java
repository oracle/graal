/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.object;

import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;

@SuppressWarnings("deprecation")
class ShapeProfiler {
    private static final String LINE_SEPARATOR = "***********************************************";
    private static final String BULLET = "* ";
    private static final String TOKEN_SEPARATOR = "\t";
    private final ConcurrentLinkedQueue<DynamicObject> queue;

    ShapeProfiler() {
        queue = new ConcurrentLinkedQueue<>();
    }

    public void track(DynamicObject obj) {
        queue.add(obj);
    }

    public void dump(PrintWriter out) {
        ShapeStats globalStats = new ShapeStats("Cumulative results for all shapes");
        for (DynamicObject obj : queue) {
            Shape shape = obj.getShape();
            globalStats.profile(shape);
        }

        globalStats.dump(out);
    }

    public void dump(PrintWriter out, int topResults) {
        if (topResults > 0) {
            IdentityHashMap<Shape, ShapeStats> shapeMap = new IdentityHashMap<>();

            for (DynamicObject obj : queue) {
                Shape shape = obj.getShape();
                ShapeStats stats = shapeMap.get(shape);
                if (stats == null) {
                    shapeMap.put(shape, stats = new ShapeStats(createLabel(shape)));
                }
                stats.profile(shape);
            }

            List<ShapeStats> allStats = new ArrayList<>(shapeMap.values());
            Collections.sort(allStats, new Comparator<ShapeStats>() {
                public int compare(ShapeStats a, ShapeStats b) {
                    return Long.compare(b.objects, a.objects);
                }
            });

            int top = Math.min(topResults, allStats.size());
            ShapeStats avgStats = new ShapeStats("Cumulative results for top " + top + " shapes");
            for (int i = 0; i < top; i++) {
                ShapeStats stats = allStats.get(i);
                stats.setLabel("Shape " + (i + 1) + ": " + stats.getLabel());
                stats.dump(out);
                avgStats.add(stats);
            }
            avgStats.dump(out);
        }
        // Dump also cumulative results.
        dump(out);
    }

    private static String createLabel(Shape shape) {
        String label = shape.toString();
        return label.substring(label.indexOf('{') + 1, label.lastIndexOf('}'));
    }

    private static class ShapeStats {
        private String label;
        private long objects;
        private long oac;
        private long oas;
        private long ofs;
        private long pac;
        private long pas;
        private long pfs;

        ShapeStats(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public void profile(Shape shape) {
            objects++;
            oac += ((ShapeImpl) shape).getObjectArrayCapacity();
            oas += ((ShapeImpl) shape).getObjectArraySize();
            ofs += ((ShapeImpl) shape).getObjectFieldSize();
            pac += ((ShapeImpl) shape).getPrimitiveArrayCapacity();
            pas += ((ShapeImpl) shape).getPrimitiveArraySize();
            pfs += ((ShapeImpl) shape).getPrimitiveFieldSize();
        }

        public void add(ShapeStats stats) {
            objects += stats.objects;
            oac += stats.oac;
            oas += stats.oas;
            ofs += stats.ofs;
            pac += stats.pac;
            pas += stats.pas;
            pfs += stats.pfs;
        }

        public void dump(PrintWriter out) {
            DecimalFormat format = new DecimalFormat("###.####");
            out.println(LINE_SEPARATOR);
            out.println(BULLET + label);
            out.println(LINE_SEPARATOR);
            out.println(BULLET + "Allocated objects:\t" + objects);
            out.println(BULLET + "Total object array capacity:\t" + oac);
            out.println(BULLET + "Total object array size:\t" + oas);
            out.println(BULLET + "Total object field size:\t" + ofs);
            out.println(BULLET + "Average object array capacity:\t" + avgOAC(format));
            out.println(BULLET + "Average object array size:\t" + avgOAS(format));
            out.println(BULLET + "Average object field size:\t" + avgOFS(format));
            out.println(LINE_SEPARATOR);
            out.println(BULLET + "Total primitive array capacity:\t" + pac);
            out.println(BULLET + "Total primitive array size:\t" + pas);
            out.println(BULLET + "Total primitive field size:\t" + pfs);
            out.println(BULLET + "Average primitive array capacity:\t" + avgPAC(format));
            out.println(BULLET + "Average primitive array size:\t" + avgPAS(format));
            out.println(BULLET + "Average primitive field size:\t" + avgPFS(format));
            out.println(LINE_SEPARATOR);
            out.println(BULLET + toString());
            out.println(LINE_SEPARATOR + "\n");
            out.flush();
        }

        @Override
        public String toString() {
            DecimalFormat format = new DecimalFormat("###.####");
            // @formatter:off
            return "{" + label + "}" + TOKEN_SEPARATOR
                   + objects + TOKEN_SEPARATOR
                   + avgOAC(format) + TOKEN_SEPARATOR
                   + avgOAS(format) + TOKEN_SEPARATOR
                   + avgOFS(format) + TOKEN_SEPARATOR
                   + avgPAC(format) + TOKEN_SEPARATOR
                   + avgPAS(format) + TOKEN_SEPARATOR
                   + avgPFS(format);
            // @formatter:on
        }

        private String avgOAC(DecimalFormat format) {
            return format.format((double) oac / objects);
        }

        private String avgOAS(DecimalFormat format) {
            return format.format((double) oas / objects);
        }

        private String avgOFS(DecimalFormat format) {
            return format.format((double) ofs / objects);
        }

        private String avgPAC(DecimalFormat format) {
            return format.format((double) pac / objects);
        }

        private String avgPAS(DecimalFormat format) {
            return format.format((double) pas / objects);
        }

        private String avgPFS(DecimalFormat format) {
            return format.format((double) pfs / objects);
        }
    }

    public static ShapeProfiler getInstance() {
        return shapeProf;
    }

    private static final ShapeProfiler shapeProf;

    static {
        if (com.oracle.truffle.object.ObjectStorageOptions.Profile) {
            shapeProf = new ShapeProfiler();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    getInstance().dump(new PrintWriter(System.out), com.oracle.truffle.object.ObjectStorageOptions.ProfileTopResults);
                }
            });
        } else {
            shapeProf = null;
        }
    }
}
