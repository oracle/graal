/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package org.graalvm.component.installer.model;

/**
 *
 * @author sdedic
 */
public interface RemoteInfoProcessor {
    public ComponentInfo decorateComponent(ComponentInfo info);
    
    public static final RemoteInfoProcessor NONE = new RemoteInfoProcessor() {
        @Override
        public ComponentInfo decorateComponent(ComponentInfo info) {
            return info;
        }
    };
}
