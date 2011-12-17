/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.max.graal.hotspot;

import java.lang.reflect.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.hotspot.logging.*;

public class HotSpotOptions {

    public static void setDefaultOptions() {
        GraalOptions.CommentedAssembly = false;
        GraalOptions.MethodEndBreakpointGuards = 2;
        GraalOptions.ResolveClassBeforeStaticInvoke = false;
    }

    public static boolean setOption(String option) {
        if (option.length() == 0) {
            return false;
        }

        Object value = null;
        String fieldName = null;
        String valueString = null;

        char first = option.charAt(0);
        if (first == '+' || first == '-') {
            fieldName = option.substring(1);
            value = (first == '+');
        } else {
            int index = option.indexOf('=');
            if (index == -1) {
                fieldName = option;
                valueString = null;
            } else {
                fieldName = option.substring(0, index);
                valueString = option.substring(index + 1);
            }
        }

        Field f;
        try {
            f = GraalOptions.class.getField(fieldName);

            if (value == null) {
                if (f.getType() == Float.TYPE) {
                    value = Float.parseFloat(valueString);
                } else if (f.getType() == Double.TYPE) {
                    value = Double.parseDouble(valueString);
                } else if (f.getType() == Integer.TYPE) {
                    value = Integer.parseInt(valueString);
                } else if (f.getType() == Boolean.TYPE) {
                    if (valueString == null || valueString.length() == 0) {
                        value = true;
                    } else {
                        value = Boolean.parseBoolean(valueString);
                    }
                } else if (f.getType() == String.class) {
                    value = valueString;
                }
            }
            if (value != null) {
                f.set(null, value);
                //Logger.info("Set option " + fieldName + " to " + value);
            } else {
                Logger.info("Wrong value \"" + valueString + "\" for option " + fieldName);
                return false;
            }
        } catch (SecurityException e) {
            Logger.info("Security exception when setting option " + option);
            return false;
        } catch (NoSuchFieldException e) {
            Logger.info("Could not find option " + fieldName);
            return false;
        } catch (IllegalArgumentException e) {
            Logger.info("Illegal value for option " + option);
            return false;
        } catch (IllegalAccessException e) {
            Logger.info("Illegal access exception when setting option " + option);
            return false;
        }

        return true;
    }
}
