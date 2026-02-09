/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.dataflow.options;

/**
 * Validates if the option given as string is parsable as integer.
 * Optinally min and max value can be given.
 *
 * @author Stefan Loidl
 */
public class IntStringValidator implements Validator{

    private int min, max;
    String error=null;

    /**
     * Creates a new instance of IntValidator with min and max bound
     * for the option.
     */
    public IntStringValidator(int min, int max) {
        this.min=min;
        this.max=max;
    }

    /**
     * Creates a new instance of the validator without bounds.
     */
    public IntStringValidator(){
        this(Integer.MIN_VALUE,Integer.MAX_VALUE);
    }

    public boolean validate(Object option) {
        try{
            if(!(option instanceof String)){
                error="Option is not a string";
                return false;
            }
            int x=Integer.parseInt((String)option);
            if(x>=min && x <=max) {
                error=null;
                return true;
            }
            error="Value not within intervall: ["+min+","+max+"]";
            return false;
        }catch(Exception e){
            error="No integer value.";
            return false;
        }
    }

    public String getLastErrorMessage() {
        return error;
    }

}
