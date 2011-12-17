/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
/**
 *
 */
package com.sun.max.test;

import java.lang.reflect.*;

import com.sun.max.program.*;
import com.sun.max.test.JavaExecHarness.*;
import com.sun.max.util.*;

public class ReflectiveExecutor implements Executor {
    public void initialize(JavaTestCase c, boolean loadingPackages) {
        for (Method m : c.clazz.getDeclaredMethods()) {
            if (m.getName().equals("test") && (m.getModifiers() & Modifier.STATIC) != 0) {
                c.slot1 = m;
                return;
            }
        }
        throw ProgramError.unexpected("could not find static test() method");
    }

    public Object execute(JavaExecHarness.JavaTestCase c, Object[] vals) throws InvocationTargetException {
        try {
            for (int i = 0; i < vals.length; ++i) {
                Object o = vals[i];
                if (o instanceof JavaExecHarness.CodeLiteral) {
                    vals[i] = ((JavaExecHarness.CodeLiteral) o).resolve();
                }
            }
            final Method m = (Method) c.slot1;
            return m.invoke(c.clazz, vals);
        } catch (IllegalArgumentException e) {
            for (Object o : vals) {
                System.out.println("type=" + o.getClass() + ", " + o);
            }
            throw ProgramError.unexpected(e);
        } catch (IllegalAccessException e) {
            throw ProgramError.unexpected(e);
        }
    }

    public static void main(String[] args) {
        final Registry<TestHarness> reg = new Registry<TestHarness>(TestHarness.class, true);
        final JavaExecHarness javaExecHarness = new JavaExecHarness(new ReflectiveExecutor());
        reg.registerObject("java", javaExecHarness);
        final TestEngine e = new TestEngine(reg);
        e.parseAndRunTests(args);
        e.report(System.out);
    }
}
