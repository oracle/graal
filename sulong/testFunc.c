#include <stdio.h>

void capitalize(int* ch) {
	int c = *ch;
	int idx = 0;
	if(c>=(int)'A' && c<=(int)'Z') {
		*ch = (int)(c-'A'+'a');
	} else if(c>='a' && c<='z') {
		*ch = (int)(c-'a'+'A');
	}	
}

int main(int argc, char* argv[]) {
	if(argc<=1) {
		printf("No arguments...");
		return 0;
	}
	int a=65;
	int idx=0;
	while(a>0) {
		capitalize(&a);
		printf("%c|",a);
		a=*(((char*)argv[1])+idx);
	idx++;
	}
	return 0;
}


