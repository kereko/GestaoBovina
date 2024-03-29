package br.agr.terras.materialdroid.childs.calendar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import br.agr.terras.materialdroid.R;

/**
 * Created by leo on 15/06/16.
 */
public class AgendaEventView extends LinearLayout {
    public static AgendaEventView inflate(ViewGroup parent) {
        return (AgendaEventView) LayoutInflater.from(parent.getContext()).inflate(R.layout.view_agenda_event, parent, false);
    }

    // region Constructors

    public AgendaEventView(Context context) {
        this(context, null);
    }

    public AgendaEventView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AgendaEventView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setPadding(getResources().getDimensionPixelSize(R.dimen.agenda_event_view_padding_left),
                getResources().getDimensionPixelSize(R.dimen.agenda_event_view_padding_top),
                getResources().getDimensionPixelSize(R.dimen.agenda_event_view_padding_right),
                getResources().getDimensionPixelSize(R.dimen.agenda_event_view_padding_bottom));
    }

    // endregion
}
