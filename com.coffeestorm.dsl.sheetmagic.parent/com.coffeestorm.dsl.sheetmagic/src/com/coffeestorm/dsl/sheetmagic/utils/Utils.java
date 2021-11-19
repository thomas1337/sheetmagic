package com.coffeestorm.dsl.sheetmagic.utils;

public class Utils {
	
	private final static String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
	
	public static int convertToIntegerIndex(String letterOrNumId) {
		if (letterOrNumId == null) {
			throw new IllegalArgumentException("No ID provided (null)");
		}
		if (letterOrNumId.matches("\\d+")) {
			// positive integer
			return Integer.parseInt(letterOrNumId);
		}
		if (letterOrNumId.matches("[a-zA-Z]+")) {
			// letter column id
			int idxAsNum = convertColumnLetterID2IntegerIndex(letterOrNumId);
			if (idxAsNum == 0) {
				throw new IllegalArgumentException("Couldn't convert column ID letters to integer.");
			}
			return idxAsNum;
		}
		throw new IllegalArgumentException("Couldn't convert column ID to integer.");
	}
	
	public static int convertColumnLetterID2IntegerIndex(String columnLetterId) {
		// work only with lower case chars, uppercase are handled same as lowercase
		columnLetterId = columnLetterId.toLowerCase();
		
		char[] ch  = columnLetterId.trim().toCharArray();
		int integer_idx = 0;
		for (int i = ch.length - 1; i >= 0; i--) {
			char c = ch[i];
			if (ALPHABET.indexOf(c) == -1) {
				throw new IllegalArgumentException("Couldn't convert column ID letters to integer.");
			}
			if (i == ch.length - 1) {
				integer_idx += ALPHABET.indexOf(c) + 1; // google sheets starts to count from 1 (='A') for column/row indexes
			} else {
				integer_idx +=  Math.pow(ALPHABET.length(), (ch.length - 1 - i)) * (ALPHABET.indexOf(c) + 1);
			}
		}
		return integer_idx;
	}

}
