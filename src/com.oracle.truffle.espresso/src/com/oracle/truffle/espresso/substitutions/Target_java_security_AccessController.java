/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.substitutions;

import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * The AccessController class is used for access control operations and decisions.
 *
 * <p>
 * <b>Security note:</b> These substitutions effectively <b>DISABLE</b> access control checks
 * defeating the AccessController purpose.
 */
@EspressoSubstitutions
public final class Target_java_security_AccessController {

    @Substitution
    public static @Host(Object.class) StaticObject doPrivileged(@Host(PrivilegedAction.class) StaticObject action) {
        Method run = action.getKlass().lookupMethod(Name.run, Signature.Object);
        return (StaticObject) run.invokeDirect(action);
    }

    @Substitution(methodName = "doPrivileged")
    public static @Host(Object.class) StaticObject doPrivileged_PrivilegedAction_AccessControlContext(
                    @Host(PrivilegedAction.class) StaticObject action,
                    @SuppressWarnings("unused") @Host(AccessControlContext.class) StaticObject context) {
        return doPrivileged(action);
    }

    @Substitution(methodName = "doPrivileged")
    public static @Host(Object.class) StaticObject doPrivileged_PrivilegedExceptionAction(@Host(PrivilegedExceptionAction.class) StaticObject action) {
        return doPrivileged_PrivilegedExceptionAction_AccessControlContext(action, StaticObject.NULL);
    }

    @Substitution(methodName = "doPrivileged")
    public static @Host(Object.class) StaticObject doPrivileged_PrivilegedExceptionAction_AccessControlContext(
                    @Host(PrivilegedExceptionAction.class) StaticObject action,
                    @SuppressWarnings("unused") @Host(AccessControlContext.class) StaticObject context) {
        Method run = null;
        try {
            run = action.getKlass().lookupMethod(Name.run, Signature.Object);
            return (StaticObject) run.invokeDirect(action);
        } catch (EspressoException e) {
            Meta meta = action.getKlass().getMeta();
            // Wrap exception in PrivilegedActionException if it is a declared exception.
            if (run == null) {
                throw e;
            }
            if (meta.Exception.isAssignableFrom(e.getException().getKlass()) &&
                            !meta.RuntimeException.isAssignableFrom(e.getException().getKlass())) {
                StaticObject wrapper = meta.PrivilegedActionException.allocateInstance();
                meta.PrivilegedActionException_init_Exception.invokeDirect(wrapper, e.getException());
                throw new EspressoException(wrapper);
            }
            throw e;

        } catch (Exception e) {
            e.printStackTrace();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Substitution
    public static @Host(AccessControlContext.class) StaticObject getStackAccessControlContext() {
        return StaticObject.NULL;
    }

}
