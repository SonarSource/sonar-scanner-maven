package com.example;

import org.apache.commons.text.StrBuilder;
import org.apache.commons.text.WordUtils;

public class Hello {
  public static void main(String[] args) {
    System.out.println("Hello, " + WordUtils.initials("Ben John Lee") + "!");
    StrBuilder sb = new StrBuilder();
    sb.append("Favorite programming language: ");
    sb.append("Perl");
    System.out.println(sb);
  }
}
