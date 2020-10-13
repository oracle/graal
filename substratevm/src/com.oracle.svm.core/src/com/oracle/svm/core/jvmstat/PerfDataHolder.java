/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.oracle.svm.core.jvmstat;

public interface PerfDataHolder {
    void allocate();

    void update();
}
