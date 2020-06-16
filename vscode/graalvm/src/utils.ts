/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as path from 'path';
import * as fs from 'fs';

export function random(low: number, high: number): number {
    return Math.floor(Math.random() * (high - low) + low);
}

export function findExecutable(program: string, graalVMHome: string | undefined): string | undefined {
    if (graalVMHome) {
        let executablePath = path.join(graalVMHome, 'bin', program);
        if (fs.existsSync(executablePath)) {
            return executablePath;
        }
    }
    return undefined;
}

export function isSymlinked(dirPath: string): Promise<boolean> {
    return new Promise((resolve, reject) => {
        fs.lstat(dirPath, (err, stats) => {
            if (err) {
                reject(err);
            }
            if (stats.isSymbolicLink()) {
                resolve(true);
            } else {
                const parent = path.dirname(dirPath);
                if (parent === dirPath) {
                    resolve(false);
                } else {
                    resolve(isSymlinked(parent));
                }
            }
        });
    });
}
