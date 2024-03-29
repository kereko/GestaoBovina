package br.agr.terras.materialdroid.utils;

import android.annotation.TargetApi;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.support.annotation.ColorInt;

/**
 * Created by leo on 13/04/16.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class RippleHelper {

    public static void applyColor(Drawable d, @ColorInt int color) {
        if (d instanceof RippleDrawable)
            ((RippleDrawable) d).setColor(ColorStateList.valueOf(color));
    }
}