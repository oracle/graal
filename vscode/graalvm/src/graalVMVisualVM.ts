/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import { exec } from 'child_process';
import * as utils from './utils';

const PROVIDE: string = 'Select the process to open in VisualVM';
export async function runVisualVMForPID(pid?: number) {
    const executable = utils.findExecutable('jvisualvm');
    if (!executable) {
        return;
    }
    if (!pid) {
        const picks = await obtainRunningJavaProcesses();
        if (picks) {
            picks.sort((a, b) => a.pid - b.pid);
            pid = await vscode.window.showQuickPick(picks, {
                placeHolder: PROVIDE
            }).then(pick => pick?.pid);
        } else {
            pid = await vscode.window.showInputBox({
                placeHolder: 'PID', 
                validateInput: input => (input && Number.parseInt(input) >= 0) ? undefined : 'PID must be positive integer',
                prompt: PROVIDE
            }).then(str => str ? Number.parseInt(str) : undefined);
        }
    }
    if (!pid) {
        return;
    }
    exec(`"${executable}" --openpid ${pid}`);
}

async function obtainRunningJavaProcesses(graalVMHome?: string): Promise<QuickPickProcess[] | undefined> {
    const executable = utils.findExecutable('jps', graalVMHome);
    if (!executable) {
        return;
    }
    const parts1 = await processCommand(`"${executable}" -l`);
    const parts2 = await processCommand(`"${executable}" -m`);
    const ret: QuickPickProcess[] = [];
    parts1.forEach(p1 => {
        const p2 = parts2.find(p2 => p2.pid === p1.pid);
        if (p2) {
            if (p2.rest) {
                ret.push(new QuickPickProcess(p1.pid, p2.rest, p1.rest));
            } else {
                ret.push(new QuickPickProcess(p1.pid, p1.rest ? p1.rest : '(no process details)'));
            }
        }
    });
    return ret;
}

async function processCommand(cmd: string): Promise<Process[]> {
    return new Promise<Process[]>((resolve, reject) => {
        exec(cmd, async (error: any, stdout: string, _stderr) => {
            if (error) {
                reject(error);
            }
            const lines = stdout.split('\n');
            const parts: Process[] = [];
            lines.forEach(line => {
                const index = line.trim().indexOf(' ');
                if (index >= 0) {
                    parts.push({pid: Number.parseInt(line.slice(0, index)), rest: line.slice(index + 1, line.length)});
                } else {
                    parts.push({pid: Number.parseInt(line)});
                }
            });
            resolve(parts);
        });
    });
}

class QuickPickProcess implements vscode.QuickPickItem{
    label: string;
    picked?: boolean | undefined;
    alwaysShow?: boolean | undefined;
    constructor(
        public readonly pid: number,
        public readonly description: string,
        public readonly detail?: string
    ){
        this.label = pid + '';
    }
}

class Process {
    constructor(
        public readonly pid: number,
        public readonly rest?: string
    ){}
}