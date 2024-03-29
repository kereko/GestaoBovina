package br.agr.terras.materialdroid.childs.calendar;

import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

import br.agr.terras.materialdroid.utils.calendar.CalendarEvent;
import br.agr.terras.materialdroid.utils.calendar.DefaultEventRenderer;
import br.agr.terras.materialdroid.utils.calendar.EventRenderer;
import br.agr.terras.materialdroid.utils.listview.StickyListHeadersAdapter;

/**
 * Created by leo on 15/06/16.
 */
public class AgendaAdapter extends BaseAdapter implements StickyListHeadersAdapter {

    private List<CalendarEvent> mEvents = new ArrayList<>();
    private List<EventRenderer<?>> mRenderers = new ArrayList<>();
    private int mCurrentDayColor;

    // region Constructor

    public AgendaAdapter(int currentDayTextColor) {
        this.mCurrentDayColor = currentDayTextColor;
    }

    // endregion

    // region Public methods

    public void updateEvents(List<CalendarEvent> events) {
        this.mEvents.clear();
        this.mEvents.addAll(events);
        notifyDataSetChanged();
    }

    // endregion

    // region Interface - StickyListHeadersAdapter

    @Override
    public View getHeaderView(int position, View convertView, ViewGroup parent) {
        AgendaHeaderView agendaHeaderView = (AgendaHeaderView) convertView;
        if (agendaHeaderView == null) {
            agendaHeaderView = AgendaHeaderView.inflate(parent);
        }
        agendaHeaderView.setDay(getItem(position).getInstanceDay(), mCurrentDayColor);
        return agendaHeaderView;
    }

    @Override
    public long getHeaderId(int position) {
        return mEvents.get(position).getInstanceDay().getTimeInMillis();
    }

    // endregion

    // region Class - BaseAdapter

    @Override
    public int getCount() {
        return mEvents.size();
    }

    @Override
    public CalendarEvent getItem(int position) {
        return mEvents.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        EventRenderer eventRenderer = new DefaultEventRenderer();
        final CalendarEvent event = getItem(position);

        // Search for the correct event renderer
        for (EventRenderer renderer : mRenderers) {
            if(event.getClass().isAssignableFrom(renderer.getRenderType())) {
                eventRenderer = renderer;
                break;
            }
        }
        convertView = LayoutInflater.from(parent.getContext())
                .inflate(eventRenderer.getEventLayout(), parent, false);
        eventRenderer.render(convertView, event);
        return convertView;
    }

    public void addEventRenderer(@NonNull final EventRenderer<?> renderer) {
        mRenderers.add(renderer);
    }

    // endregion
}