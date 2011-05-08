package com.symbiotic.support;

import android.widget.Adapter;
import android.widget.ListAdapter;
import android.widget.BaseAdapter;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.LinkedHashMap;

// Original source code taken from:
// http://whyandroid.com/android/180-separating-lists-with-headers-in-android-09.html

public class SeparatedListAdapter extends BaseAdapter
{
	private final LinkedHashMap<String, ListAdapter> sections;
	
	public SeparatedListAdapter(Context context)
	{
		sections = new LinkedHashMap<String, ListAdapter>();
	}
	
	public void addSection(String section, ListAdapter adapter)
	{
		sections.put(section, adapter);
	}
	
	@Override
	public Object getItem(int position)
	{
		ListAdapter adapter;
		int size;
		
		for(Object section : this.sections.keySet())
		{
			adapter = sections.get(section);
			size = adapter.getCount() + 1;

			// check if position inside this section
			if(position == 0)
				return section;
			else if(position < size)
				return adapter.getItem(position - 1);

			// otherwise jump into next section
			position -= size;
		}
		return null;
	}
	
	@Override
	public int getCount()
	{
		int total = 0;
	
		// total together all sections, plus one for each section header
		for(ListAdapter adapter : this.sections.values())
			total += adapter.getCount() + 1;
		return total;
	}
	
	@Override	
	public int getViewTypeCount()
	{
		int total = 1;
		
		// assume that headers count as one, then total all sections
		for(ListAdapter adapter : this.sections.values())
			total += adapter.getViewTypeCount();
		return total;
	}
	
	@Override
	public int getItemViewType(int position)
	{
		ListAdapter adapter;
		int size;
		int type = 0;
		
		for(Object section : this.sections.keySet())
		{
			adapter = sections.get(section);
			size = adapter.getCount() + 1;
			
			// check if position inside this section
			if(position == 0)
				return (getViewTypeCount() - 1);	// the section header
			else if(position < size)
				return type + adapter.getItemViewType(position - 1);

			// otherwise jump into next section
			position -= size;
			type += adapter.getViewTypeCount();
		}
		return Adapter.IGNORE_ITEM_VIEW_TYPE;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		TextView sectionHeaderView;
		ListAdapter adapter;
		int size;
		int sectionnum = 0;
		
		for(Object section : this.sections.keySet())
		{
			adapter = sections.get(section);
			size = adapter.getCount() + 1;
			
			// check if position inside this section
			if(position == 0)
			{
				if((convertView != null) && (convertView.getTag().equals("SeperatedListAdapterView")))
				{
					sectionHeaderView = (TextView)convertView;
				}
				else
				{
					sectionHeaderView = new TextView(parent.getContext(), null, android.R.attr.listSeparatorTextViewStyle);
					sectionHeaderView.setTag("SeperatedListAdapterView");
					sectionHeaderView.setPadding(5, 2, 0, 2);
				}
				sectionHeaderView.setText((String)section);
				return sectionHeaderView;
			}
			else if(position < size)
			{
				return adapter.getView(position - 1, convertView, parent);
			}
			
			// otherwise jump into next section
			position -= size;
			sectionnum++;
		}
		return null;
	}

	@Override	
	public boolean areAllItemsEnabled()
	{
		return false;
	}

	@Override
	public boolean isEnabled(int position)
	{
		ListAdapter adapter;
		int size;
		
		for(Object section : this.sections.keySet())
		{
			adapter = sections.get(section);
			size = adapter.getCount() + 1;

			// check if position inside this section
			if(position == 0)
				return false;
			else if(position < size)
				return adapter.isEnabled(position - 1);

			// otherwise jump into next section
			position -= size;
		}
		return false;
	}
	
	@Override
	public long getItemId(int position)
	{
		ListAdapter adapter;
		int size;
		
		for(Object section : this.sections.keySet())
		{
			adapter = sections.get(section);
			size = adapter.getCount() + 1;

			// check if position inside this section
			if(position == 0)
				return 0;
			else if(position < size)
				return adapter.getItemId(position - 1);

			// otherwise jump into next section
			position -= size;
		}
		return 0;
	}
}
