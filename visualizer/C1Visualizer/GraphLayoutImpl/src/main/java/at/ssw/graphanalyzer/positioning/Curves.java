/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.graphanalyzer.positioning;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Curves {


    // Bsplines taken and modified from http://www.cse.unsw.edu.au/~lambert/splines/Bspline.java
    // the basis function for a cubic B spline
    private static float b(int i, float t) {
        switch (i) {
            case -2:
                return (((-t+3)*t-3)*t+1)/6;
            case -1:
                return (((3*t-6)*t)*t+4)/6;
            case 0:
                return (((-3*t+3)*t+3)*t+1)/6;
            case 1:
                return (t*t*t)/6;
        }
        return 0; //we only get here if an invalid i is specified
    }

    // evaluate a point on the B spline
    private static Point p(int i, float t, List<Point> points) {
        float px=0;
        float py=0;
        for (int j = -2; j<=1; j++){
            Point point = points.get(i + j);
            px += b(j,t)*point.x;
            py += b(j,t)*point.y;
        }
        return new Point(Math.round(px),Math.round(py));
    }


    public static List<Point> bsplines(List<Point> inputPoints, Point startRef, Point endRef, int steps) {
        if(inputPoints.size() == 2) return inputPoints;
        List<Point> points = new ArrayList<Point>(inputPoints);
        Point firstPoint = inputPoints.get(0);
        Point lastPoint = inputPoints.get(inputPoints.size() - 1);

        List<Point> result = new ArrayList<Point>();
        Point q = p(2, 0, points);
        for(int i= 2; i < points.size() - 1; i++) {
            for(int j=1; j<=steps; j++) {
                q = p(i, j/(float)steps, points);
                result.add(q);
            }
        }
        result.add(0, firstPoint);
        result.add(lastPoint);
        return result;
    }

    public static Point scaleVector(Point p, double len) {
        double scale = Math.sqrt(p.x * p.x + p.y * p.y);
        scale = len / scale;
        Point result = new Point(p);
        result.x = (int)Math.round((double)result.x * scale);
        result.y = (int)Math.round((double)result.y * scale);
        return result;
    }

    public static int quadraticOffset(Point p1, Point p2) {
        return (p1.x - p2.x) *(p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y);
    }

    public static List<Point> convertToBezier(List<Point> list, Point startRefPoint,
            Point endRefPoint, double bezierScale, int numberOfIntermediatePoints) {

        // Straight line, no bezier needed
        if (list.size() == 2) {
            return list;
        }

        List<Point> result = new ArrayList<Point>();

        Point prev = null;
        for (int i = 0; i < list.size() - 1; i++) {
            Point cur = list.get(i);
            Point next = list.get(i + 1);
            Point nextnext = null;
            if (i < list.size() - 2) {
                nextnext = list.get(i + 2);
            }

            Point bezierFrom = null;
            Point bezierTo = null;

            if (prev == null) {
                bezierFrom = new Point(cur.x - startRefPoint.x, cur.y
                        - startRefPoint.y);
            } else {
                bezierFrom = new Point(next.x - prev.x, next.y - prev.y);
            }

            if (nextnext == null) {
                bezierTo = new Point(next.x - endRefPoint.x, next.y
                        - endRefPoint.y);
            } else {
                bezierTo = new Point(cur.x - nextnext.x, cur.y - nextnext.y);
            }

            Point vec = new Point(cur.x - next.x, cur.y - next.y);
            double len = Math.sqrt(vec.x * vec.x + vec.y * vec.y);
            double scale = len * bezierScale;

            bezierFrom = scaleVector(bezierFrom, scale);
            bezierFrom.translate(cur.x, cur.y);
            bezierTo = scaleVector(bezierTo, scale);
            bezierTo.translate(next.x, next.y);

            List<Point> curList = bezier(cur, next, bezierFrom, bezierTo,
                    1 / (double) numberOfIntermediatePoints);
            for(Point p : curList) {
                if(result.size() > 0) {
                    Point other = result.get(result.size() - 1);
                    if(quadraticOffset(p, other) > 80) {
                        result.add(p);
                    }
                } else {
                    result.add(p);
                }
            }
            prev = cur;
        }

        result.add(list.get(list.size() - 1));
        return result;
    }

    private static List<Point> bezier(Point from, Point to, Point bezierFrom,
            Point bezierTo, double offset) {
        double t;
        List<Point> list = new ArrayList<Point>();

        // from = P0
        // to = P3
        // bezierFrom = P1
        // bezierTo = P2

        Point lastPoint = new Point(-1, -1);

        for (t = 0.0; t <= 1.0; t += offset) {
            double tInv = 1.0 - t;
            double tInv2 = tInv * tInv;
            double tInv3 = tInv2 * tInv;
            double t2 = t * t;
            double t3 = t2 * t;
            double x = tInv3 * from.x + 3 * t * tInv2 * bezierFrom.x + 3 * t2
                    * tInv * bezierTo.x + t3 * to.x;
            double y = tInv3 * from.y + 3 * t * tInv2 * bezierFrom.y + 3 * t2
                    * tInv * bezierTo.y + t3 * to.y;
            Point p = new Point((int)Math.round(x), (int)Math.round(y));
            if (lastPoint.x != p.x || lastPoint.y != p.y) {
                int offx = p.x - lastPoint.x;
                int offy = p.y - lastPoint.y;
                int off = offx * offx + offy * offy;
                if (list.size() == 1 || off > 2) {
                    list.add(p);
                    lastPoint = p;
                }
            }
        }

        list.remove(list.size() - 1);
        list.add(to);

        return list;
    }
}
