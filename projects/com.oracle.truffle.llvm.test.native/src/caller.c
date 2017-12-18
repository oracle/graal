#include <stdio.h>

char caller_i8(char (*callback)(char), char value)
{
	printf("calling i8 callback\n");
	return callback(value);
}

long caller_i64(long (*callback)(long), long value)
{
	printf("calling i64 callback\n");
	return callback(value);
}

float caller_f32(float (*callback)(float), float value)
{
	printf("calling f32 callback\n");
	return callback(value);
}

double caller_f64(double (*callback)(double), double value)
{
	printf("calling f64 callback\n");
	return callback(value);
}
