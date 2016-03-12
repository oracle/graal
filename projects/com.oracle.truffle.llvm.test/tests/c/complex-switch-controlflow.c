int test(int a) {
	int i;
	int sum = 0;
	for (i = 0; i < a; i++) {
		int j = 0;
		int x;
		switch (i % 5) {
			case 0:
				switch (a % 3) {
					case 0:
						j += 3;
						break;
					case 1:
						j = 7;
						x = 4;
						goto asdf;
					case 2:
						sum -= 11;
						j++;
					default:
						j -= 3;
				}
			case 1:
				j = 3;
				x = -2;
				goto asdf;
			case 2:
				if (j + i % 2 == 0) {
					x = 12;
				} else {
					x = 2;
					switch (i % 3) {
						case 0: x = 21;
						case 1: x += 4;
						case 2: goto asdf;
					}
				}
				break;
			case 3:
				x = 43;
				if (x - i) {
					x++;
					break;
				}
			case 4:
				j++;
				break;
			default:
				sum = 0;
		}
		sum += j;
		asdf:
		sum += 2 * j + x;
	}
	return sum;
}

int main() {
	int i;
	int sum = 0;
	for (i = 0; i < 10000; i++) {
		sum += test(i);
	}
	return sum == 1114580352;
}
