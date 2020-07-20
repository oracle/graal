/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import { pathToFileURL } from 'url';

const coverageOn = new Set<string>();
let coveredDecoration: vscode.TextEditorDecorationType;
let uncoveredDecoration: vscode.TextEditorDecorationType;

export async function toggleCodeCoverage(context: vscode.ExtensionContext) {
    const editor = vscode.window.activeTextEditor;
    if (editor) {
        if (!uncoveredDecoration) {
            uncoveredDecoration = vscode.window.createTextEditorDecorationType({
                dark: {
                    gutterIconPath: context.asAbsolutePath('images/gutter-dark-red.svg'),
                    gutterIconSize: 'contain',
                    overviewRulerColor: '#630417',
                },
                light: {
                    gutterIconPath: context.asAbsolutePath('images/gutter-light-red.svg'),
                    gutterIconSize: 'contain',
                    overviewRulerColor: '#f29494',
                },
                overviewRulerLane: vscode.OverviewRulerLane.Right,
            });
        }
        if (!coveredDecoration) {
            coveredDecoration = vscode.window.createTextEditorDecorationType({
                dark: {
                    gutterIconPath: context.asAbsolutePath('images/gutter-dark-green.svg'),
                    gutterIconSize: 'contain',
                    overviewRulerColor: '#144014',
                },
                light: {
                    gutterIconPath: context.asAbsolutePath('images/gutter-light-green.svg'),
                    gutterIconSize: 'contain',
                    overviewRulerColor: '#a6e3a6',
                },
                overviewRulerLane: vscode.OverviewRulerLane.Right,
            });
        }
        const id = (editor as any).id;
        if (coverageOn.has(id)) {
            coverageOn.delete(id);
            editor.setDecorations(coveredDecoration, []);
            editor.setDecorations(uncoveredDecoration, []);
        } else {
            if (await showCoverage(editor)) {
                coverageOn.add(id);
            } else {
                vscode.window.setStatusBarMessage('No coverage data available.', 3000);
            }
        }
    }
}

export async function activeTextEditorChaged(editor: vscode.TextEditor) {
    const id = (editor as any).id;
    if (coverageOn.has(id)) {
        if (await showCoverage(editor)) {
            coverageOn.add(id);
        } else {
            coverageOn.delete(id);
            vscode.window.setStatusBarMessage('No coverage data available.', 3000);
        }
    }
}

async function showCoverage(editor: vscode.TextEditor): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
        vscode.commands.getCommands().then((commands: string[]) => {
            if (commands.includes('get_coverage')) {
                vscode.commands.executeCommand('get_coverage', pathToFileURL(editor.document.uri.fsPath)).then((value) => {
                    if (value) {
                        const coverage = value as {covered: vscode.Range[], uncovered: vscode.Range[]};
                        editor.setDecorations(coveredDecoration, coverage.covered);
                        editor.setDecorations(uncoveredDecoration, coverage.uncovered);
                        resolve(true);
                    } else {
                        resolve(false);
                    }
                });
            } else {
                resolve(false);
            }
        });
    });
}