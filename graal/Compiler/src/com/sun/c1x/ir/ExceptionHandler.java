/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import java.util.*;

import com.sun.c1x.lir.*;
import com.sun.cri.ri.*;

/**
 * The {@code ExceptionHandler} class represents an exception handler for a Java bytecode method.
 * There is exactly one instance of this class for every exception handler without any specific
 * reference to an exception-throwing instruction covered by the handler. Then there is one
 * instance per exception-throwing instruction that is used to record the frame state before
 * execution of the instruction. The latter is used to generate exception adapter blocks
 * (see section 3.4 of the paper <a href="http://www.usenix.org/events/vee05/full_papers/p132-wimmer.pdf">
 * Optimized Interval Splitting in a Linear Scan Register Allocator</a>) where necessary.
 *
 * @author Ben L. Titzer
 */
public final class ExceptionHandler {

    public static final List<ExceptionHandler> ZERO_HANDLERS = Collections.emptyList();

    public final RiExceptionHandler handler;
    private BlockBegin entryBlock;
    private LIRList entryCode;
    private int entryCodeOffset;
    private int phiOperand;
    private int scopeCount;
    private int lirOpId;

    public ExceptionHandler(RiExceptionHandler handler) {
        this.handler = handler;
        this.entryCodeOffset = -1;
        this.phiOperand = -1;
        this.scopeCount = -1;
        this.lirOpId = -1;
    }

    public ExceptionHandler(ExceptionHandler other) {
        this.handler = other.handler;
        this.entryBlock = other.entryBlock;
        this.entryCode = other.entryCode;
        this.entryCodeOffset = other.entryCodeOffset;
        this.phiOperand = other.phiOperand;
        this.scopeCount = other.scopeCount;
        this.lirOpId = other.lirOpId;
    }

    @Override
    public String toString() {
        return "XHandler(Block=" + entryBlock.blockID + ") " + handler;
    }

    /**
     * Gets the compiler interface object that describes this exception handler,
     * including the bytecode ranges.
     * @return the compiler interface exception handler
     */
    public RiExceptionHandler handler() {
        return handler;
    }

    /**
     * Gets the bytecode index of the handler (catch block).
     * @return the bytecode index of the handler
     */
    public int handlerBCI() {
        return handler.handlerBCI();
    }

    /**
     * Utility method to check if this exception handler covers the specified bytecode index.
     * @param bci the bytecode index to check
     * @return {@code true} if this exception handler covers the specified bytecode
     */
    public boolean covers(int bci) {
        return handler.startBCI() <= bci && bci < handler.endBCI();
    }

    /**
     * Gets the entry block for this exception handler.
     * @return the entry block
     */
    public BlockBegin entryBlock() {
        return entryBlock;
    }

    /**
     * Gets the PC offset of the handler entrypoint, which is used by
     * the runtime to forward exception points to their catch sites.
     * @return the pc offset of the handler entrypoint
     */
    public int entryCodeOffset() {
        return entryCodeOffset;
    }

    public int phiOperand() {
        return phiOperand;
    }

    public int scopeCount() {
        return scopeCount;
    }

    public void setEntryBlock(BlockBegin entry) {
        entryBlock = entry;
    }

    public void setEntryCodeOffset(int pco) {
        entryCodeOffset = pco;
    }

    public void setPhiOperand(int phi) {
        phiOperand = phi;
    }

    public void setScopeCount(int count) {
        scopeCount = count;
    }

    public boolean isCatchAll() {
        return handler.catchTypeCPI() == 0;
    }

    public static boolean couldCatch(List<ExceptionHandler> exceptionHandlers, RiType klass, boolean typeIsExact) {
        // the type is unknown so be conservative
        if (!klass.isResolved()) {
            return true;
        }

        for (int i = 0; i < exceptionHandlers.size(); i++) {
            ExceptionHandler handler = exceptionHandlers.get(i);
            if (handler.isCatchAll()) {
                // catch of ANY
                return true;
            }
            RiType handlerKlass = handler.handler.catchType();
            // if it's unknown it might be catchable
            if (!handlerKlass.isResolved()) {
                return true;
            }
            // if the throw type is definitely a subtype of the catch type
            // then it can be caught.
            if (klass.isSubtypeOf(handlerKlass)) {
                return true;
            }
            if (!typeIsExact) {
                // If the type isn't exactly known then it can also be caught by
                // catch statements where the inexact type is a subtype of the
                // catch type.
                // given: foo extends bar extends Exception
                // throw bar can be caught by catch foo, catch bar, and catch
                // Exception, however it can't be caught by any handlers without
                // bar in its type hierarchy.
                if (handlerKlass.isSubtypeOf(klass)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int lirOpId() {
        return lirOpId;
    }

    public LIRList entryCode() {
        return entryCode;
    }

    public void setLirOpId(int throwingOpId) {
        lirOpId = throwingOpId;
    }

    public void setEntryCode(LIRList entryCode) {
        this.entryCode = entryCode;

    }
}
