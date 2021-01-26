/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as path from 'path';
import { getJavaHome, findExecutable } from "./utils";

const MICRONAUT: string = 'Micronaut';
const NATIVE_IMAGE: string = 'native-image';
let goals: vscode.QuickPickItem[] = [];

export async function builderInit() {
    goals = await buildWrapper(getAvailableGradleGoals, getAvailableMavenGoals) || [];
}

export async function build(goal?: string) {
    if (!goal) {
        if (goals.length === 0) {
            goal = 'build';
        } else {
            const selected = goals.length > 1 ? await vscode.window.showQuickPick(goals, { placeHolder: 'Select build goal to invoke' }) : goals.length === 1 ? goals[0] : undefined;
            if (selected) {
                goal = selected.label;
            }
        }
    }
    if (goal) {
        const javaHome = getJavaHome();
        if (javaHome && (goal === 'nativeImage' || goal === 'dockerBuildNative')) {
            const nativeImage = findExecutable(NATIVE_IMAGE, javaHome);
            if (!nativeImage) {
                const gu = findExecutable('gu', javaHome);
                if (gu) {
                    const selected = await vscode.window.showInformationMessage(`${NATIVE_IMAGE} is not installed in your GraalVM`, `Install ${NATIVE_IMAGE}`);
                    if (selected === `Install ${NATIVE_IMAGE}`) {
                        await vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', NATIVE_IMAGE, javaHome);
                        return;
                    }
                } else {
                    vscode.window.showWarningMessage(`native-image is missing in ${javaHome}`);
                }
            }
        }
        const command = await terminalCommandFor(goal);
        if (command) {
            let terminal: vscode.Terminal | undefined = vscode.window.terminals.find(terminal => terminal.name === MICRONAUT);
            if (!terminal) {
                const env: any = {};
                if (javaHome) {
                    env.JAVA_HOME = javaHome;
                    env.PATH = `${path.join(javaHome, 'bin')}${path.delimiter}${process.env.PATH}`;
                }
                terminal = vscode.window.createTerminal({ name: MICRONAUT, env });
            }
            terminal.show();
            terminal.sendText(command);
        } else {
            throw new Error(`No terminal command for ${goal}`);
        }
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
        return `${exec} ${goal} --no-daemon`;
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
                command = 'package -Dpackaging=native-image';
                break;
            case 'dockerBuild':
                command = 'package -Dpackaging=docker';
                break;
            case 'dockerBuildNative':
                command = 'package -Dpackaging=docker-native';
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

function getAvailableGradleGoals(wrapper: vscode.Uri): vscode.QuickPickItem[] {
    const goals: vscode.QuickPickItem[] = [];
    const out = cp.execFileSync(wrapper.fsPath, ['tasks', '--no-daemon', '--group=build', `--project-dir=${path.dirname(wrapper.fsPath)}`]);
    let process: boolean = false;
    out.toString().split('\n').map(line => line.trim()).forEach(line => {
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
        { label: 'package', detail: 'Packages the compiled code in its distributable format' },
        { label: 'nativeImage', detail: 'Packages the compiled code as a GraalVM native image'},
        { label: 'dockerBuild', detail: 'Builds a Docker image with the application artifacts'},
        { label: 'dockerBuildNative', detail: 'Builds a Docker image with a GraalVM native image inside'}
    ];
    return goals;
}
