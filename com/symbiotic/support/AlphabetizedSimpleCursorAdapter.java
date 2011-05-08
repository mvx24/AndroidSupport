package com.symbiotic.support;

import android.widget.SimpleCursorAdapter;
import android.widget.AlphabetIndexer;
import android.widget.SectionIndexer;
import android.database.Cursor;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

public class AlphabetizedSimpleCursorAdapter extends SimpleCursorAdapter implements SectionIndexer
{
	protected final AlphabetIndexer alphaIndexer;
	protected int sortedColumnIndex;
	protected boolean useSectionHeaders;
	
	public AlphabetizedSimpleCursorAdapter(Context context, int layout, Cursor cursor, String[] from, int[] to, int sortedColumnIndex)
	{
		super(context, layout, cursor, from, to);
		this.sortedColumnIndex = sortedColumnIndex;
		alphaIndexer = new AlphabetIndexer(cursor, sortedColumnIndex, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
		registerDataSetObserver(alphaIndexer);
		useSectionHeaders = isPositionHeader(0);
	}
	
	private String getPositionValue(int position)
	{
		Cursor cursor;
		
		cursor = getCursor();
		cursor.moveToPosition(position);
		return cursor.getString(sortedColumnIndex);
	}
	
	private boolean isPositionHeader(int position)
	{
		String str = getPositionValue(position);
		return (str.length() == 1);
	}
	
	// Methods for Adapter interface
	
	@Override
	public int getViewTypeCount()
	{
		if(!useSectionHeaders)
			return super.getViewTypeCount();
		return super.getViewTypeCount() + 1;
	}
	
	@Override
	public int getItemViewType(int position)
	{
		if(!useSectionHeaders || !isPositionHeader(position))
			return super.getItemViewType(position);
		return 	getViewTypeCount() - 1;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		TextView sectionHeaderView;
		
		if(!useSectionHeaders || !isPositionHeader(position))
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
		sectionHeaderView.setText(getPositionValue(position));
		return sectionHeaderView;
	}
	
	// Methods for ListAdapter interface
	
	@Override
	public boolean areAllItemsEnabled()
	{
		if(!useSectionHeaders)
			return super.areAllItemsEnabled();
		return false;
	}
	
	@Override
	public boolean isEnabled(int position)
	{
		if(!useSectionHeaders)
			return super.isEnabled(position);
		return !isPositionHeader(position);
	}
	
	// Methods for SectionIndexer interface
	
	@Override
	public int getPositionForSection(int section) { return alphaIndexer.getPositionForSection(section); } 

	@Override
	public int getSectionForPosition(int position) { return alphaIndexer.getSectionForPosition(position); } 

	@Override
	public Object[] getSections() { return alphaIndexer.getSections(); } 
 
}
