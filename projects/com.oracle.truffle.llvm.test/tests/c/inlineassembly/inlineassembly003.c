int main() {
	int arg1 = 0;
	int not = 0;
	__asm__ ( "notl %%eax;"
        : "=a" (not)
        : "a" (arg1) );
	return not;
}
