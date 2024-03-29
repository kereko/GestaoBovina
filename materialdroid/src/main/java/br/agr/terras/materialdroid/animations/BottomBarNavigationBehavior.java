package br.agr.terras.materialdroid.animations;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorCompat;
import android.support.v4.view.ViewPropertyAnimatorUpdateListener;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import br.agr.terras.materialdroid.FloatingActionButton;
import br.agr.terras.materialdroid.R;
import br.agr.terras.materialdroid.childs.SnackBarLayout;
import br.agr.terras.materialdroid.childs.TabLayout;
import br.agr.terras.materialdroid.parents.CoordinatorLayout;

/**
 * Created by leo on 02/06/16.
 */
public class BottomBarNavigationBehavior<V extends View> extends VerticalScrollingBehavior<V> {

    private static final Interpolator INTERPOLATOR = new LinearOutSlowInInterpolator();
    private static final int ANIM_DURATION = 300;

    private int mTabLayoutId;
    private boolean hidden = false;
    private ViewPropertyAnimatorCompat translationAnimator;
    private ObjectAnimator translationObjectAnimator;
    private TabLayout mTabLayout;
    private SnackBarLayout.SnackBarLayoutLayout snackbarLayout;
    private FloatingActionButton floatingActionButton;
    private int mSnackbarHeight = -1;
    private boolean fabBottomMarginInitialized = false;
    private float targetOffset = 0, fabTargetOffset = 0, fabDefaultBottomMargin = 0, snackBarY = 0;
    private boolean behaviorTranslationEnabled = true;

    /**
     * Constructor
     */
    public BottomBarNavigationBehavior() {
        super();
    }

    public BottomBarNavigationBehavior(boolean behaviorTranslationEnabled) {
        super();
        this.behaviorTranslationEnabled = behaviorTranslationEnabled;
    }

    public BottomBarNavigationBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BottomBar);
        mTabLayoutId = a.getResourceId(R.styleable.BottomBar_bb_tabLayoutId, View.NO_ID);
        a.recycle();
    }

    @Override
    public boolean onLayoutChild(CoordinatorLayout parent, V child, int layoutDirection) {
        boolean layoutChild = super.onLayoutChild(parent, child, layoutDirection);
        if (mTabLayout == null && mTabLayoutId != View.NO_ID) {
            mTabLayout = findTabLayout(child);
        }
        return layoutChild;
    }

    private TabLayout findTabLayout(View child) {
        if (mTabLayoutId == 0) return null;
        return (TabLayout) child.findViewById(mTabLayoutId);
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, V child, View dependency) {
        return super.onDependentViewChanged(parent, child, dependency);
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, V child, View dependency) {
        updateSnackbar(child, dependency);
        updateFloatingActionButton(dependency);
        return super.layoutDependsOn(parent, child, dependency);
    }

    @Override
    public void onNestedVerticalOverScroll(CoordinatorLayout coordinatorLayout, V child, @ScrollDirection int direction, int currentOverScroll, int totalOverScroll) {
    }

    @Override
    public void onDirectionNestedPreScroll(CoordinatorLayout coordinatorLayout, V child, View target, int dx, int dy, int[] consumed, @ScrollDirection int scrollDirection) {
    }

    @Override
    protected boolean onNestedDirectionFling(CoordinatorLayout coordinatorLayout, V child, View target, float velocityX, float velocityY, @ScrollDirection int scrollDirection) {
        return false;
    }

    @Override
    public void onNestedScroll(CoordinatorLayout coordinatorLayout, V child, View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
        if (dyConsumed < 0) {
            handleDirection(child, ScrollDirection.SCROLL_DIRECTION_DOWN);
        } else if (dyConsumed > 0) {
            handleDirection(child, ScrollDirection.SCROLL_DIRECTION_UP);
        }
    }

    @Override
    public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout, V child, View directTargetChild, View target, int nestedScrollAxes) {
        return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL || super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, nestedScrollAxes);
    }

    /**
     * Handle scroll direction
     * @param child
     * @param scrollDirection
     */
    private void handleDirection(V child, int scrollDirection) {
        if (!behaviorTranslationEnabled) {
            return;
        }
        if (scrollDirection == ScrollDirection.SCROLL_DIRECTION_DOWN && hidden) {
            hidden = false;
            animateOffset(child, 0, false, true);
        } else if (scrollDirection == ScrollDirection.SCROLL_DIRECTION_UP && !hidden) {
            hidden = true;
            animateOffset(child, child.getHeight(), false, true);
        }
    }

    /**
     * Animate offset
     *
     * @param child
     * @param offset
     */
    private void animateOffset(final V child, final int offset, boolean forceAnimation, boolean withAnimation) {
        if (!behaviorTranslationEnabled && !forceAnimation) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            ensureOrCancelObjectAnimation(child, offset, withAnimation);
            translationObjectAnimator.start();
        } else {
            ensureOrCancelAnimator(child, withAnimation);
            translationAnimator.translationY(offset).start();
        }
    }

    /**
     * Manage animation for Android >= KITKAT
     *
     * @param child
     */
    private void ensureOrCancelAnimator(V child, boolean withAnimation) {
        if (translationAnimator == null) {
            translationAnimator = ViewCompat.animate(child);
            translationAnimator.setDuration(withAnimation ? ANIM_DURATION : 0);
            translationAnimator.setUpdateListener(new ViewPropertyAnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(View view) {
                    // Animate snackbar
                    if (snackbarLayout != null && snackbarLayout.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                        targetOffset = view.getMeasuredHeight() - view.getTranslationY();
                        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) snackbarLayout.getLayoutParams();
                        p.setMargins(p.leftMargin, p.topMargin, p.rightMargin, (int) targetOffset);
                        snackbarLayout.requestLayout();
                    }
                    // Animate Floating Action Button
                    if (floatingActionButton != null && floatingActionButton.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) floatingActionButton.getLayoutParams();
                        fabTargetOffset = fabDefaultBottomMargin - view.getTranslationY() + snackBarY;
                        p.setMargins(p.leftMargin, p.topMargin, p.rightMargin, (int) fabTargetOffset);
                        floatingActionButton.requestLayout();
                    }
                }
            });
            translationAnimator.setInterpolator(INTERPOLATOR);
        } else {
            translationAnimator.setDuration(withAnimation ? ANIM_DURATION : 0);
            translationAnimator.cancel();
        }
    }

    /**
     * Manage animation for Android < KITKAT
     *
     * @param child
     */
    private void ensureOrCancelObjectAnimation(final V child, final int offset, boolean withAnimation) {

        if (translationObjectAnimator != null) {
            translationObjectAnimator.cancel();
        }

        translationObjectAnimator = ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, offset);
        translationObjectAnimator.setDuration(withAnimation ? ANIM_DURATION : 0);
        translationObjectAnimator.setInterpolator(INTERPOLATOR);
        translationObjectAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (snackbarLayout != null && snackbarLayout.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    targetOffset = child.getMeasuredHeight() - child.getTranslationY();
                    ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) snackbarLayout.getLayoutParams();
                    p.setMargins(p.leftMargin, p.topMargin, p.rightMargin, (int) targetOffset);
                    snackbarLayout.requestLayout();
                }
                // Animate Floating Action Button
                if (floatingActionButton != null && floatingActionButton.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) floatingActionButton.getLayoutParams();
                    fabTargetOffset = fabDefaultBottomMargin - child.getTranslationY() + snackBarY;
                    p.setMargins(p.leftMargin, p.topMargin, p.rightMargin, (int) fabTargetOffset);
                    floatingActionButton.requestLayout();
                }
            }
        });
    }


    public static <V extends View> BottomBarNavigationBehavior<V> from(V view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof CoordinatorLayout.LayoutParams)) {
            throw new IllegalArgumentException("The view is not a child of CoordinatorLayout");
        }
        CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) params)
                .getBehavior();
        if (!(behavior instanceof BottomBarNavigationBehavior)) {
            throw new IllegalArgumentException(
                    "The view is not associated com AHBottomNavigationBehavior");
        }
        return (BottomBarNavigationBehavior<V>) behavior;
    }

    public void setTabLayoutId(int tabId) {
        this.mTabLayoutId = tabId;
    }

    /**
     * Enable or not the behavior translation
     * @param behaviorTranslationEnabled
     */
    public void setBehaviorTranslationEnabled(boolean behaviorTranslationEnabled) {
        this.behaviorTranslationEnabled = behaviorTranslationEnabled;
    }

    /**
     * Hide AHBottomNavigation com animation
     * @param view
     * @param offset
     */
    public void hideView(V view, int offset, boolean withAnimation) {
        if (!hidden) {
            hidden = true;
            animateOffset(view, offset, true, withAnimation);
        }
    }

    /**
     * Reset AHBottomNavigation position com animation
     * @param view
     */
    public void resetOffset(V view, boolean withAnimation) {
        if (hidden) {
            hidden = false;
            animateOffset(view, 0, true, withAnimation);
        }
    }

    /**
     * Update Snackbar bottom margin
     */
    public void updateSnackbar(final View child, View dependency) {

        if (dependency != null && dependency instanceof SnackBarLayout.SnackBarLayoutLayout) {

            snackbarLayout = (SnackBarLayout.SnackBarLayoutLayout) dependency;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                snackbarLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        if (floatingActionButton != null &&
                                floatingActionButton.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) floatingActionButton.getLayoutParams();
                            snackBarY = bottom - v.getY();
                            fabTargetOffset = fabDefaultBottomMargin - child.getTranslationY() + snackBarY;
                            p.setMargins(p.leftMargin, p.topMargin, p.rightMargin, (int) fabTargetOffset);
                            floatingActionButton.requestLayout();
                        }
                    }
                });
            }

            if (mSnackbarHeight == -1) {
                mSnackbarHeight = dependency.getHeight();
            }

            int targetMargin = (int) (child.getMeasuredHeight() - child.getTranslationY());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                child.bringToFront();
            }

            if (dependency.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) dependency.getLayoutParams();
                p.setMargins(p.leftMargin, p.topMargin, p.rightMargin, targetMargin);
                dependency.requestLayout();
            }
        }
    }

    /**
     * Update floating action button bottom margin
     */
    public void updateFloatingActionButton(View dependency) {
        if (dependency != null && dependency instanceof FloatingActionButton) {
            floatingActionButton = (FloatingActionButton) dependency;
            if (!fabBottomMarginInitialized && dependency.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                fabBottomMarginInitialized = true;
                ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) dependency.getLayoutParams();
                fabDefaultBottomMargin = p.bottomMargin;
            }
        }
    }
}
