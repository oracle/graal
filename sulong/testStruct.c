#include <stdio.h>
#include <stdlib.h>

typedef struct {
	int a;
	struct List* next;
} List;

List* createNode(int a) {
	List* l = (List*) malloc(sizeof(List*));
	l->a=a;
	return l;
}

void freeList(List* l) {
	if(l->next!=NULL) freeList(l->next);
	free(l);
}

void push(List** list, int a) {
	List* newList = createNode(a);
	newList->next = *list;
	*list = newList;
}

int pop(List** list) {
	if((*list)!=NULL) {
		int r = (*list)->a;
		*list = (*list)->next;
		return r;
	}
	return 0;
}

int isEmpty(List* list) {
	return list==NULL;
}

void printList(List* list) {
	while(list!=NULL) {
		int s=list->a;
		printf("%c",s);
		list=list->next;
	}
}

void reverseList(List** list) {
	List* oldList = *list;
	if(isEmpty(oldList)) return;
	List* newList = oldList;
	oldList = oldList->next;
	newList->next = NULL;
	while(oldList!=NULL) {
		List* cur = oldList;
		oldList = oldList->next;
		cur->next = newList;
		newList = cur;	
	}
	*list = newList;
}

int main(int argc, char* argv[]) {
	if(argc<=1) {
		printf("No arguments...");
		return 0;
	}
	int a=0x20;
	int idx=0;
	List* list = createNode(a);
	while(a>0) {
		push(&list, a);
		a=*(((char*)argv[1])+idx);
		idx++;
	}
	reverseList(&list);
	printList(list);
	reverseList(&list);
	printList(list);
	freeList(list);
	return 0;
}


