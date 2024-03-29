package br.agr.terras.materialdroid.utils.cardview;

/**
 * Created by leo on 03/10/16.
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import br.agr.terras.materialdroid.parents.CardViewImpl;
import br.agr.terras.materialdroid.utils.drawable.RoundRectDrawableWithShadow;

public class CardViewEclairMr1 implements CardViewImpl {

    final RectF sCornerRect = new RectF();

    @Override
    public void initStatic() {
        // Draws a round rect using 7 draw operations. This is faster than using
        // canvas.drawRoundRect before JBMR1 because API 11-16 used alpha mask textures to draw
        // shapes.
        RoundRectDrawableWithShadow.sRoundRectHelper =
                new RoundRectDrawableWithShadow.RoundRectHelper() {
                    @Override
                    public void drawRoundRect(Canvas canvas, RectF bounds, float cornerRadius,
                                              Paint paint) {
                        final float twoRadius = cornerRadius * 2;
                        final float innerWidth = bounds.width() - twoRadius - 1;
                        final float innerHeight = bounds.height() - twoRadius - 1;
                        if (cornerRadius >= 1f) {
                            // increment corner radius to account for half pixels.
                            float roundedCornerRadius = cornerRadius + .5f;
                            sCornerRect.set(-roundedCornerRadius, -roundedCornerRadius, roundedCornerRadius,
                                    roundedCornerRadius);
                            int saved = canvas.save();
                            canvas.translate(bounds.left + roundedCornerRadius,
                                    bounds.top + roundedCornerRadius);
                            canvas.drawArc(sCornerRect, 180, 90, true, paint);
                            canvas.translate(innerWidth, 0);
                            canvas.rotate(90);
                            canvas.drawArc(sCornerRect, 180, 90, true, paint);
                            canvas.translate(innerHeight, 0);
                            canvas.rotate(90);
                            canvas.drawArc(sCornerRect, 180, 90, true, paint);
                            canvas.translate(innerWidth, 0);
                            canvas.rotate(90);
                            canvas.drawArc(sCornerRect, 180, 90, true, paint);
                            canvas.restoreToCount(saved);
                            //draw top and bottom pieces
                            canvas.drawRect(bounds.left + roundedCornerRadius - 1f, bounds.top,
                                    bounds.right - roundedCornerRadius + 1f,
                                    bounds.top + roundedCornerRadius, paint);
                            canvas.drawRect(bounds.left + roundedCornerRadius - 1f,
                                    bounds.bottom - roundedCornerRadius + 1f,
                                    bounds.right - roundedCornerRadius + 1f, bounds.bottom, paint);
                        }
                        // center
                        canvas.drawRect(bounds.left, bounds.top + Math.max(0, cornerRadius - 1f),
                                bounds.right, bounds.bottom - cornerRadius + 1f, paint);
                    }
                };
    }

    @Override
    public void initialize(CardViewDelegate cardView, Context context, int backgroundColor,
                           float radius, float elevation, float maxElevation) {
        RoundRectDrawableWithShadow background = createBackground(context, backgroundColor, radius,
                elevation, maxElevation);
        background.setAddPaddingForCorners(cardView.getPreventCornerOverlap());
        cardView.setCardBackground(background);
        updatePadding(cardView);
    }

    private RoundRectDrawableWithShadow createBackground(Context context, int backgroundColor,
                                                                                   float radius, float elevation, float maxElevation) {
        return new RoundRectDrawableWithShadow(context.getResources(), backgroundColor, radius,
                elevation, maxElevation);
    }

    @Override
    public void updatePadding(CardViewDelegate cardView) {
        Rect shadowPadding = new Rect();
        getShadowBackground(cardView).getMaxShadowAndCornerPadding(shadowPadding);
        cardView.setMinWidthHeightInternal((int) Math.ceil(getMinWidth(cardView)),
                (int) Math.ceil(getMinHeight(cardView)));
        cardView.setShadowPadding(shadowPadding.left, shadowPadding.top,
                shadowPadding.right, shadowPadding.bottom);
    }

    @Override
    public void onCompatPaddingChanged(CardViewDelegate cardView) {
        // NO OP
    }

    @Override
    public void onPreventCornerOverlapChanged(CardViewDelegate cardView) {
        getShadowBackground(cardView).setAddPaddingForCorners(cardView.getPreventCornerOverlap());
        updatePadding(cardView);
    }

    @Override
    public void setBackgroundColor(CardViewDelegate cardView, int color) {
        getShadowBackground(cardView).setColor(color);
    }

    @Override
    public void setRadius(CardViewDelegate cardView, float radius) {
        getShadowBackground(cardView).setCornerRadius(radius);
        updatePadding(cardView);
    }

    @Override
    public float getRadius(CardViewDelegate cardView) {
        return getShadowBackground(cardView).getCornerRadius();
    }

    @Override
    public void setElevation(CardViewDelegate cardView, float elevation) {
        getShadowBackground(cardView).setShadowSize(elevation);
    }

    @Override
    public float getElevation(CardViewDelegate cardView) {
        return getShadowBackground(cardView).getShadowSize();
    }

    @Override
    public void setMaxElevation(CardViewDelegate cardView, float maxElevation) {
        getShadowBackground(cardView).setMaxShadowSize(maxElevation);
        updatePadding(cardView);
    }

    @Override
    public float getMaxElevation(CardViewDelegate cardView) {
        return getShadowBackground(cardView).getMaxShadowSize();
    }

    @Override
    public float getMinWidth(CardViewDelegate cardView) {
        return getShadowBackground(cardView).getMinWidth();
    }

    @Override
    public float getMinHeight(CardViewDelegate cardView) {
        return getShadowBackground(cardView).getMinHeight();
    }


    @Override
    public void setColorShadow(CardViewDelegate cardView, int colorShadow) {
        getShadowBackground(cardView).setColorShadow(colorShadow);
    }

    @Override
    public void setAlphaShadow(CardViewDelegate cardView, int alphaStart, int alphaEnd) {
        getShadowBackground(cardView).setShadowAlpha(alphaStart, alphaEnd);
    }

    private RoundRectDrawableWithShadow getShadowBackground(CardViewDelegate cardView) {
        return ((RoundRectDrawableWithShadow) cardView.getCardBackground());
    }
}
