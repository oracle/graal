/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;

public class VisibilityTest extends ProxyLanguageEnvTest {
    private static Class<?> run;

    static void setRun(Class<?> cls) {
        Assert.assertNull(run);
        run = cls;
    }

    public abstract static class A1 {
        public abstract void run();
    }

    class NP1 extends A1 {
        @Override
        public void run() {
            setRun(NP1.class);
        }
    }

    class NP2 implements Runnable {
        public void run() {
            setRun(NP2.class);
        }
    }

    private interface PrivateRunnable extends Runnable {
    }

    class NP3 implements PrivateRunnable {
        public void run() {
            setRun(NP3.class);
        }
    }

    class NP4 extends A1 implements PrivateRunnable {
        @Override
        public void run() {
            setRun(NP4.class);
        }
    }

    abstract static class A2 implements PrivateRunnable {
        @Override
        public void run() {
            setRun(A2.class);
        }
    }

    class NP5 extends A2 {
    }

    class NP6 extends A2 {
        @Override
        public void run() {
            setRun(NP6.class);
        }
    }

    public interface I1 {
        default void run() {
            setRun(I1.class);
        }
    }

    public interface I2 extends Runnable {
        default void run() {
            setRun(I2.class);
        }
    }

    class NP7 implements I1 {
    }

    class NP8 extends A2 implements I1 {
        @Override
        public void run() {
            setRun(NP8.class);
        }
    }

    class NP9 extends A2 implements I1 {
    }

    public interface IS1 {
        static void run() {
            setRun(IS1.class);
        }
    }

    public class S1 implements IS1 {
        public void run() {
            setRun(S1.class);
        }
    }

    class S2 implements IS1 {
        public void run() {
            setRun(S2.class);
        }
    }

    abstract static class A3 {
        public void run() {
            setRun(A3.class);
        }
    }

    public class B1 extends A3 {
        // public bridge method
    }

    public interface ID1 {
        default void run() {
            setRun(ID1.class);
        }
    }

    public class D1 implements ID1 {
    }

    interface ID2 {
        default void run() {
            setRun(ID2.class);
        }
    }

    public class D2 implements ID2 {
    }

    abstract class A4<T> {
        public abstract T run(T arg);
    }

    public abstract class A5 extends A4<Integer> {
        @Override
        public Integer run(Integer arg) {
            setRun(A5.class);
            return arg;
        }
    }

    class C1 extends A5 {
        @Override
        public Integer run(Integer arg) {
            setRun(C1.class);
            return arg;
        }
    }

    public interface I5<T> {
        T run(T arg);
    }

    abstract class A6<T> implements I5<T> {
        @Override
        public T run(T arg) {
            setRun(A6.class);
            return arg;
        }
    }

    public class C2 extends A6<Integer> {
        // may or may not have public bridge method
    }

    public abstract class A7<T> {
        public abstract T run(T arg);
    }

    abstract class A8<T> extends A7<T> {
        @Override
        public T run(T arg) {
            setRun(A8.class);
            return arg;
        }
    }

    public class C3 extends A8<Integer> {
        // may or may not have public bridge method
    }

    @Test
    public void testNonPublicClassPublicSuper() throws InteropException {
        invokeRun(new NP1(), NP1.class);
        invokeRun(new NP2(), NP2.class);
        invokeRun(new NP3(), NP3.class);
        invokeRun(new NP4(), NP4.class);
        invokeRun(new NP5(), A2.class);
        invokeRun(new NP6(), NP6.class);
        invokeRun(new NP7(), I1.class);
        invokeRun(new NP8(), NP8.class);
        invokeRun(new NP9(), A2.class);
    }

    @Test
    public void testStaticMethodNotInherited() throws InteropException {
        invokeRun(new S1(), S1.class);
        invokeRun(new S2(), null);
    }

    @Test
    public void testPublicClassBridgeMethod() throws InteropException {
        invokeRun(new B1(), A3.class);
    }

    @Test
    public void testPublicClassDefaultMethod() throws InteropException {
        invokeRun(new D1(), ID1.class);
        invokeRun(new D2(), null);
    }

    @Test
    public void testComplexHierachy() throws InteropException {
        invokeRun(new C1(), C1.class, 42);
        invokeRun(new C2(), A6.class, 42);
        invokeRun(new C3(), A8.class, 42);
    }

    private Object invokeRun(Object obj, Class<?> methodClass, Object... args) throws InteropException {
        TruffleObject receiver = asTruffleObject(obj);
        try {
            Object result = ForeignAccess.sendInvoke(Message.INVOKE.createNode(), receiver, "run", args);
            Assert.assertSame(methodClass, run);
            return result;
        } catch (UnknownIdentifierException uie) {
            Assert.assertSame(methodClass, null);
            Assert.assertNull(run);
            return null;
        } finally {
            run = null;
        }
    }

    public static class F1 {
        public int a = 10;
        public int b = 20;
        public static int s = 30;
    }

    public static class F2 extends F1 {
        public static int a = 30;
        public int b = 40;
    }

    private static class F3 extends F1 {
        public int a = 30;
        public int c = 40;
    }

    public static class F4 extends F3 {
    }

    @Test
    public void testPublicClassPublicField() throws InteropException {
        Assert.assertEquals(10, read(new F1(), "a"));
        Assert.assertEquals(40, read(new F2(), "b"));

        // get instance field from superclass instead of static field in subclass
        Assert.assertEquals(10, read(new F2(), "a"));
        // static fields can be accessed via class
        Assert.assertEquals(30, read(F2.class, "a"));
        // static fields in superclass not visible from subclass
        Assert.assertNull(read(F2.class, "s"));
    }

    @Test
    public void testPrivateClassPublicField() throws InteropException {
        // public field in private superclass of public class
        Assert.assertNull(read(new F4(), "c"));
        // public field in public superclass of private class (not shadowed)
        Assert.assertEquals(20, read(new F3(), "b"));
    }

    @Test
    public void testShadowedField() throws InteropException {
        // public field in public superclass but shadowed by public field in private class
        Assert.assertNull(read(new F3(), "a"));
        Assert.assertNull(read(new F4(), "a"));
    }

    private Object read(Object obj, String name) throws InteropException {
        TruffleObject receiver = obj instanceof Class<?> ? asTruffleHostSymbol((Class<?>) obj) : asTruffleObject(obj);
        try {
            return ForeignAccess.sendRead(Message.READ.createNode(), receiver, name);
        } catch (UnknownIdentifierException uie) {
            return null;
        }
    }
}
