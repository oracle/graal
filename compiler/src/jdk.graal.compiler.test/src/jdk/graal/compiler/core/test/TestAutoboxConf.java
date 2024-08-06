/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.lang.reflect.Field;
import java.util.ArrayList;

import jdk.graal.compiler.phases.common.BoxNodeOptimizationPhase;

/**
 * Independent test program to test if the {@link BoxNodeOptimizationPhase} respects the caching
 * specified by {@link Integer#valueOf(int)} in a libgraal setting.
 */
public class TestAutoboxConf {

    static Object S;

    public static Integer intBoxOptimized(Object o) {
        int i = (Integer) o;
        S = o;
        // box again, reuse if existing
        return i;
    }

    private static int getCacheHigh() {
        Class<?> cacheClass = Integer.class.getDeclaredClasses()[0];
        try {
            Field f = cacheClass.getDeclaredField("cache");
            f.setAccessible(true);
            Integer[] cache = (Integer[]) f.get(null);
            return cache[cache.length - 1];
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static void main(String[] args) {
        final int cacheLow = -128;
        final int cacheHigh = getCacheHigh();
        System.out.println("Working with cache=" + cacheHigh);
        final int listLength = 1000;
        ArrayList<Object> integersInterpreter = new ArrayList<>();
        for (int i = -listLength; i < listLength; i++) {
            Object boxed = i;
            // cache values in range, rebox on return
            integersInterpreter.add(boxed);
        }

        // let it compile
        for (int i = 0; i < 10000; i++) {
            intBoxOptimized(i);
        }

        // wait for compile (and dump)
        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ArrayList<Object> integersReuse = new ArrayList<>();
        for (int i = -listLength; i < listLength; i++) {
            Object boxed = integersInterpreter.get(i + listLength);
            // cache values in range, re-use if out of range
            integersReuse.add(intBoxOptimized(boxed));
        }
        for (int i = 0; i < integersInterpreter.size(); i++) {
            Object interpreterObject = integersInterpreter.get(i);
            Object objectReuse = integersReuse.get(i);
            long originalVal;
            Number n = (Number) interpreterObject;
            originalVal = n.longValue();
            if (originalVal >= cacheLow && originalVal <= cacheHigh) {
                // in cache, all must be the same objects
                if (interpreterObject != objectReuse) {
                    throw new Error("val=" + originalVal + " optimized version must remain cached object identities");
                }
            } else {
                if (interpreterObject != objectReuse) {
                    throw new Error("val=" + originalVal + " out of cache, optimized version must re-use the argument from the call and thus be the same as the interpreter object");
                }
            }
        }
        System.out.println("Successful execution!!");
    }

}
