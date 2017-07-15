package org.johnnei.sonarqube.bugs;

import java.util.ArrayList;
import java.util.Collection;

public class MyClass {


	public static void main(String... args) {
		Collection<String> myString = new ArrayList<String>();
		myString.add("a");
		myString.add("b");
		myString.add("c");

		StringBuilder message = new StringBuilder("");
		for (String s : myString) {
			message.append(s);
		}

		System.out.println(message.toString());
	}
}
