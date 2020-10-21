/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

const vscode = acquireVsCodeApi();
document.addEventListener("DOMContentLoaded", function(event) {
    const acceptButton = document.getElementById('accept');
    acceptButton.addEventListener('click', () => {
        vscode.postMessage({ command: 'accepted', value: true });
    });
    const rejectButton = document.getElementById('reject');
    rejectButton.addEventListener('click', () => {
        vscode.postMessage({ command: 'accepted', value: false });
    });
});
