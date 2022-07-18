/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.operations.instructions;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData;

public class QuickenedInstruction extends CustomInstruction {

    private final CustomInstruction orig;
    private final List<String> activeSpecNames;
    private final List<SpecializationData> activeSpecs;

    private static String makeName(CustomInstruction orig, List<String> activeSpecNames) {
        StringBuilder sb = new StringBuilder(orig.name);
        sb.append(".q");
        for (String activeSpec : activeSpecNames) {
            sb.append('.');
            sb.append(activeSpec);
        }

        return sb.toString();
    }

    public List<String> getActiveSpecNames() {
        return activeSpecNames;
    }

    public List<SpecializationData> getActiveSpecs() {
        return activeSpecs;
    }

    public CustomInstruction getOrig() {
        return orig;
    }

    @Override
    public String getUniqueName() {
        StringBuilder sb = new StringBuilder(getData().getName());
        sb.append("_q");
        for (String activeSpec : activeSpecNames) {
            sb.append("_");
            sb.append(activeSpec.replaceAll("[^a-zA-Z0-9_]", "_"));
        }
        return sb.toString();
    }

    public QuickenedInstruction(CustomInstruction orig, int id, SingleOperationData data, List<String> activeSpecNames) {
        super(makeName(orig, activeSpecNames), id, data);
        this.orig = orig;
        this.activeSpecNames = activeSpecNames;

        if (activeSpecNames.isEmpty()) {
            data.addWarning("Invalid quickened instruction %s: no specializations defined.", data.getName());
            activeSpecs = new ArrayList<>();
            return;
        }

        activeSpecs = new ArrayList<>(data.getNodeData().getSpecializations());

        // validate specialization names

        boolean hasErrors = false;
        outer: for (String activeSpec : activeSpecNames) {
            for (SpecializationData spec : activeSpecs) {
                if (spec.getId().equals(activeSpec)) {
                    continue outer;
                }
            }

            List<String> realSpecNames = data.getNodeData().getSpecializations().stream().map(x -> x.getId()).collect(Collectors.toUnmodifiableList());
            data.addWarning("Invalid specialization id '%s' for operation %s. Expected one of %s.", activeSpec, data.getName(), realSpecNames);
            hasErrors = true;
        }

        if (hasErrors) {
            return;
        }

        activeSpecs.removeIf(spec -> {
            for (String activeSpec : activeSpecNames) {
                if (spec.getId().equals(activeSpec)) {
                    return false;
                }
            }
            return true;
        });

        orig.addQuickenedVariant(this);
    }

    @Override
    public BoxingEliminationBehaviour boxingEliminationBehaviour() {
        return BoxingEliminationBehaviour.SET_BIT;
    }

    @Override
    public CodeTree boxingEliminationBitOffset() {
        return orig.boxingEliminationBitOffset();
    }

    @Override
    public int boxingEliminationBitMask() {
        return orig.boxingEliminationBitMask();
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return orig.createPrepareAOT(vars, language, root);
    }

    @Override
    public void addQuickenedVariant(QuickenedInstruction quick) {
        throw new AssertionError("should not add quickened variants to quickened instructions");
    }

    @Override
    public boolean neverInUncached() {
        return true;
    }
}
