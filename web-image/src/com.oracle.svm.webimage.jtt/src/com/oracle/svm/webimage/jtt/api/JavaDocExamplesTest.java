/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.jtt.api;

import java.util.Random;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSObject;

public class JavaDocExamplesTest {
    public static final String[] OUTPUT = {
                    // JS
                    "User message: Initialization completed.", "3",
                    // JSObject
                    "3.2", "4.8", "5.4",
                    "JavaScript<function;", "JavaScript<number; 5.0>",
                    "0.3", "0.4", "0.5", "Type mismatch: 'whoops' cannot be coerced to 'Double'.",
                    "1.5, 2.5", "0.0, 0.0", "1.25, 0.5",
                    "640x480",
                    "1024", "true",
    };

    public static void main(String[] args) {
        jsExamples();
        jsObjectExamples();
    }

    /**
     * See {@link JS} annotation.
     */
    private static void jsExamples() {
        // Examples
        reportToUser("Initialization completed.");
        System.out.println(hashCode(new Galois(4, 7)));
    }

    @JS("console.log('User message: '.concat(message));")
    public static native void reportToUser(String message);

    @JS("return obj.hashCode();")
    public static native Integer hashCode(Object obj);

    /**
     * See {@link JSObject} class.
     */
    private static void jsObjectExamples() {
        // Anonymous JavaScript objects
        JSObject pair = createPair(3.2, 4.8);

        System.out.println(pair.get("x"));
        System.out.println(pair.get("y"));

        pair.set("x", 5.4);
        System.out.println(pair.get("x"));

        // Anonymous JavaScript functions
        JSObject add = addFunction();
        System.out.println(add.toString().substring(0, 20));
        JSNumber result = (JSNumber) add.invoke(JSNumber.of(2), JSNumber.of(3));
        System.out.println(result);

        // Declaring JavaScript classes in Java
        Point p = new Point(0.3, 0.4);
        System.out.println(p.x);
        System.out.println(p.y);
        System.out.println(p.absolute());

        try {
            corruptedAccess(p);
        } catch (ClassCastException e) {
            System.out.println("Type mismatch: " + e.getMessage());
        }

        // Passing JSObjects between Java and JavaScript
        Point p0 = new Point(1.5, 2.5);
        System.out.println(p0.x + ", " + p0.y);
        reset(p0, 0.0, 0.0);
        System.out.println(p0.x + ", " + p0.y);

        Point p1 = create(Point.class, 1.25, 0.5);
        System.out.println(p1.x + ", " + p1.y);

        // Importing existing JavaScript classes
        Rectangle r = new Rectangle(640, 480);
        System.out.println(r.width + "x" + r.height);

        // Exporting Java classes to JavaScript
        JSObject vm = vm(Class.class);
        byte[] bytes = useRandomizer(vm);
        System.out.println(bytes.length);
        int sum = 0;
        for (byte b : bytes) {
            sum += Math.abs(b);
        }
        System.out.println(sum != 0);
    }

    @JS("return {x: x, y: y};")
    public static native JSObject createPair(double x, double y);

    @JS("return (a, b) => a + b;")
    private static native JSObject addFunction();

    @JS("p.x = s;")
    public static native void corrupt(Point p, String s);

    private static void corruptedAccess(Point p) {
        corrupt(p, "whoops");
        System.out.println(p.x);
    }

    @JS("p.x = x; p.y = y;")
    private static native void reset(Point p, double x, double y);

    @JS("return new pointType(x, y);")
    private static native Point create(Class<Point> pointType, double x, double y);

    @JS("return c.$vm;")
    private static native JSObject vm(Class<?> c);

    @JS("const Randomizer = vm.exports.com.oracle.svm.webimage.jtt.api.Randomizer; const r = new Randomizer(); return r.randomBytes(vm.as(1024, 'java.lang.Integer'));")
    private static native byte[] useRandomizer(JSObject vm);

}

class Galois {
    private final int x;
    private final int modulo;

    Galois(int x, int modulo) {
        this.x = x;
        this.modulo = modulo;
    }

    @Override
    public int hashCode() {
        return x ^ modulo;
    }
}

class Point extends JSObject {
    protected double x;
    protected double y;

    Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double absolute() {
        return Math.sqrt(x * x + y * y);
    }
}

@JS.Import
@JS.Code.Include("/com/oracle/svm/webimage/jtt/api/java-doc-examples-test.js")
class Rectangle extends JSObject {
    protected int width;
    protected int height;

    @SuppressWarnings("unused")
    Rectangle(int width, int height) {
    }
}

@JS.Export
class Randomizer extends JSObject {
    protected Random rng = new Random(719513L);

    public byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        rng.nextBytes(bytes);
        return bytes;
    }
}
