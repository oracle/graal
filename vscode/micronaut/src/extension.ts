/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import { micronautProjectExists, getJavaHome, findExecutable } from "./utils";
import { WelcomePanel } from './welcome';
import { createProject } from './projectCreate';
import { build } from './projectBuild';

export function activate(context: vscode.ExtensionContext) {
	if (vscode.workspace.getConfiguration().get<boolean>('micronaut.showWelcomePage')) {
		WelcomePanel.createOrShow(context);
	}
	context.subscriptions.push(vscode.commands.registerCommand('extension.micronaut.showWelcomePage', () => {
		WelcomePanel.createOrShow(context);
	}));
	context.subscriptions.push(vscode.commands.registerCommand('extension.micronaut.createProject', () => {
		createProject();
	}));
	context.subscriptions.push(vscode.commands.registerCommand('extension.micronaut.build', (goal?: string) => {
		build(goal);
	}));
	context.subscriptions.push(vscode.commands.registerCommand('extension.micronaut.buildProject', () => {
		vscode.commands.executeCommand('extension.micronaut.build', 'build');
	}));
	context.subscriptions.push(vscode.commands.registerCommand('extension.micronaut.buildNativeImage', () => {
		vscode.commands.executeCommand('extension.micronaut.build', 'nativeImage');
	}));
	if (micronautProjectExists()) {
		vscode.commands.executeCommand('setContext', 'micronautProjectExists', true);
		const javaHome = getJavaHome();
		if (javaHome) {
			vscode.commands.executeCommand('setContext', 'javaHomeSet', true);
			if (findExecutable('native-image', javaHome)) {
				vscode.commands.executeCommand('setContext', 'graalVMHomeSet', true);
			}
		}
	}
}

export function deactivate() {}
