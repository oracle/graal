/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include <stdlib.h>
#include <string.h>
#include "harness.h"

#define TEXT_LENGTH (5000000)
#define PATTERN_LENGTH (512)
#define COPY_COUNT (50)

char source_text[TEXT_LENGTH];
char target_text[TEXT_LENGTH];
char* source_texts[COPY_COUNT];
char* target_texts[COPY_COUNT];
char pattern[PATTERN_LENGTH + 1];

const char* characters =
  "!@#$%^&*()_[]{}~@^\\ qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM1234567890\x7f";

int benchmarkIterationsCount() {
  return 10;
}

void benchmarkSetupOnce() {
}

void benchmarkSetupEach() {
  for (int i = 0; i < TEXT_LENGTH; i++) {
    int index = ((0x40 + i % 0x40 + (i * i) % 0x16) & 0x7e);
    if (index < 0x40) {
      index = 0x40;
    }
    source_text[i] = (char) index;
    target_text[i] = (char) 0;
  }
  source_text[TEXT_LENGTH - 1] = (char) 0x00;
  for (int i = 0; i < COPY_COUNT; i++) {
    source_texts[i] = source_text;
    target_texts[i] = target_text;
  }
  strncpy(pattern, source_text + TEXT_LENGTH / 2, PATTERN_LENGTH);
  pattern[PATTERN_LENGTH] = (char) 0x00;
}

void benchmarkTeardownEach(char* outputFile) {
}

int benchmarkRun() {
  long long hash = 0;

  for (int i = 0; i < COPY_COUNT; i++) {
    strcpy(target_texts[i], source_texts[i]);
  }

  for (int i = 0; i < COPY_COUNT; i++) {
    hash += strlen(target_texts[i]);
  }

  for (int i = 0; i < COPY_COUNT; i++) {
    hash += 10 + strcmp(target_texts[i], source_text);
  }

  for (int i = 0; i < COPY_COUNT; i++) {
    char* ptr = strchr(target_texts[i], 0x40 + i % 0x80);
    if (ptr != NULL) {
      hash += ptr[0];
    }
    ptr = strchr(source_texts[i], 0x40 + i % 0x80);
    if (ptr != NULL) {
      hash += ptr[0];
    }
  }

  for (int i = 0; i < COPY_COUNT; i++) {
    char* ptr = strstr(target_texts[i], pattern);
    if (ptr != NULL) {
      hash += ptr[0];
    }
  }

  for (int i = 0; i < COPY_COUNT; i++) {
    size_t len = strspn(target_texts[i], characters);
    hash += len;
  }

  for (int i = 0; i < COPY_COUNT; i++) {
    hash += 10 + strcasecmp(target_texts[i], source_texts[i]);
  }

  for (int i = 0; i < 200; i++) {
    hash += (int) target_text[i];
  }

  return (int) hash;
}
