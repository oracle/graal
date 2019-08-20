/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

// Checkstyle: stop
import java.io.File;

class PureMustBeSafe {
    static int v;
    static {
        v = 1;
        v = 42;
    }
}

class InitializesPureMustBeSafe {
    static int v;
    static {
        v = PureMustBeSafe.v;
    }
}

/* this one should not even show up */
class NonPureAccessedFinal {
    static final int v = 1;
    static {
        System.out.println("Must not be called at runtime or compile time.");
        System.exit(1);
    }
}

class PureCallMustBeSafe {
    static int v;
    static {
        v = TestClassInitializationMustBeSafe.pure();
    }
}

class NonPureMustBeDelayed {
    static int v = 1;
    static {
        System.out.println("Analysis should not reach here.");
    }
}

class InitializesNonPureMustBeDelayed {
    static int v = NonPureMustBeDelayed.v;
}

class SystemPropReadMustBeDelayed {
    static int v = 1;
    static {
        System.getProperty("test");
    }
}

class SystemPropWriteMustBeDelayed {
    static int v = 1;
    static {
        System.setProperty("test", "");
    }
}

class StartsAThreadMustBeDelayed {
    static int v = 1;
    static {
        new Thread().start();
    }
}

class CreatesAFileMustBeDelayed {
    static int v = 1;
    static File f = new File("./");
}

class CreatesAnExceptionMustBeDelayed {
    static Exception e;
    static {
        e = new Exception("should fire at runtime");
    }
}

class ThrowsAnExceptionMustBeDelayed {
    static int v = 1;
    static {
        if (PureMustBeSafe.v == 42) {
            throw new RuntimeException("should fire at runtime");
        }
    }
}

interface PureInterfaceMustBeSafe {
}

class PureSubclassMustBeDelayed extends SuperClassMustBeDelayed {
    static int v = 1;
}

class SuperClassMustBeDelayed implements PureInterfaceMustBeSafe {
    static {
        System.out.println("Delaying this class.");
    }
}

interface InterfaceNonPureMustBeDelayed {
    int v = B.v;

    class B {
        static int v = 1;
        static {
            System.out.println("Delaying this class.");
        }
    }
}

interface InterfaceNonPureDefaultMustBeDelayed {
    int v = B.v;

    class B {
        static int v = 1;
        static {
            System.out.println("Delaying this class.");
        }
    }

    default int m() {
        return v;
    }
}

class PureSubclassInheritsDelayedInterfaceMustBeSafe implements InterfaceNonPureMustBeDelayed {
    static int v = 1;
}

class PureSubclassInheritsDelayedDefaultInterfaceMustBeDelayed implements InterfaceNonPureDefaultMustBeDelayed {
    static int v = 1;
}

class ImplicitExceptionInInitializerMustBeDelayed {

    static int a = 10;
    static int b = 0;
    static int res;

    static {
        res = a / b;
    }
}

class PureDependsOnImplicitExceptionMustBeDelayed {

    static int a;

    static {
        a = ImplicitExceptionInInitializerMustBeDelayed.res;
    }
}

/**
 * Suffixes MustBeSafe and MustBeDelayed are parsed by an external script in the tests after the
 * image is built. Every class that ends with `MustBeSafe` should be eagerly initialized and every
 * class that ends with `MustBeDelayed` should be initialized at runtime.
 *
 * NOTE: using assert in a method will make a class initialized at runtime.
 */
public class TestClassInitializationMustBeSafe {
    static int pure() {
        return PureCallMustBeSafe.v + PureCallMustBeSafe.v + PureCallMustBeSafe.v + transitivelyPure();
    }

    private static int transitivelyPure() {
        return PureCallMustBeSafe.v + PureCallMustBeSafe.v + PureCallMustBeSafe.v;
    }

    public static void main(String[] args) {
        System.out.println(PureMustBeSafe.v);
        System.out.println(PureCallMustBeSafe.v);
        System.out.println(InitializesPureMustBeSafe.v);
        System.out.println(NonPureMustBeDelayed.v);
        System.out.println(NonPureAccessedFinal.v);
        System.out.println(InitializesNonPureMustBeDelayed.v);
        System.out.println(SystemPropReadMustBeDelayed.v);
        System.out.println(SystemPropWriteMustBeDelayed.v);
        System.out.println(StartsAThreadMustBeDelayed.v);
        System.out.println(CreatesAFileMustBeDelayed.v);
        System.out.println(PureSubclassMustBeDelayed.v);
        System.out.println(PureSubclassInheritsDelayedInterfaceMustBeSafe.v);
        System.out.println(PureSubclassInheritsDelayedDefaultInterfaceMustBeDelayed.v);
        System.out.println(InterfaceNonPureMustBeDelayed.v);
        try {
            System.out.println(ThrowsAnExceptionMustBeDelayed.v);
        } catch (Throwable t) {
            System.out.println(CreatesAnExceptionMustBeDelayed.e.getMessage());
        }
        try {
            System.out.println(ImplicitExceptionInInitializerMustBeDelayed.res);
            throw new RuntimeException("should not reach here");
        } catch (ExceptionInInitializerError ae) {
            if (!(ae.getCause() instanceof ArithmeticException)) {
                throw new RuntimeException("should not reach here");
            }
        }
        try {
            System.out.println(PureDependsOnImplicitExceptionMustBeDelayed.a);
            throw new RuntimeException("should not reach here");
        } catch (NoClassDefFoundError ae) {
            /* This is OK */
        }
    }
}
// Checkstyle: resume
