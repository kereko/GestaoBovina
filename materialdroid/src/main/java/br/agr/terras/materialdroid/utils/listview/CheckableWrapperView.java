package br.agr.terras.materialdroid.utils.listview;

import android.content.Context;
import android.widget.Checkable;

import br.agr.terras.materialdroid.childs.listview.WrapperView;

/**
 * Created by leo on 15/06/16.
 */
public class CheckableWrapperView extends WrapperView implements Checkable {

    public CheckableWrapperView(final Context context) {
        super(context);
    }

    @Override
    public boolean isChecked() {
        return ((Checkable) mItem).isChecked();
    }

    @Override
    public void setChecked(final boolean checked) {
        ((Checkable) mItem).setChecked(checked);
    }

    @Override
    public void toggle() {
        setChecked(!isChecked());
    }
}