/*
 * Copyright (c) 2011 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */

package com.sun.hotspot.c1x;

import java.lang.reflect.*;

import com.sun.c1x.*;
import com.sun.hotspot.c1x.logging.*;

public class HotSpotOptions {

    public static void setDefaultOptions() {
        C1XOptions.setOptimizationLevel(3);
        C1XOptions.OptInlineExcept = false;
        C1XOptions.OptInlineSynchronized = false;
        C1XOptions.DetailedAsserts = false;
        C1XOptions.CommentedAssembly = false;
        C1XOptions.MethodEndBreakpointGuards = 2;
    }

    public static boolean setOption(String option) {
        if (option.length() == 0) {
            return false;
        }

        Object value = null;
        String fieldName = null;
        String valueString = null;

        System.out.println(option);

        char first = option.charAt(0);
        if (first == '+' || first == '-') {
            fieldName = option.substring(1);
            value = (first == '+');
        } else {
            int index = option.indexOf('=');
            if (index == -1) {
                return false;
            }
            fieldName = option.substring(0, index);
            valueString = option.substring(index + 1);
        }

        Field f;
        try {
            f = C1XOptions.class.getField(fieldName);

            if (value == null) {
                if (f.getType() == Float.TYPE) {
                    value = Float.parseFloat(valueString);
                } else if (f.getType() == Double.TYPE) {
                    value = Double.parseDouble(valueString);
                } else if (f.getType() == Integer.TYPE) {
                    value = Integer.parseInt(valueString);
                } else if (f.getType() == Boolean.TYPE) {
                    value = Boolean.parseBoolean(valueString);
                } else if (f.getType() == String.class) {
                    value = valueString;
                }
            }
            if (value != null) {
                f.set(null, value);
                Logger.info("Set option " + fieldName + " to " + value);
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
