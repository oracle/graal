 #include <stdbool.h>

bool nativeInvert(bool value);

int main() {
	if (nativeInvert(false)) {
		return 0;
	} else {
		return 1;
	}
}

