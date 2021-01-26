/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import { micronautProjectExists, getJavaHome } from "./utils";
import { WelcomePanel } from './welcome';
import { creatorInit, createProject } from './projectCreate';
import { builderInit, build } from './projectBuild';

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
	context.subscriptions.push(vscode.commands.registerCommand('extension.micronaut.buildNativeImage', () => {
		vscode.commands.executeCommand('extension.micronaut.build', 'nativeImage');
	}));
	context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(e => {
		if (e.affectsConfiguration('micronaut.home')) {
			creatorInit();
		}
	}));
	creatorInit();
	if (micronautProjectExists()) {
		vscode.commands.executeCommand('setContext', 'micronautProjectExists', true);
		builderInit();
		const javaHome = getJavaHome();
		if (javaHome) {
			vscode.commands.executeCommand('setContext', 'javaHomeSet', true);
		}
	}
}

export function deactivate() {}
