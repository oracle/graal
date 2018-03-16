/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
