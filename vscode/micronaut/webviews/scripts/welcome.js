/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

const vscode = acquireVsCodeApi();
document.addEventListener("DOMContentLoaded", function(event) {
    const checkbox = document.getElementById('showWelcomePage');
    checkbox.addEventListener('click', () => {
        vscode.postMessage({ command: 'showWelcomePage', value: checkbox.checked });
    });
});
