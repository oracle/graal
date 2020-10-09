/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package org.graalvm.component.installer;

/**
 *
 * @author sdedic
 */
public interface Config {
    Config enableStacktraces();

    public boolean isAutoYesEnabled();
    public void setAutoYesEnabled(boolean autoYesEnabled);
    public boolean isNonInteractive();
    public void setNonInteractive(boolean nonInteractive);
    public void setAllOutputToErr(boolean allOutputToErr);
    public void setFileIterable(ComponentIterable fileIterable);
    public void setCatalogFactory(CommandInput.CatalogFactory catalogFactory);
}
