/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.polyglot.groovy;

import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

final class GroovyV4IndyInterfaceFeature extends GroovyIndyInterfaceFeature {

    // Groovy 4 has the invalidateSwitchPoints method in the v7 package
    static final String INDY_INTERFACE_CLASS_NAME = "org.codehaus.groovy.vmplugin.v7.IndyInterface";

    static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(com.oracle.svm.polyglot.groovy.GroovyV4IndyInterfaceFeature.class);
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return super.isInConfiguration(access, INDY_INTERFACE_CLASS_NAME);
    }
}

final class GroovyV5IndyInterfaceFeature extends GroovyIndyInterfaceFeature {

    // Groovy 5 has the invalidateSwitchPoints method in the v8 package
    static final String INDY_INTERFACE_CLASS_NAME = "org.codehaus.groovy.vmplugin.v8.IndyInterface";

    static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(com.oracle.svm.polyglot.groovy.GroovyV5IndyInterfaceFeature.class);
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return super.isInConfiguration(access, INDY_INTERFACE_CLASS_NAME);
    }
}

class GroovyIndyInterfaceFeature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return false;
    }

    protected boolean isInConfiguration(IsInConfigurationAccess access, String className) {
        Class<?> indyInterfaceClass = access.findClassByName(className);
        if (indyInterfaceClass == null) {
            return false;
        }
        try {
            return indyInterfaceClass.getDeclaredMethod("invalidateSwitchPoints") != null;
        } catch (ReflectiveOperationException e) {
            return false; // nothing to substitute
        }
    }
}

@TargetClass(className = com.oracle.svm.polyglot.groovy.GroovyV4IndyInterfaceFeature.INDY_INTERFACE_CLASS_NAME, onlyWith = com.oracle.svm.polyglot.groovy.GroovyV4IndyInterfaceFeature.IsEnabled.class)
final class Target_org_codehaus_groovy_vmplugin_v7_IndyInterface_invalidateSwitchPoints {
    @Substitute
    protected static void invalidateSwitchPoints() {
        throw new Error("IndyInterface.invalidateSwitchPoints() is not supported.");
    }
}

@TargetClass(className = com.oracle.svm.polyglot.groovy.GroovyV5IndyInterfaceFeature.INDY_INTERFACE_CLASS_NAME, onlyWith = com.oracle.svm.polyglot.groovy.GroovyV5IndyInterfaceFeature.IsEnabled.class)
final class Target_org_codehaus_groovy_vmplugin_v8_IndyInterface_invalidateSwitchPoints {
    @Substitute
    protected static void invalidateSwitchPoints() {
        throw new Error("IndyInterface.invalidateSwitchPoints() is not supported.");
    }
}

public class GroovySubstitutions {
}
