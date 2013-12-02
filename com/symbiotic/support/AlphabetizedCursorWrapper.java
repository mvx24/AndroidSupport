package com.symbiotic.support;

import android.widget.SimpleCursorAdapter;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.content.Context;

public class AlphabetizedCursorWrapper extends CursorWrapper
{
	protected final Cursor cursor;
	protected final int sortedColumnIndex;
	protected AlphabetNumberIndexer alphaIndexer;
	protected int position;
	protected boolean isSectionHeader;
	protected int numberSection;
	
	public AlphabetizedCursorWrapper(Cursor cursor, int sortedColumnIndex)
	{
		super(cursor);
		this.cursor = cursor;
		this.sortedColumnIndex = sortedColumnIndex;
		this.position = -1;
		this.isSectionHeader = false;
		this.numberSection = 0;
		this.buildAlphaIndexer();
	}
	
	private void buildAlphaIndexer()
	{
		StringBuilder stringBuilder;
		int section, position, prevPosition;

		this.alphaIndexer = new AlphabetNumberIndexer(this.cursor, this.sortedColumnIndex, "#ABCDEFGHIJKLMNOPQRSTUVWXYZ");
		
		// Build a restricted AlphabetNumberIndexer for sections that exist
		stringBuilder = new StringBuilder();
		prevPosition = this.alphaIndexer.getPositionForSection(0);
		for(section = 1; section < this.alphaIndexer.getSections().length; ++section)
		{
			position = this.alphaIndexer.getPositionForSection(section);
			if(prevPosition != position)
				stringBuilder.append(this.alphaIndexer.getSections()[section - 1]);
			prevPosition = position;
		}
		// Check the last section
		--section;
		if(this.cursor.getCount() != this.alphaIndexer.getPositionForSection(section))
			stringBuilder.append("Z");

		this.alphaIndexer = new AlphabetNumberIndexer(this.cursor, this.sortedColumnIndex, stringBuilder.toString());
	}
	
	private boolean determinePosition()
	{
		int realPosition;
		int section;
		int count;
		
		count = this.getCount();
		
		if(count == 0)
		{
			this.position = -1;
			return false;
		}
		if(this.position == -1)
		{
			this.isSectionHeader = false;
			realPosition = -1;
		}
		else if(this.position == 0)
		{
			this.isSectionHeader = true;
			realPosition = 0;
		}
		else if(this.position == (count - 1))
		{
			this.isSectionHeader = false;
			realPosition = super.getCount() - 1;
		}
		else if(this.position == count)
		{
			this.isSectionHeader = false;
			realPosition = super.getCount();
		}
		else
		{
			// Find the section
			for(section = 0; section < this.alphaIndexer.getSections().length; ++section)
			{
				realPosition = this.alphaIndexer.getPositionForSection(section);
				if((realPosition + section) > this.position)
					break;
			}
			--section;
			
			// Section found, which gives the number of header rows appearing before this position
			realPosition = this.position - section;
			if(realPosition == this.alphaIndexer.getPositionForSection(section))
			{
				this.isSectionHeader = true;
			}
			else
			{
				this.isSectionHeader = false;
				// Account for the section header of the current section
				--realPosition;
			}
		}
		
		return super.moveToPosition(realPosition);
	}
	
	@Override
	public String getString(int columnIndex)
	{
		if(!this.isSectionHeader || (columnIndex != this.sortedColumnIndex))
			return super.getString(columnIndex);
		String sectionHeader = super.getString(columnIndex).substring(0, 1);
		if(Character.isDigit(sectionHeader.charAt(0)))
			return "#";
		else
			return sectionHeader;
	}
	
	@Override
	public int getCount()
	{
		return super.getCount() + this.alphaIndexer.getSections().length;
	}
	
	@Override
	public int getPosition()
	{
		return this.position;
	}
	
	@Override
	public boolean move(int offset)
	{
		this.position += offset;
		if(this.position <= -1)
			this.position = -1;
		else if(this.position >= this.getCount())
			this.position = this.getCount();
		return this.determinePosition();
	}
	
	@Override
	public boolean moveToPosition(int newPosition)
	{
		this.position = newPosition;
		if(this.position <= -1)
			this.position = -1;
		else if(this.position >= this.getCount())
			this.position = this.getCount();
		return this.determinePosition();
	}
	
	@Override
	public boolean moveToFirst()
	{
		this.position = 0;
		return this.determinePosition();
	}
	
	@Override
	public boolean moveToLast()
	{
		this.position = this.getCount() - 1;
		return this.determinePosition();
	}
	
	@Override
	public boolean moveToNext()
	{
		++this.position;
		if(this.position > this.getCount())
			this.position = this.getCount();
		return this.determinePosition();
	}
	
	@Override
	public boolean moveToPrevious()
	{
		--this.position;
		if(this.position < -1)
			this.position = -1;
		return this.determinePosition();
	}

	@Override
	public void close()
	{
		super.close();
		this.alphaIndexer.onInvalidated();
	}
	
	@Override
	public void deactivate()
	{
		super.deactivate();
		this.alphaIndexer.onInvalidated();
	}
	
	@Override
	public boolean requery()
	{
		boolean ret;
		ret = super.requery();
		this.buildAlphaIndexer();
		return ret;
	}

}
