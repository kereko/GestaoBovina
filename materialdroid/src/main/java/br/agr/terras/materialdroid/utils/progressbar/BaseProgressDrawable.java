/*
 * Copyright (c) 2015 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package br.agr.terras.materialdroid.utils.progressbar;

abstract class BaseProgressDrawable extends BasePaintDrawable implements IntrinsicPaddingDrawable {

    protected boolean mUseIntrinsicPadding = true;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getUseIntrinsicPadding() {
        return mUseIntrinsicPadding;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUseIntrinsicPadding(boolean useIntrinsicPadding) {
        if (mUseIntrinsicPadding != useIntrinsicPadding) {
            mUseIntrinsicPadding = useIntrinsicPadding;
            invalidateSelf();
        }
    }
}
