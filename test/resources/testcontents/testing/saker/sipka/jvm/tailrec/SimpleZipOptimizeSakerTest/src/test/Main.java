package test;

public class Main {
	public static void count(int n) {
		if (n == 0) {
			return;
		}
		count(n - 1);
	}
}