/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as path from 'path';
import { getJavaHome } from "./utils";

const MICRONAUT: string = 'Micronaut';

export async function build(goal?: string) {
    if (!goal) {
        const goals = await getAvailableGoals();
        const selected = goals.length > 1 ? await vscode.window.showQuickPick(goals, { placeHolder: 'Select build goal to invoke' }) : goals.length === 1 ? goals[0] : undefined;
        if (selected) {
            goal = selected.label;
        } else {
            goal = 'build';
        }
    }
    const command = await terminalCommandFor(goal);
    if (command) {
        let terminal: vscode.Terminal | undefined = vscode.window.terminals.find(terminal => terminal.name === MICRONAUT);
        if (!terminal) {
            terminal = vscode.window.createTerminal({ name: MICRONAUT, env: { JAVA_HOME: getJavaHome() }});
        }
        terminal.show();
        terminal.sendText(command);
    } else {
        throw new Error(`No terminal command for ${goal}`);
    }
}

async function buildWrapper<T>(gradle?: (wrapper: vscode.Uri, ...args: any[]) => T, maven?: (wrapper: vscode.Uri, ...args: any[]) => T, ...args: any[]): Promise<T | undefined> {
    let wrapper: vscode.Uri[] = await vscode.workspace.findFiles(process.platform === 'win32' ? '**/gradlew.bat' : '**/gradlew', '**/node_modules/**');
    if (gradle && wrapper && wrapper.length > 0) {
        return gradle(wrapper[0], ...args);
    }
    wrapper = await vscode.workspace.findFiles(process.platform === 'win32' ? '**/mvnw.bat' : '**/mvnw', '**/node_modules/**');
    if (maven && wrapper && wrapper.length > 0) {
        return maven(wrapper[0], ...args);
    }
    return undefined;
}

async function terminalCommandFor(goal: string): Promise<string | undefined> {
    return buildWrapper(terminalGradleCommandFor, terminalMavenCommandFor, goal);
}

function terminalGradleCommandFor(wrapper: vscode.Uri, goal: string): string | undefined {
    const exec = wrapper.fsPath.replace(/(\s+)/g, '\\$1');
    if (exec) {
        return `${exec} ${goal}`;
    }
    return undefined;
}

function terminalMavenCommandFor(wrapper: vscode.Uri, goal: string): string | undefined {
    const exec = wrapper.fsPath.replace(/(\s+)/g, '\\$1');
    if (exec) {
        let command;
        switch(goal) {
            case 'build':
                command = 'compile';
                break;
            case 'nativeImage':
                command = 'mn:nativeImage';
                break;
            default:
                command = goal;
                break;
        }
        if (command) {
            return `${exec} ${command}`;
        }
    }
    return undefined;
}

async function getAvailableGoals(): Promise<vscode.QuickPickItem[]> {
    return await buildWrapper(getAvailableGradleGoals, getAvailableMavenGoals) || [];
}

function getAvailableGradleGoals(wrapper: vscode.Uri): vscode.QuickPickItem[] {
    const goals: vscode.QuickPickItem[] = [];
    const out = cp.execFileSync(wrapper.fsPath, ['tasks', '--group=build', `--project-dir=${path.dirname(wrapper.fsPath)}`]);
    let process: boolean = false;
    out.toString().split('\n').forEach(line => {
        if (process) {
            if (line.length === 0) {
                process = false;
            }
            if (!line.startsWith('---')) {
                const info: string[] | null = line.match(/(\S+)\s*-\s*(.*)/);
                if (info && info.length >= 3) {
                    goals.push({ label: info[1], detail: info[2] });
                }
            }
        } else {
            if (line === 'Build tasks') {
                process = true;
            }
        }
    });
    return goals;
}

function getAvailableMavenGoals(): vscode.QuickPickItem[] {
    const goals: vscode.QuickPickItem[] = [
        { label: 'clean', detail: 'Cleans the project' },
        { label: 'compile', detail: 'Compiles the source code of the project' },
        { label: 'package', detail: 'Packages the compiled code it in its distributable format' }
    ];
    return goals;
}