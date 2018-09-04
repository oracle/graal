package com.oracle.truffle.espresso.impl;

import static com.oracle.truffle.espresso.impl.EspressoVMConfig.config;
import static java.lang.reflect.Modifier.ABSTRACT;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.INTERFACE;
import static java.lang.reflect.Modifier.NATIVE;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PROTECTED;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static java.lang.reflect.Modifier.STRICT;
import static java.lang.reflect.Modifier.SYNCHRONIZED;
import static java.lang.reflect.Modifier.TRANSIENT;
import static java.lang.reflect.Modifier.VOLATILE;

import java.lang.reflect.Modifier;

/**
 * The non-public modifiers in {@link Modifier} that need to be retrieved from
 * {@link EspressoVMConfig}.
 */
public final class EspressoModifiers {

    // TODO(peterssen): Initialize properly non-standard flags, all are set to 0.
    // @formatter:off
    public static final int ANNOTATION = config().jvmAccAnnotation;
    public static final int ENUM       = config().jvmAccEnum;
    public static final int VARARGS    = config().jvmAccVarargs;
    public static final int BRIDGE     = config().jvmAccBridge;
    public static final int SYNTHETIC  = config().jvmAccSynthetic;
    // @formatter:on

    public static int jvmClassModifiers() {
        return PUBLIC | FINAL | INTERFACE | ABSTRACT | ANNOTATION | ENUM | SYNTHETIC;
    }

    public static int jvmMethodModifiers() {
        return PUBLIC | PRIVATE | PROTECTED | STATIC | FINAL | SYNCHRONIZED | BRIDGE | VARARGS | NATIVE | ABSTRACT | STRICT | SYNTHETIC;
    }

    public static int jvmFieldModifiers() {
        return PUBLIC | PRIVATE | PROTECTED | STATIC | FINAL | VOLATILE | TRANSIENT | ENUM | SYNTHETIC;
    }
}
