package com.symbiotic.support;

import android.widget.AlphabetIndexer;
import android.widget.SimpleCursorAdapter;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.content.Context;

public class AlphabetizedCursorWrapper extends CursorWrapper
{
	protected final Cursor cursor;
	protected final int sortedColumnIndex;
	protected AlphabetIndexer alphaIndexer;
	protected int position;
	protected boolean isSectionHeader;
	
	public AlphabetizedCursorWrapper(Cursor cursor, int sortedColumnIndex)
	{
		super(cursor);
		this.cursor = cursor;
		this.sortedColumnIndex = sortedColumnIndex;
		position = -1;
		isSectionHeader = false;
		buildAlphaIndexer();
	}
	
	private void buildAlphaIndexer()
	{
		StringBuilder stringBuilder;
		int section, position, prevPosition;
		
		alphaIndexer = new AlphabetIndexer(cursor, sortedColumnIndex, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
		
		// Build a restricted AlphabetIndexer for sections that exist
		stringBuilder = new StringBuilder();
		prevPosition = alphaIndexer.getPositionForSection(0);
		for(section = 1; section < alphaIndexer.getSections().length; ++section)
		{
			position = alphaIndexer.getPositionForSection(section);
			if(prevPosition != position)
				stringBuilder.append((char)('A' + (section - 1)));
			prevPosition = position;
		}
		// Check the last section
		--section;
		if(cursor.getCount() != alphaIndexer.getPositionForSection(section))
			stringBuilder.append((char)('A' + section));

		alphaIndexer = new AlphabetIndexer(cursor, sortedColumnIndex, stringBuilder.toString());
	}
	
	private boolean determinePosition()
	{
		int realPosition;
		int section;
		int count;
		
		count = getCount();
		
		if(count == 0)
		{
			position = -1;
			return false;
		}
		if(position == -1)
		{
			isSectionHeader = false;
			realPosition = -1;
		}
		else if(position == 0)
		{
			isSectionHeader = true;
			realPosition = 0;
		}
		else if(position == (count - 1))
		{
			isSectionHeader = false;
			realPosition = super.getCount() - 1;
		}
		else if(position == count)
		{
			isSectionHeader = false;
			realPosition = super.getCount();
		}
		else
		{
			// find the section
			for(section = 0; section < alphaIndexer.getSections().length; ++section)
			{
				realPosition = alphaIndexer.getPositionForSection(section);
				if((realPosition + section) > position)
					break;
			}
			--section;
			
			// section found, which gives the number of header rows appearing before this position
			realPosition = position - section;
			if(realPosition == alphaIndexer.getPositionForSection(section))
			{
				isSectionHeader = true;
			}
			else
			{
				isSectionHeader = false;
				// account for the section header of the current section
				--realPosition;
			}
		}
		
		return super.moveToPosition(realPosition);
	}
	
	@Override
	public String getString(int columnIndex)
	{
		if(!isSectionHeader || (columnIndex != sortedColumnIndex))
			return super.getString(columnIndex);
		return super.getString(columnIndex).substring(0, 1);
	}
	
	@Override
	public int getCount()
	{
		return super.getCount() + alphaIndexer.getSections().length;
	}
	
	@Override
	public int getPosition()
	{
		return position;
	}
	
	@Override
	public boolean move(int offset)
	{
		position += offset;
		if(position <= -1)
			position = -1;
		else if(position >= getCount())
			position = getCount();
		return determinePosition();
	}
	
	@Override
	public boolean moveToPosition(int newPosition)
	{
		position = newPosition;
		if(position <= -1)
			position = -1;
		else if(position >= getCount())
			position = getCount();
		return determinePosition();
	}
	
	@Override
	public boolean moveToFirst()
	{
		position = 0;
		return determinePosition();
	}
	
	@Override
	public boolean moveToLast()
	{
		position = getCount() - 1;
		return determinePosition();
	}
	
	@Override
	public boolean moveToNext()
	{
		++position;
		if(position > getCount())
			position = getCount();
		return determinePosition();
	}
	
	@Override
	public boolean moveToPrevious()
	{
		--position;
		if(position < -1)
			position = -1;
		return determinePosition();
	}

	@Override
	public void close()
	{
		super.close();
		alphaIndexer.onInvalidated();
	}
	
	@Override
	public void deactivate()
	{
		super.deactivate();
		alphaIndexer.onInvalidated();
	}
	
	@Override
	public boolean requery()
	{
		boolean ret;
		ret = super.requery();
		buildAlphaIndexer();
		return ret;
	}

}
