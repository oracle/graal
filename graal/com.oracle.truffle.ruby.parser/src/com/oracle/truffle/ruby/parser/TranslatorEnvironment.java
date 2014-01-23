/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.parser;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.nodes.methods.locals.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.methods.*;

public class TranslatorEnvironment {

    private final RubyContext context;

    private final FrameDescriptor frameDescriptor;

    private final List<FrameSlot> preParameters = new ArrayList<>();

    private final List<FrameSlot> optionalParameters = new ArrayList<>();
    private final Map<FrameSlot, RubyNode> optionalParametersDefaultValues = new HashMap<>();

    private final List<FrameSlot> postParameters = new ArrayList<>();

    private final List<FrameSlot> flipFlopStates = new ArrayList<>();

    private FrameSlot restParameter = null;

    private FrameSlot blockParameter = null;

    private JRubyParser parser;
    private final long returnID;

    private final boolean ownScopeForAssignments;
    private final boolean neverAssignInParentScope;

    protected final TranslatorEnvironment parent;
    private String methodName = "";
    private boolean needsDeclarationFrame = false;
    private UniqueMethodIdentifier methodIdentifier;

    private int tempIndex;

    public TranslatorEnvironment(RubyContext context, TranslatorEnvironment parent, FrameDescriptor frameDescriptor, JRubyParser parser, long returnID, boolean ownScopeForAssignments,
                    boolean neverAssignInParentScope, UniqueMethodIdentifier methodIdentifier) {
        this.context = context;
        this.parent = parent;
        this.frameDescriptor = frameDescriptor;
        this.parser = parser;
        this.returnID = returnID;
        this.ownScopeForAssignments = ownScopeForAssignments;
        this.neverAssignInParentScope = neverAssignInParentScope;
        this.methodIdentifier = methodIdentifier;
    }

    public TranslatorEnvironment(RubyContext context, TranslatorEnvironment parent, JRubyParser parser, long returnID, boolean ownScopeForAssignments, boolean neverAssignInParentScope,
                    UniqueMethodIdentifier methodIdentifier) {
        this(context, parent, new FrameDescriptor(RubyFrameTypeConversion.getInstance()), parser, returnID, ownScopeForAssignments, neverAssignInParentScope, methodIdentifier);
    }

    public int getLocalVarCount() {
        return getFrameDescriptor().getSize();
    }

    public TranslatorEnvironment getParent() {
        return parent;
    }

    public List<FrameSlot> getPreParameters() {
        return preParameters;
    }

    public List<FrameSlot> getOptionalParameters() {
        return optionalParameters;
    }

    public Map<FrameSlot, RubyNode> getOptionalParametersDefaultValues() {
        return optionalParametersDefaultValues;
    }

    public List<FrameSlot> getPostParameters() {
        return postParameters;
    }

    public TranslatorEnvironment getParent(int level) {
        assert level >= 0;
        if (level == 0) {
            return this;
        } else {
            return parent.getParent(level - 1);
        }
    }

    public FrameSlot declareVar(String name) {
        return getFrameDescriptor().findOrAddFrameSlot(name);
    }

    public UniqueMethodIdentifier findMethodForLocalVar(String name) {
        TranslatorEnvironment current = this;
        do {
            FrameSlot slot = current.getFrameDescriptor().findFrameSlot(name);
            if (slot != null) {
                return current.methodIdentifier;
            }

            current = current.parent;
        } while (current != null);

        return null;
    }

    public RubyNode findLocalVarNode(String name, SourceSection sourceSection) {
        TranslatorEnvironment current = this;
        int level = -1;
        try {
            do {
                level++;
                FrameSlot slot = current.getFrameDescriptor().findFrameSlot(name);
                if (slot != null) {
                    if (level == 0) {
                        return ReadLocalVariableNodeFactory.create(context, sourceSection, slot);
                    } else {
                        return ReadLevelVariableNodeFactory.create(context, sourceSection, slot, level);
                    }
                }

                current = current.parent;
            } while (current != null);
        } finally {
            if (current != null) {
                current = this;
                while (level-- > 0) {
                    current.needsDeclarationFrame = true;
                    current = current.parent;
                }
            }
        }

        return null;
    }

    public void setRestParameter(FrameSlot restParameter) {
        this.restParameter = restParameter;
    }

    public FrameSlot getRestParameter() {
        return restParameter;
    }

    public void setBlockParameter(FrameSlot blockParameter) {
        this.blockParameter = blockParameter;
    }

    public FrameSlot getBlockParameter() {
        return blockParameter;
    }

    public void declareFunction(String name) {
        declareVar(name);
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public void setNeedsDeclarationFrame() {
        needsDeclarationFrame = true;
    }

    public boolean needsDeclarationFrame() {
        return needsDeclarationFrame;
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    public String allocateLocalTemp() {
        final String name = "rubytruffle_temp" + tempIndex;
        tempIndex++;
        declareVar(name);
        return name;
    }

    public long getReturnID() {
        return returnID;
    }

    public JRubyParser getParser() {
        return parser;
    }

    public boolean hasOwnScopeForAssignments() {
        return ownScopeForAssignments;
    }

    public boolean getNeverAssignInParentScope() {
        return neverAssignInParentScope;
    }

    public void addMethodDeclarationSlots() {
        frameDescriptor.addFrameSlot(RubyModule.VISIBILITY_FRAME_SLOT_ID);
        frameDescriptor.addFrameSlot(RubyModule.MODULE_FUNCTION_FLAG_FRAME_SLOT_ID);
    }

    public UniqueMethodIdentifier getUniqueMethodIdentifier() {
        return methodIdentifier;
    }

    public List<FrameSlot> getFlipFlopStates() {
        return flipFlopStates;
    }

    public RubyNodeInstrumenter getNodeInstrumenter() {
        return parser.getNodeInstrumenter();
    }
}
