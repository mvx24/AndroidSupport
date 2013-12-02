package com.symbiotic.support;

import android.database.Cursor;
import android.widget.AlphabetIndexer;

public class AlphabetNumberIndexer extends AlphabetIndexer
{
	public AlphabetNumberIndexer(Cursor cursor, int sortedColumnIndex, CharSequence alphabet)
	{
		super(cursor, sortedColumnIndex, alphabet);
	}

	@Override
	protected int compare(String word, String letter)
	{
		if(letter.equals("#"))
		{
			if(Character.isDigit(word.charAt(0)))
				return 0;
			else
				return 1;
		}
		else
		{
			return super.compare(word, letter);
		}
	}
}
