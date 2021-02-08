/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#include <stdlib.h>
#include <stdio.h>

struct node {
    int data;
    struct node *next;
} * head;

int count() {
    struct node *n;
    int c = 0;
    n = head;
    while (n != NULL) {
        n = n->next;
        c++;
    }
    return c;
}

void append(int num) {
    struct node *temp, *right;
    temp = (struct node *) malloc(sizeof(struct node));
    temp->data = num;
    right = (struct node *) head;
    while (right->next != NULL)
        right = right->next;
    right->next = temp;
    right = temp;
    right->next = NULL;
}

void add(int num) {
    struct node *temp;
    temp = (struct node *) malloc(sizeof(struct node));
    temp->data = num;
    if (head == NULL) {
        head = temp;
        head->next = NULL;
    } else {
        temp->next = head;
        head = temp;
    }
}
void addafter(int num, int loc) {
    int i;
    struct node *temp, *left, *right;
    right = head;
    for (i = 1; i < loc; i++) {
        left = right;
        right = right->next;
    }
    temp = (struct node *) malloc(sizeof(struct node));
    temp->data = num;
    left->next = temp;
    left = temp;
    left->next = right;
}

void insert(int num) {
    int c = 0;
    struct node *temp;
    temp = head;
    if (temp == NULL) {
        add(num);
    } else {
        while (temp != NULL) {
            if (temp->data < num)
                c++;
            temp = temp->next;
        }
        if (c == 0)
            add(num);
        else if (c < count())
            addafter(num, ++c);
        else
            append(num);
    }
}

int delete (int num) {
    struct node *temp, *prev;
    temp = head;
    while (temp != NULL) {
        if (temp->data == num) {
            if (temp == head) {
                head = temp->next;
                free(temp);
                return 1;
            } else {
                prev->next = temp->next;
                free(temp);
                return 1;
            }
        } else {
            prev = temp;
            temp = temp->next;
        }
    }
    return 0;
}

void display(struct node *r, FILE *f) {
    char d[] = "%d ";
    char ne[] = " \n";
    r = head;
    if (r == NULL) {
        return;
    }
    while (r != NULL) {
        fprintf(f, d, r->data);
        r = r->next;
    }
    fprintf(f, "%s", ne);
}

int main() {
    FILE *f = fopen("output", "w");
    head = NULL;

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-variable"
    char s[] = "Size of the list is %d\n";
    char d[] = "Deleted? %d \n";
#pragma clang diagnostic pop

    insert(5);
    display(head, f);
    fprintf(f, "%i", count());

    insert(8);
    display(head, f);
    insert(1);
    display(head, f);

    fprintf(f, "%i", delete (2));
    fprintf(f, "%i", delete (8));

    display(head, f);

    fprintf(f, "%i", count());

    insert(8);

    fclose(f);

    return count();
}
