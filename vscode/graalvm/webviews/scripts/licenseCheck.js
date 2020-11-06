/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

const vscode = acquireVsCodeApi();
document.addEventListener("DOMContentLoaded", function(event) {
    const emailInput = document.getElementById('email');
    emailInput.addEventListener('input', () => {
        emailInput.setCustomValidity('');
        if (!emailInput.checkValidity()) {
            emailInput.setCustomValidity('Please provide a valid email address');
        }
    });
    const acceptButton = document.getElementById('accept');
    acceptButton.addEventListener('click', () => {
        if (emailInput.reportValidity()) {
            vscode.postMessage({ command: 'accepted', email: emailInput.value });
        }
    });
    const rejectButton = document.getElementById('reject');
    rejectButton.addEventListener('click', () => {
        vscode.postMessage({ command: 'rejected' });
    });
});
