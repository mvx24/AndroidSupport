package com.symbiotic.support;

import android.widget.SimpleCursorAdapter;
import android.widget.SectionIndexer;
import android.database.Cursor;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

public class AlphabetizedSimpleCursorAdapter extends SimpleCursorAdapter implements SectionIndexer
{
	protected final AlphabetNumberIndexer alphaIndexer;
	protected int sortedColumnIndex;
	protected boolean useSectionHeaders;
	
	public AlphabetizedSimpleCursorAdapter(Context context, int layout, Cursor cursor, String[] from, int[] to, int sortedColumnIndex)
	{
		super(context, layout, cursor, from, to);
		this.sortedColumnIndex = sortedColumnIndex;
		this.alphaIndexer = new AlphabetNumberIndexer(cursor, sortedColumnIndex, "#ABCDEFGHIJKLMNOPQRSTUVWXYZ");
		registerDataSetObserver(this.alphaIndexer);
		this.useSectionHeaders = this.isPositionHeader(0);
	}
	
	private String getPositionValue(int position)
	{
		Cursor cursor;
		
		cursor = getCursor();
		cursor.moveToPosition(position);
		return cursor.getString(this.sortedColumnIndex);
	}
	
	private boolean isPositionHeader(int position)
	{
		String str = this.getPositionValue(position);
		return (str.length() == 1);
	}
	
	// Methods for Adapter interface
	
	@Override
	public int getViewTypeCount()
	{
		if(!this.useSectionHeaders)
			return super.getViewTypeCount();
		return super.getViewTypeCount() + 1;
	}
	
	@Override
	public int getItemViewType(int position)
	{
		if(!this.useSectionHeaders || !this.isPositionHeader(position))
			return super.getItemViewType(position);
		return 	this.getViewTypeCount() - 1;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		TextView sectionHeaderView;
		
		if(!this.useSectionHeaders || !this.isPositionHeader(position))
			return super.getView(position, convertView, parent);
			
		if((convertView != null) && (convertView.getTag().equals("AlphabetizedSimpleCursorSectionView")))
		{
			sectionHeaderView = (TextView)convertView;
		}
		else
		{
			sectionHeaderView = new TextView(parent.getContext(), null, android.R.attr.listSeparatorTextViewStyle);
			sectionHeaderView.setTag("AlphabetizedSimpleCursorSectionView");
			sectionHeaderView.setPadding(5, 2, 0, 2);
		}
		sectionHeaderView.setText(this.getPositionValue(position));
		return sectionHeaderView;
	}
	
	// Methods for ListAdapter interface
	
	@Override
	public boolean areAllItemsEnabled()
	{
		if(!this.useSectionHeaders)
			return super.areAllItemsEnabled();
		return false;
	}
	
	@Override
	public boolean isEnabled(int position)
	{
		if(!this.useSectionHeaders)
			return super.isEnabled(position);
		return !this.isPositionHeader(position);
	}
	
	// Methods for SectionIndexer interface
	
	@Override
	public int getPositionForSection(int section) { return this.alphaIndexer.getPositionForSection(section); } 

	@Override
	public int getSectionForPosition(int position) { return this.alphaIndexer.getSectionForPosition(position); } 

	@Override
	public Object[] getSections() { return this.alphaIndexer.getSections(); } 
 
}
