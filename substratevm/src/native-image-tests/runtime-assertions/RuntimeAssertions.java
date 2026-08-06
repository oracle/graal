/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package runtimeassertions;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BooleanSupplier;

/// Exercises assertion directives in separate executions of one native image.
public final class RuntimeAssertions {

    /// Names a bootstrap-loaded JDK class that Native Image initializes at run time.
    private static final String RUNTIME_SYSTEM_CLASS_NAME = "java.util.concurrent.ThreadLocalRandom$ThreadLocalRandomProxy";

    private RuntimeAssertions() {
    }

    /// Runs the assertion check selected by the exact runtime option string.
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new AssertionError("Expected one assertion option string");
        }

        switch (args[0]) {
            case "" -> {
                checkAssertionState(ClassDisabled.class, false, ClassDisabled::assertionsEnabled);
                checkSystemAssertionState(false);
            }
            case "-ea" -> {
                checkAssertionState(ClassEnabled.class, true, ClassEnabled::assertionsEnabled);
                checkSystemAssertionState(false);
                checkNestedClassAssertionState();
            }
            case "-ea -da" -> checkAssertionState(ClassDisabled.class, false, ClassDisabled::assertionsEnabled);
            case "-ea:runtimeassertions.ClassEnabled" -> {
                checkAssertionState(ClassEnabled.class, true, ClassEnabled::assertionsEnabled);
                checkAssertionState(ClassDisabled.class, false, ClassDisabled::assertionsEnabled);
            }
            case "-ea:runtimeassertions... -da:runtimeassertions.ClassDisabled" -> {
                checkAssertionState(PackageEnabled.class, true, PackageEnabled::assertionsEnabled);
                checkAssertionState(ClassDisabled.class, false, ClassDisabled::assertionsEnabled);
            }
            case "-ea -da:runtimeassertions... -ea:runtimeassertions.ClassEnabled" -> {
                checkAssertionState(PackageEnabled.class, false, PackageEnabled::assertionsEnabled);
                checkAssertionState(ClassEnabled.class, true, ClassEnabled::assertionsEnabled);
            }
            case "-esa" -> checkSystemAssertionState(true);
            case "-esa -dsa" -> checkSystemAssertionState(false);
            case "-enableassertions:runtimeassertions.ClassEnabled -enablesystemassertions" -> {
                checkAssertionState(ClassEnabled.class, true, ClassEnabled::assertionsEnabled);
                checkSystemAssertionState(true);
            }
            case "-enableassertions -disableassertions -enablesystemassertions -disablesystemassertions" -> {
                checkAssertionState(ClassDisabled.class, false, ClassDisabled::assertionsEnabled);
                checkSystemAssertionState(false);
            }
            case "runtime-loaded:false" -> checkRuntimeLoadedAssertionState(false);
            case "runtime-loaded:true" -> checkRuntimeLoadedAssertionState(true);
            case "legacy-build-time-status" -> checkLegacyBuildTimeAssertionState(args);
            case "code-excluded" -> checkExcludedAssertionCode();
            default -> throw new AssertionError("Unknown assertion options: " + args[0]);
        }
    }

    /// Checks both the reported status and execution of an assertion in a runtime-initialized class.
    private static void checkAssertionState(Class<?> clazz, boolean expected, BooleanSupplier assertionsEnabled) {
        if (clazz.desiredAssertionStatus() != expected) {
            throw new AssertionError("Unexpected assertion status for " + clazz.getName());
        }
        if (assertionsEnabled.getAsBoolean() != expected) {
            throw new AssertionError("Unexpected assertion execution for " + clazz.getName());
        }
    }

    /// Checks the runtime default reported for bootstrap-loaded classes.
    private static void checkSystemAssertionState(boolean expected) {
        Class<?> runtimeSystemClass;
        try {
            runtimeSystemClass = Class.forName(RUNTIME_SYSTEM_CLASS_NAME, false, null);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Could not load runtime-initialized system class", ex);
        }
        if (runtimeSystemClass.desiredAssertionStatus() != expected) {
            throw new AssertionError("Unexpected system assertion status");
        }
    }

    /// Checks that a nested class uses the assertion status of its lexically enclosing top-level class.
    private static void checkNestedClassAssertionState() {
        ClassLoader loader = RuntimeAssertions.class.getClassLoader();
        loader.setPackageAssertionStatus("runtimeassertions", false);
        loader.setClassAssertionStatus("runtimeassertions.AssertionTopLevel$Nested", true);
        if (new AssertionTopLevel.Nested().assertionsEnabled()) {
            throw new AssertionError("Nested assertion did not use the top-level class assertion status");
        }
    }

    /// Loads a class after image construction and checks that runtime directives control its status.
    private static void checkRuntimeLoadedAssertionState(boolean expected) {
        byte[] classBytes;
        try (InputStream stream = RuntimeAssertions.class.getResourceAsStream("/runtimeassertions/RuntimeLoadedAssertions.class")) {
            if (stream == null) {
                throw new AssertionError("Could not find run-time assertion class resource");
            }
            classBytes = stream.readAllBytes();
        } catch (IOException ex) {
            throw new AssertionError("Could not read run-time assertion class resource", ex);
        }

        Class<?> clazz = new RuntimeAssertionClassLoader().define(classBytes);
        boolean actual;
        try {
            actual = (Boolean) clazz.getMethod("assertionsEnabled").invoke(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Could not invoke run-time assertion probe", ex);
        }
        checkAssertionState(clazz, expected, () -> actual);
    }

    /// Confirms that legacy Java option mode fixes image-class assertion status at image build time.
    private static void checkLegacyBuildTimeAssertionState(String[] args) {
        /* Keep the receiver dynamic so Class.desiredAssertionStatus is evaluated at run time. */
        Class<?> runtimeInitializedClass = args.hashCode() == 0 ? RuntimeAssertions.class : ClassEnabled.class;
        checkAssertionState(runtimeInitializedClass, true, ClassEnabled::assertionsEnabled);
    }

    /// Confirms that legacy Java option mode still eliminates assertion-only code by default.
    private static void checkExcludedAssertionCode() {
        if (ClassEnabled.assertionsEnabled()) {
            throw new AssertionError("Assertion code was retained in legacy Java option mode");
        }
    }

}

final class ClassEnabled {
    /// Reports whether assertions are enabled for this class.
    static boolean assertionsEnabled() {
        boolean enabled = false;
        assert (enabled = true) == true;
        return enabled;
    }
}

final class PackageEnabled {
    /// Reports whether assertions are enabled for this class.
    static boolean assertionsEnabled() {
        boolean enabled = false;
        assert (enabled = true) == true;
        return enabled;
    }
}

final class ClassDisabled {
    /// Reports whether assertions are enabled for this class.
    static boolean assertionsEnabled() {
        boolean enabled = false;
        assert (enabled = true) == true;
        return enabled;
    }
}

/// Exercises assertion status lookup for a nested class.
final class AssertionTopLevel {
    static final class Nested {
        boolean assertionsEnabled() {
            boolean enabled = false;
            assert (enabled = true) == true;
            return enabled;
        }
    }
}

/// Defines run-time assertion classes without delegating to the image class loader.
final class RuntimeAssertionClassLoader extends ClassLoader {
    RuntimeAssertionClassLoader() {
        super(null);
    }

    /// Defines the supplied class bytes as a new run-time class.
    Class<?> define(byte[] classBytes) {
        return defineClass(null, classBytes, 0, classBytes.length);
    }
}
