package br.agr.terras.materialdroid.parents;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.agr.terras.materialdroid.R;


public class CoordinatorLayout extends ViewGroup implements NestedScrollingParent {
    static final String TAG = "CoordinatorLayout";
    static final String WIDGET_PACKAGE_NAME = CoordinatorLayout.class.getPackage().getName();

    static {
        if (Build.VERSION.SDK_INT >= 21) {
            TOP_SORTED_CHILDREN_COMPARATOR = new ViewElevationComparator();
        } else {
            TOP_SORTED_CHILDREN_COMPARATOR = null;
        }
    }

    static final Class<?>[] CONSTRUCTOR_PARAMS = new Class<?>[]{
            Context.class,
            AttributeSet.class
    };
    static final ThreadLocal<Map<String, Constructor<Behavior>>> sConstructors =
            new ThreadLocal<>();
    final Comparator<View> mLayoutDependencyComparator = new Comparator<View>() {
        @Override
        public int compare(View lhs, View rhs) {
            if (lhs == rhs) {
                return 0;
            } else if (((LayoutParams) lhs.getLayoutParams()).dependsOn(
                    CoordinatorLayout.this, lhs, rhs)) {
                return 1;
            } else if (((LayoutParams) rhs.getLayoutParams()).dependsOn(
                    CoordinatorLayout.this, rhs, lhs)) {
                return -1;
            } else {
                return 0;
            }
        }
    };
    static final Comparator<View> TOP_SORTED_CHILDREN_COMPARATOR;
    private final List<View> mDependencySortedChildren = new ArrayList<View>();
    private final List<View> mTempList1 = new ArrayList<>();
    private final List<View> mTempDependenciesList = new ArrayList<>();
    private final Rect mTempRect1 = new Rect();
    private final Rect mTempRect2 = new Rect();
    private final Rect mTempRect3 = new Rect();
    private final int[] mTempIntPair = new int[2];
    private Paint mScrimPaint;
    private boolean mIsAttachedToWindow;
    private int[] mKeylines;
    private View mBehaviorTouchView;
    private View mNestedScrollingDirectChild;
    private View mNestedScrollingTarget;
    private OnPreDrawListener mOnPreDrawListener;
    private boolean mNeedsPreDrawListener;
    private final NestedScrollingParentHelper mNestedScrollingParentHelper =
            new NestedScrollingParentHelper(this);

    public CoordinatorLayout(Context context) {
        this(context, null);
    }

    public CoordinatorLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CoordinatorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CoordinatorLayout,
                defStyleAttr, 0);
        final int keylineArrayRes = a.getResourceId(R.styleable.CoordinatorLayout_keylines, 0);
        if (keylineArrayRes != 0) {
            final Resources res = context.getResources();
            mKeylines = res.getIntArray(keylineArrayRes);
            final float density = res.getDisplayMetrics().density;
            final int count = mKeylines.length;
            for (int i = 0; i < count; i++) {
                mKeylines[i] *= density;
            }
        }
        a.recycle();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        resetTouchBehaviors();
        if (mNeedsPreDrawListener) {
            if (mOnPreDrawListener == null) {
                mOnPreDrawListener = new OnPreDrawListener();
            }
            final ViewTreeObserver vto = getViewTreeObserver();
            vto.addOnPreDrawListener(mOnPreDrawListener);
        }
        mIsAttachedToWindow = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        resetTouchBehaviors();
        if (mNeedsPreDrawListener && mOnPreDrawListener != null) {
            final ViewTreeObserver vto = getViewTreeObserver();
            vto.removeOnPreDrawListener(mOnPreDrawListener);
        }
        if (mNestedScrollingTarget != null) {
            onStopNestedScroll(mNestedScrollingTarget);
        }
        mIsAttachedToWindow = false;
    }

    /**
     * Reset all Behavior-related tracking records either to clean up or in preparation
     * for a new event stream. This should be called when attached or detached from a window,
     * in response to an UP or CANCEL event, when intercept is request-disallowed
     * and similar cases where an event stream in progress will be aborted.
     */
    private void resetTouchBehaviors() {
        if (mBehaviorTouchView != null) {
            final Behavior b = ((LayoutParams) mBehaviorTouchView.getLayoutParams()).getBehavior();
            if (b != null) {
                final long now = SystemClock.uptimeMillis();
                final MotionEvent cancelEvent = MotionEvent.obtain(now, now,
                        MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
                b.onTouchEvent(this, mBehaviorTouchView, cancelEvent);
                cancelEvent.recycle();
            }
            mBehaviorTouchView = null;
        }
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.resetTouchBehaviorTracking();
        }
    }

    /**
     * Populate a list com the current child views, sorted such that the topmost views
     * in z-order are at the front of the list. Useful for hit testing and event dispatch.
     */
    private void getTopSortedChildren(List<View> out) {
        out.clear();
        final boolean useCustomOrder = isChildrenDrawingOrderEnabled();
        final int childCount = getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            final int childIndex = useCustomOrder ? getChildDrawingOrder(childCount, i) : i;
            final View child = getChildAt(childIndex);
            out.add(child);
        }
        if (TOP_SORTED_CHILDREN_COMPARATOR != null) {
            Collections.sort(out, TOP_SORTED_CHILDREN_COMPARATOR);
        }
    }

    private boolean performIntercept(MotionEvent ev) {
        boolean intercepted = false;
        boolean newBlock = false;
        MotionEvent cancelEvent = null;
        final int action = ev.getActionMasked();
        final List<View> topmostChildList = mTempList1;
        getTopSortedChildren(topmostChildList);
        // Let topmost child views inspect first
        final int childCount = topmostChildList.size();
        for (int i = 0; i < childCount; i++) {
            final View child = topmostChildList.get(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final Behavior b = lp.getBehavior();
            if ((intercepted || newBlock) && action != MotionEvent.ACTION_DOWN) {
                // Cancel all behaviors beneath the one that intercepted.
                // If the event is "down" then we don't have anything to cancel yet.
                if (b != null) {
                    if (cancelEvent != null) {
                        final long now = SystemClock.uptimeMillis();
                        cancelEvent = MotionEvent.obtain(now, now,
                                MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
                    }
                    b.onInterceptTouchEvent(this, child, cancelEvent);
                }
                continue;
            }
            if (!intercepted && b != null
                    && (intercepted = b.onInterceptTouchEvent(this, child, ev))) {
                mBehaviorTouchView = child;
            }
            // Don't keep going if we're not allowing interaction below this.
            // Setting newBlock will make sure we cancel the rest of the behaviors.
            final boolean wasBlocking = lp.didBlockInteraction();
            final boolean isBlocking = lp.isBlockingInteractionBelow(this, child);
            newBlock = isBlocking && !wasBlocking;
            if (isBlocking && !newBlock) {
                // Stop here since we don't have anything more to cancel - we already did
                // when the behavior first started blocking things below this point.
                break;
            }
        }
        topmostChildList.clear();
        return intercepted;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        MotionEvent cancelEvent = null;
        final int action = ev.getActionMasked();
        // Make sure we reset in case we had missed a previous important event.
        if (action == MotionEvent.ACTION_DOWN) {
            resetTouchBehaviors();
        }
        final boolean intercepted = performIntercept(ev);
        if (cancelEvent != null) {
            cancelEvent.recycle();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            resetTouchBehaviors();
        }
        return intercepted;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = false;
        boolean cancelSuper = false;
        MotionEvent cancelEvent = null;
        final int action = ev.getActionMasked();
        if (mBehaviorTouchView != null || (cancelSuper = performIntercept(ev))) {
            // Safe since performIntercept guarantees that
            // mBehaviorTouchView != null if it returns true
            final LayoutParams lp = (LayoutParams) mBehaviorTouchView.getLayoutParams();
            final Behavior b = lp.getBehavior();
            if (b != null) {
                b.onTouchEvent(this, mBehaviorTouchView, ev);
            }
        }
        // Keep the super implementation correct
        if (mBehaviorTouchView == null) {
            handled |= super.onTouchEvent(ev);
        } else if (cancelSuper) {
            if (cancelEvent != null) {
                final long now = SystemClock.uptimeMillis();
                cancelEvent = MotionEvent.obtain(now, now,
                        MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
            }
            super.onTouchEvent(cancelEvent);
        }
        if (!handled && action == MotionEvent.ACTION_DOWN) {
        }
        if (cancelEvent != null) {
            cancelEvent.recycle();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            resetTouchBehaviors();
        }
        return handled;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        if (disallowIntercept) {
            resetTouchBehaviors();
        }
    }

    private int getKeyline(int index) {
        if (mKeylines == null) {
            Log.e(TAG, "No keylines defined for " + this + " - attempted index lookup " + index);
            return 0;
        }
        if (index < 0 || index >= mKeylines.length) {
            Log.e(TAG, "Keyline index " + index + " out of range for " + this);
            return 0;
        }
        return mKeylines[index];
    }

    static Behavior parseBehavior(Context context, AttributeSet attrs, String name) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        final String fullName;
        if (name.startsWith(".")) {
            // Relative to the app package. Prepend the app package name.
            fullName = context.getPackageName() + name;
        } else if (name.indexOf('.') >= 0) {
            // Fully qualified package name.
            fullName = name;
        } else {
            // Assume stock behavior in this package.
            fullName = WIDGET_PACKAGE_NAME + '.' + name;
        }
        try {
            Map<String, Constructor<Behavior>> constructors = sConstructors.get();
            if (constructors == null) {
                constructors = new HashMap<>();
                sConstructors.set(constructors);
            }
            Constructor<Behavior> c = constructors.get(fullName);
            if (c == null) {
                final Class<Behavior> clazz = (Class<Behavior>) Class.forName(fullName, true,
                        context.getClassLoader());
                c = clazz.getConstructor(CONSTRUCTOR_PARAMS);
                constructors.put(fullName, c);
            }
            return c.newInstance(context, attrs);
        } catch (Exception e) {
            throw new RuntimeException("Could not inflate Behavior subclass " + fullName, e);
        }
    }

    LayoutParams getResolvedLayoutParams(View child) {
        final LayoutParams result = (LayoutParams) child.getLayoutParams();
        if (!result.mBehaviorResolved) {
            Class<?> childClass = child.getClass();
            DefaultBehavior defaultBehavior = null;
            while (childClass != null &&
                    (defaultBehavior = childClass.getAnnotation(DefaultBehavior.class)) == null) {
                childClass = childClass.getSuperclass();
            }
            if (defaultBehavior != null) {
                try {
                    result.setBehavior(defaultBehavior.value().newInstance());
                } catch (Exception e) {
                    Log.e(TAG, "Default behavior class " + defaultBehavior.value().getName() +
                            " could not be instantiated. Did you forget a default constructor?", e);
                }
            }
            result.mBehaviorResolved = true;
        }
        return result;
    }

    private void prepareChildren() {
        final int childCount = getChildCount();
        boolean resortRequired = mDependencySortedChildren.size() != childCount;
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = getResolvedLayoutParams(child);
            if (!resortRequired && lp.isDirty(this, child)) {
                resortRequired = true;
            }
            lp.findAnchorView(this, child);
        }
        if (resortRequired) {
            mDependencySortedChildren.clear();
            for (int i = 0; i < childCount; i++) {
                mDependencySortedChildren.add(getChildAt(i));
            }
            Collections.sort(mDependencySortedChildren, mLayoutDependencyComparator);
        }
    }

    /**
     * Retrieve the transformed bounding rect of an arbitrary descendant view.
     * This does not need to be a direct child.
     *
     * @param descendant descendant view to reference
     * @param out        rect to set to the bounds of the descendant view
     */
    void getDescendantRect(View descendant, Rect out) {
        getDescendantRect(this, descendant, out);
    }

    void getDescendantRect(ViewGroup parent, View descendant, Rect out) {
        out.set(0, 0, descendant.getWidth(), descendant.getHeight());
        offsetDescendantRect(parent, descendant, out);
    }

    void offsetDescendantRect(ViewGroup parent, View descendant, Rect rect) {
        if (descendant.getParent() instanceof CoordinatorLayout)
            if (descendant.getParent() == this)
                parent.offsetDescendantRectToMyCoords(descendant, rect);
    }

    @Override
    protected int getSuggestedMinimumWidth() {
        return Math.max(super.getSuggestedMinimumWidth(), getPaddingLeft() + getPaddingRight());
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        return Math.max(super.getSuggestedMinimumHeight(), getPaddingTop() + getPaddingBottom());
    }

    /**
     * Called to measure each individual child view unless a
     * {@link Behavior Behavior} is present. The Behavior may choose to delegate
     * child measurement to this method.
     *
     * @param child                   the child to measure
     * @param parentWidthMeasureSpec  the width requirements for this view
     * @param widthUsed               extra space that has been used up by the parent
     *                                horizontally (possibly by other children of the parent)
     * @param parentHeightMeasureSpec the height requirements for this view
     * @param heightUsed              extra space that has been used up by the parent
     *                                vertically (possibly by other children of the parent)
     */
    public void onMeasureChild(View child, int parentWidthMeasureSpec, int widthUsed,
                               int parentHeightMeasureSpec, int heightUsed) {
        measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed,
                parentHeightMeasureSpec, heightUsed);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        prepareChildren();
        ensurePreDrawListener();
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int paddingRight = getPaddingRight();
        final int paddingBottom = getPaddingBottom();
        final int layoutDirection = ViewCompat.getLayoutDirection(this);
        final boolean isRtl = layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL;
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int widthPadding = paddingLeft + paddingRight;
        final int heightPadding = paddingTop + paddingBottom;
        int widthUsed = getSuggestedMinimumWidth();
        int heightUsed = getSuggestedMinimumHeight();
        int childState = 0;
        final int childCount = mDependencySortedChildren.size();
        for (int i = 0; i < childCount; i++) {
            final View child = mDependencySortedChildren.get(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int keylineWidthUsed = 0;
            if (lp.keyline >= 0 && widthMode != MeasureSpec.UNSPECIFIED) {
                final int keylinePos = getKeyline(lp.keyline);
                final int keylineGravity = GravityCompat.getAbsoluteGravity(
                        resolveKeylineGravity(lp.gravity), layoutDirection)
                        & Gravity.HORIZONTAL_GRAVITY_MASK;
                if ((keylineGravity == Gravity.LEFT && !isRtl)
                        || (keylineGravity == Gravity.RIGHT && isRtl)) {
                    keylineWidthUsed = Math.max(0, widthSize - paddingRight - keylinePos);
                } else if ((keylineGravity == Gravity.RIGHT && !isRtl)
                        || (keylineGravity == Gravity.LEFT && isRtl)) {
                    keylineWidthUsed = Math.max(0, keylinePos - paddingLeft);
                }
            }
            final Behavior b = lp.getBehavior();
            if (b == null || !b.onMeasureChild(this, child, widthMeasureSpec, keylineWidthUsed,
                    heightMeasureSpec, 0)) {
                onMeasureChild(child, widthMeasureSpec, keylineWidthUsed,
                        heightMeasureSpec, 0);
            }
            widthUsed = Math.max(widthUsed, widthPadding + child.getMeasuredWidth() +
                    lp.leftMargin + lp.rightMargin);
            heightUsed = Math.max(heightUsed, heightPadding + child.getMeasuredHeight() +
                    lp.topMargin + lp.bottomMargin);
            childState = ViewCompat.combineMeasuredStates(childState,
                    ViewCompat.getMeasuredState(child));
        }
        final int width = ViewCompat.resolveSizeAndState(widthUsed, widthMeasureSpec,
                childState & ViewCompat.MEASURED_STATE_MASK);
        final int height = ViewCompat.resolveSizeAndState(heightUsed, heightMeasureSpec,
                childState << ViewCompat.MEASURED_HEIGHT_STATE_SHIFT);
        setMeasuredDimension(width, height);
    }

    /**
     * Called to lay out each individual child view unless a
     * {@link Behavior Behavior} is present. The Behavior may choose to
     * delegate child measurement to this method.
     *
     * @param child           child view to lay out
     * @param layoutDirection the resolved layout direction for the CoordinatorLayout, such as
     *                        {@link ViewCompat#LAYOUT_DIRECTION_LTR} or
     *                        {@link ViewCompat#LAYOUT_DIRECTION_RTL}.
     */
    public void onLayoutChild(View child, int layoutDirection) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp.checkAnchorChanged()) {
            throw new IllegalStateException("An anchor may not be changed after CoordinatorLayout"
                    + " measurement begins before layout is complete.");
        }
        if (lp.mAnchorView != null) {
            layoutChildWithAnchor(child, lp.mAnchorView, layoutDirection);
        } else if (lp.keyline >= 0) {
            layoutChildWithKeyline(child, lp.keyline, layoutDirection);
        } else {
            layoutChild(child, layoutDirection);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int layoutDirection = ViewCompat.getLayoutDirection(this);
        final int childCount = mDependencySortedChildren.size();
        for (int i = 0; i < childCount; i++) {
            final View child = mDependencySortedChildren.get(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final Behavior behavior = lp.getBehavior();
            if (behavior == null || !behavior.onLayoutChild(this, child, layoutDirection)) {
                onLayoutChild(child, layoutDirection);
            }
        }
    }

    /**
     * Mark the last known child position rect for the given child view.
     * This will be used when checking if a child view's position has changed between frames.
     * The rect used here should be one returned by
     * {@link #getChildRect(View, boolean, Rect)}, com translation
     * disabled.
     *
     * @param child child view to set for
     * @param r     rect to set
     */
    void recordLastChildRect(View child, Rect r) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        lp.setLastChildRect(r);
    }

    /**
     * Get the last known child rect recorded by
     * {@link #recordLastChildRect(View, Rect)}.
     *
     * @param child child view to retrieve from
     * @param out   rect to set to the outpur values
     */
    void getLastChildRect(View child, Rect out) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        out.set(lp.getLastChildRect());
    }

    /**
     * Get the position rect for the given child. If the child has currently requested layout
     * or has a visibility of GONE.
     *
     * @param child     child view to check
     * @param transform true to include transformation in the output rect, false to
     *                  only account for the base position
     * @param out       rect to set to the output values
     */
    void getChildRect(View child, boolean transform, Rect out) {
        if (child.isLayoutRequested() || child.getVisibility() == View.GONE) {
            out.set(0, 0, 0, 0);
            return;
        }
        if (transform) {
            getDescendantRect(child, out);
        } else {
            out.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
        }
    }

    /**
     * Calculate the desired child rect relative to an anchor rect, respecting both
     * gravity and anchorGravity.
     *
     * @param child           child view to calculate a rect for
     * @param layoutDirection the desired layout direction for the CoordinatorLayout
     * @param anchorRect      rect in CoordinatorLayout coordinates of the anchor view area
     * @param out             rect to set to the output values
     */
    void getDesiredAnchoredChildRect(View child, int layoutDirection, Rect anchorRect, Rect out) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int absGravity = GravityCompat.getAbsoluteGravity(
                resolveAnchoredChildGravity(lp.gravity), layoutDirection);
        final int absAnchorGravity = GravityCompat.getAbsoluteGravity(
                resolveGravity(lp.anchorGravity),
                layoutDirection);
        final int hgrav = absGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        final int vgrav = absGravity & Gravity.VERTICAL_GRAVITY_MASK;
        final int anchorHgrav = absAnchorGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        final int anchorVgrav = absAnchorGravity & Gravity.VERTICAL_GRAVITY_MASK;
        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();
        int left;
        int top;
        // Align to the anchor
        switch (anchorHgrav) {
            default:
            case Gravity.LEFT:
                left = anchorRect.left;
                break;
            case Gravity.RIGHT:
                left = anchorRect.right - childWidth;
                break;
            case Gravity.CENTER_HORIZONTAL:
                left = anchorRect.left + (anchorRect.width() - childWidth) / 2;
                break;
        }
        switch (anchorVgrav) {
            default:
            case Gravity.TOP:
                top = anchorRect.top;
                break;
            case Gravity.BOTTOM:
                top = anchorRect.bottom - childHeight;
                break;
            case Gravity.CENTER_VERTICAL:
                top = anchorRect.top + (anchorRect.height() - childHeight) / 2;
                break;
        }
        // Offset by the child view's gravity itself
        switch (hgrav) {
            default:
            case Gravity.LEFT:
                // Do nothing, we're already in position.
                break;
            case Gravity.RIGHT:
                left += childWidth;
                break;
            case Gravity.CENTER_HORIZONTAL:
                left += childWidth / 2;
                break;
        }
        switch (vgrav) {
            default:
            case Gravity.TOP:
                // Do nothing, we're already in position.
                break;
            case Gravity.BOTTOM:
                top += childHeight;
                break;
            case Gravity.CENTER_VERTICAL:
                top += childHeight / 2;
                break;
        }
        final int width = getWidth();
        final int height = getHeight();
        // Obey margins and padding
        left = Math.max(getPaddingLeft() + lp.leftMargin,
                Math.min(left,
                        width - getPaddingRight() - childWidth - lp.rightMargin));
        top = Math.max(getPaddingTop() + lp.topMargin,
                Math.min(top,
                        height - getPaddingBottom() - childHeight - lp.bottomMargin));
        out.set(left, top, left + childWidth, top + childHeight);
    }

    /**
     * CORE ASSUMPTION: anchor has been laid out by the time this is called for a given child view.
     *
     * @param child           child to lay out
     * @param anchor          view to anchor child relative to; already laid out.
     * @param layoutDirection ViewCompat constant for layout direction
     */
    private void layoutChildWithAnchor(View child, View anchor, int layoutDirection) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final Rect anchorRect = mTempRect1;
        final Rect childRect = mTempRect2;
        getDescendantRect(anchor, anchorRect);
        getDesiredAnchoredChildRect(child, layoutDirection, anchorRect, childRect);
        child.layout(childRect.left, childRect.top, childRect.right, childRect.bottom);
    }

    /**
     * Lay out a child view com respect to a keyline.
     * <p>
     * <p>The keyline represents a horizontal offset from the unpadded starting edge of
     * the CoordinatorLayout. The child's gravity will affect how it is positioned com
     * respect to the keyline.</p>
     *
     * @param child           child to lay out
     * @param keyline         offset from the starting edge in pixels of the keyline to align com
     * @param layoutDirection ViewCompat constant for layout direction
     */
    private void layoutChildWithKeyline(View child, int keyline, int layoutDirection) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int absGravity = GravityCompat.getAbsoluteGravity(
                resolveKeylineGravity(lp.gravity), layoutDirection);
        final int hgrav = absGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        final int vgrav = absGravity & Gravity.VERTICAL_GRAVITY_MASK;
        final int width = getWidth();
        final int height = getHeight();
        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();
        if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL) {
            keyline = width - keyline;
        }
        int left = getKeyline(keyline) - childWidth;
        int top = 0;
        switch (hgrav) {
            default:
            case Gravity.LEFT:
                // Nothing to do.
                break;
            case Gravity.RIGHT:
                left += childWidth;
                break;
            case Gravity.CENTER_HORIZONTAL:
                left += childWidth / 2;
                break;
        }
        switch (vgrav) {
            default:
            case Gravity.TOP:
                // Do nothing, we're already in position.
                break;
            case Gravity.BOTTOM:
                top += childHeight;
                break;
            case Gravity.CENTER_VERTICAL:
                top += childHeight / 2;
                break;
        }
        // Obey margins and padding
        left = Math.max(getPaddingLeft() + lp.leftMargin,
                Math.min(left,
                        width - getPaddingRight() - childWidth - lp.rightMargin));
        top = Math.max(getPaddingTop() + lp.topMargin,
                Math.min(top,
                        height - getPaddingBottom() - childHeight - lp.bottomMargin));
        child.layout(left, top, left + childWidth, top + childHeight);
    }

    /**
     * Lay out a child view com no special handling. This will position the child as
     * if it were within a FrameLayout or similar simple frame.
     *
     * @param child           child view to lay out
     * @param layoutDirection ViewCompat constant for the desired layout direction
     */
    private void layoutChild(View child, int layoutDirection) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final Rect parent = mTempRect1;
        parent.set(getPaddingLeft() + lp.leftMargin,
                getPaddingTop() + lp.topMargin,
                getWidth() - getPaddingRight() - lp.rightMargin,
                getHeight() - getPaddingBottom() - lp.bottomMargin);
        final Rect out = mTempRect2;
        GravityCompat.apply(resolveGravity(lp.gravity), child.getMeasuredWidth(),
                child.getMeasuredHeight(), parent, out, layoutDirection);
        child.layout(out.left, out.top, out.right, out.bottom);
    }

    /**
     * Return the given gravity value or the default if the passed value is NO_GRAVITY.
     * This should be used for children that are not anchored to another view or a keyline.
     */
    private static int resolveGravity(int gravity) {
        return gravity == Gravity.NO_GRAVITY ? GravityCompat.START | Gravity.TOP : gravity;
    }

    /**
     * Return the given gravity value or the default if the passed value is NO_GRAVITY.
     * This should be used for children that are positioned relative to a keyline.
     */
    private static int resolveKeylineGravity(int gravity) {
        return gravity == Gravity.NO_GRAVITY ? GravityCompat.END | Gravity.TOP : gravity;
    }

    /**
     * Return the given gravity value or the default if the passed value is NO_GRAVITY.
     * This should be used for children that are anchored to another view.
     */
    private static int resolveAnchoredChildGravity(int gravity) {
        return gravity == Gravity.NO_GRAVITY ? Gravity.CENTER : gravity;
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp.mBehavior != null && lp.mBehavior.getScrimOpacity(this, child) > 0.f) {
            if (mScrimPaint == null) {
                mScrimPaint = new Paint();
            }
            mScrimPaint.setColor(lp.mBehavior.getScrimColor(this, child));
            // TODO: Set the clip appropriately to avoid unnecessary overdraw.
            canvas.drawRect(getPaddingLeft(), getPaddingTop(),
                    getWidth() - getPaddingRight(), getHeight() - getPaddingBottom(), mScrimPaint);
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    /**
     * Dispatch any dependent view changes to the relevant {@link Behavior} instances.
     * <p>
     * Usually run as part of the pre-draw step when at least one child view has a reported
     * dependency on another view. This allows CoordinatorLayout to account for layout
     * changes and animations that occur outside of the normal layout pass.
     * <p>
     * It can also be ran as part of the nested scrolling dispatch to ensure that any offsetting
     * is completed within the correct coordinate window.
     * <p>
     * The offsetting behavior implemented here does not store the computed offset in
     * the LayoutParams; instead it expects that the layout process will always reconstruct
     * the proper positioning.
     *
     * @param fromNestedScroll true if this is being called from one of the nested scroll methods,
     *                         false if run as part of the pre-draw step.
     */
    void dispatchOnDependentViewChanged(final boolean fromNestedScroll) {
        final int layoutDirection = ViewCompat.getLayoutDirection(this);
        final int childCount = mDependencySortedChildren.size();
        for (int i = 0; i < childCount; i++) {
            final View child = mDependencySortedChildren.get(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            // Check child views before for anchor
            for (int j = 0; j < i; j++) {
                final View checkChild = mDependencySortedChildren.get(j);
                if (lp.mAnchorDirectChild == checkChild) {
                    offsetChildToAnchor(child, layoutDirection);
                }
            }
            // Did it change? if not continue
            final Rect oldRect = mTempRect1;
            final Rect newRect = mTempRect2;
            getLastChildRect(child, oldRect);
            getChildRect(child, true, newRect);
            if (oldRect.equals(newRect)) {
                continue;
            }
            recordLastChildRect(child, newRect);
            // Update any behavior-dependent views for the change
            for (int j = i + 1; j < childCount; j++) {
                final View checkChild = mDependencySortedChildren.get(j);
                final LayoutParams checkLp = (LayoutParams) checkChild.getLayoutParams();
                final Behavior b = checkLp.getBehavior();
                if (b != null && b.layoutDependsOn(this, checkChild, child)) {
                    if (!fromNestedScroll && checkLp.getChangedAfterNestedScroll()) {
                        // If this is not from a nested scroll and we have already been changed
                        // from a nested scroll, skip the dispatch and reset the flag
                        checkLp.resetChangedAfterNestedScroll();
                        continue;
                    }
                    final boolean handled = b.onDependentViewChanged(this, checkChild, child);
                    if (fromNestedScroll) {
                        // If this is from a nested scroll, set the flag so that we may skip
                        // any resulting onPreDraw dispatch (if needed)
                        checkLp.setChangedAfterNestedScroll(handled);
                    }
                }
            }
        }
    }

    /**
     * Allows the caller to manually dispatch
     * {@link Behavior#onDependentViewChanged(CoordinatorLayout, View, View)} to the associated
     * {@link Behavior} instances of views which depend on the provided {@link View}.
     * <p>
     * <p>You should not normally need to call this method as the it will be automatically done
     * when the view has changed.
     *
     * @param view the View to find dependents of to dispatch the call.
     */
    public void dispatchDependentViewsChanged(View view) {
        final int childCount = mDependencySortedChildren.size();
        boolean viewSeen = false;
        for (int i = 0; i < childCount; i++) {
            final View child = mDependencySortedChildren.get(i);
            if (child == view) {
                // We've seen our view, which means that any Views after this could be dependent
                viewSeen = true;
                continue;
            }
            if (viewSeen) {
                LayoutParams lp = (LayoutParams)
                        child.getLayoutParams();
                Behavior b = lp.getBehavior();
                if (b != null && lp.dependsOn(this, child, view)) {
                    b.onDependentViewChanged(this, child, view);
                }
            }
        }
    }

    /**
     * Returns the list of views which the provided view depends on. Do not store this list as it's
     * contents may not be valid beyond the caller.
     *
     * @param child the view to find dependencies for.
     * @return the list of views which {@code child} depends on.
     */
    public List<View> getDependencies(View child) {
        // TODO The result of this is probably a good candidate for caching
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final List<View> list = mTempDependenciesList;
        list.clear();
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View other = getChildAt(i);
            if (other == child) {
                continue;
            }
            if (lp.dependsOn(this, child, other)) {
                list.add(other);
            }
        }
        return list;
    }

    /**
     * Add or remove the pre-draw listener as necessary.
     */
    void ensurePreDrawListener() {
        boolean hasDependencies = false;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (hasDependencies(child)) {
                hasDependencies = true;
                break;
            }
        }
        if (hasDependencies != mNeedsPreDrawListener) {
            if (hasDependencies) {
                addPreDrawListener();
            } else {
                removePreDrawListener();
            }
        }
    }

    /**
     * Check if the given child has any layout dependencies on other child views.
     */
    boolean hasDependencies(View child) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp.mAnchorView != null) {
            return true;
        }
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View other = getChildAt(i);
            if (other == child) {
                continue;
            }
            if (lp.dependsOn(this, child, other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add the pre-draw listener if we're attached to a window and mark that we currently
     * need it when attached.
     */
    void addPreDrawListener() {
        if (mIsAttachedToWindow) {
            // Add the listener
            if (mOnPreDrawListener == null) {
                mOnPreDrawListener = new OnPreDrawListener();
            }
            final ViewTreeObserver vto = getViewTreeObserver();
            vto.addOnPreDrawListener(mOnPreDrawListener);
        }
        // Record that we need the listener regardless of whether or not we're attached.
        // We'll add the real listener when we become attached.
        mNeedsPreDrawListener = true;
    }

    /**
     * Remove the pre-draw listener if we're attached to a window and mark that we currently
     * do not need it when attached.
     */
    void removePreDrawListener() {
        if (mIsAttachedToWindow) {
            if (mOnPreDrawListener != null) {
                final ViewTreeObserver vto = getViewTreeObserver();
                vto.removeOnPreDrawListener(mOnPreDrawListener);
            }
        }
        mNeedsPreDrawListener = false;
    }

    /**
     * Adjust the child left, top, right, bottom rect to the correct anchor view position,
     * respecting gravity and anchor gravity.
     * <p>
     * Note that child translation properties are ignored in this process, allowing children
     * to be animated away from their anchor. However, if the anchor view is animated,
     * the child will be offset to match the anchor's translated position.
     */
    void offsetChildToAnchor(View child, int layoutDirection) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp.mAnchorView != null) {
            final Rect anchorRect = mTempRect1;
            final Rect childRect = mTempRect2;
            final Rect desiredChildRect = mTempRect3;
            getDescendantRect(lp.mAnchorView, anchorRect);
            getChildRect(child, false, childRect);
            getDesiredAnchoredChildRect(child, layoutDirection, anchorRect, desiredChildRect);
            final int dx = desiredChildRect.left - childRect.left;
            final int dy = desiredChildRect.top - childRect.top;
            if (dx != 0) {
                child.offsetLeftAndRight(dx);
            }
            if (dy != 0) {
                child.offsetTopAndBottom(dy);
            }
            if (dx != 0 || dy != 0) {
                // If we have needed to move, make sure to notify the child's Behavior
                final Behavior b = lp.getBehavior();
                if (b != null) {
                    b.onDependentViewChanged(this, child, lp.mAnchorView);
                }
            }
        }
    }

    /**
     * Check if a given point in the CoordinatorLayout's coordinates are within the view bounds
     * of the given direct child view.
     *
     * @param child child view to test
     * @param x     X coordinate to test, in the CoordinatorLayout's coordinate system
     * @param y     Y coordinate to test, in the CoordinatorLayout's coordinate system
     * @return true if the point is within the child view's bounds, false otherwise
     */
    public boolean isPointInChildBounds(View child, int x, int y) {
        final Rect r = mTempRect1;
        getDescendantRect(child, r);
        return r.contains(x, y);
    }

    /**
     * Check whether two views overlap each other. The views need to be descendants of this
     * {@link CoordinatorLayout} in the view hierarchy.
     *
     * @param first  first child view to test
     * @param second second child view to test
     * @return true if both views are visible and overlap each other
     */
    public boolean doViewsOverlap(View first, View second) {
        if (first.getVisibility() == VISIBLE && second.getVisibility() == VISIBLE) {
            final Rect firstRect = mTempRect1;
            getChildRect(first, first.getParent() != this, firstRect);
            final Rect secondRect = mTempRect2;
            getChildRect(second, second.getParent() != this, secondRect);
            return !(firstRect.left > secondRect.right || firstRect.top > secondRect.bottom
                    || firstRect.right < secondRect.left || firstRect.bottom < secondRect.top);
        }
        return false;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        if (p instanceof LayoutParams) {
            return new LayoutParams((LayoutParams) p);
        } else if (p instanceof MarginLayoutParams) {
            return new LayoutParams((MarginLayoutParams) p);
        }
        return new LayoutParams(p);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        boolean handled = false;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View view = getChildAt(i);
            final LayoutParams lp = (LayoutParams) view.getLayoutParams();
            final Behavior viewBehavior = lp.getBehavior();
            if (viewBehavior != null) {
                final boolean accepted = viewBehavior.onStartNestedScroll(this, view, child, target,
                        nestedScrollAxes);
                handled |= accepted;
                lp.acceptNestedScroll(accepted);
            } else {
                lp.acceptNestedScroll(false);
            }
        }
        return handled;
    }

    public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes);
        mNestedScrollingDirectChild = child;
        mNestedScrollingTarget = target;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View view = getChildAt(i);
            final LayoutParams lp = (LayoutParams) view.getLayoutParams();
            if (!lp.isNestedScrollAccepted()) {
                continue;
            }
            final Behavior viewBehavior = lp.getBehavior();
            if (viewBehavior != null) {
                viewBehavior.onNestedScrollAccepted(this, view, child, target, nestedScrollAxes);
            }
        }
    }

    public void onStopNestedScroll(View target) {
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View view = getChildAt(i);
            final LayoutParams lp = (LayoutParams) view.getLayoutParams();
            if (!lp.isNestedScrollAccepted()) {
                continue;
            }
            final Behavior viewBehavior = lp.getBehavior();
            if (viewBehavior != null) {
                viewBehavior.onStopNestedScroll(this, view, target);
            }
            lp.resetNestedScroll();
            lp.resetChangedAfterNestedScroll();
        }
        mNestedScrollingDirectChild = null;
        mNestedScrollingTarget = null;
    }

    public void onNestedScroll(View target, int dxConsumed, int dyConsumed,
                               int dxUnconsumed, int dyUnconsumed) {
        final int childCount = getChildCount();
        boolean accepted = false;
        for (int i = 0; i < childCount; i++) {
            final View view = getChildAt(i);
            final LayoutParams lp = (LayoutParams) view.getLayoutParams();
            if (!lp.isNestedScrollAccepted()) {
                continue;
            }
            final Behavior viewBehavior = lp.getBehavior();
            if (viewBehavior != null) {
                viewBehavior.onNestedScroll(this, view, target, dxConsumed, dyConsumed,
                        dxUnconsumed, dyUnconsumed);
                accepted = true;
            }
        }
        if (accepted) {
            dispatchOnDependentViewChanged(true);
        }
    }

    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        int xConsumed = 0;
        int yConsumed = 0;
        boolean accepted = false;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View view = getChildAt(i);
            final LayoutParams lp = (LayoutParams) view.getLayoutParams();
            if (!lp.isNestedScrollAccepted()) {
                continue;
            }
            final Behavior viewBehavior = lp.getBehavior();
            if (viewBehavior != null) {
                mTempIntPair[0] = mTempIntPair[1] = 0;
                viewBehavior.onNestedPreScroll(this, view, target, dx, dy, mTempIntPair);
                xConsumed = dx > 0 ? Math.max(xConsumed, mTempIntPair[0])
                        : Math.min(xConsumed, mTempIntPair[0]);
                yConsumed = dy > 0 ? Math.max(yConsumed, mTempIntPair[1])
                        : Math.min(yConsumed, mTempIntPair[1]);
                accepted = true;
            }
        }
        consumed[0] = xConsumed;
        consumed[1] = yConsumed;
        if (accepted) {
            dispatchOnDependentViewChanged(true);
        }
    }

    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        boolean handled = false;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View view = getChildAt(i);
            final LayoutParams lp = (LayoutParams) view.getLayoutParams();
            if (!lp.isNestedScrollAccepted()) {
                continue;
            }
            final Behavior viewBehavior = lp.getBehavior();
            if (viewBehavior != null) {
                handled |= viewBehavior.onNestedFling(this, view, target, velocityX, velocityY,
                        consumed);
            }
        }
        if (handled) {
            dispatchOnDependentViewChanged(true);
        }
        return handled;
    }

    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        boolean handled = false;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View view = getChildAt(i);
            final LayoutParams lp = (LayoutParams) view.getLayoutParams();
            if (!lp.isNestedScrollAccepted()) {
                continue;
            }
            final Behavior viewBehavior = lp.getBehavior();
            if (viewBehavior != null) {
                handled |= viewBehavior.onNestedPreFling(this, view, target, velocityX, velocityY);
            }
        }
        return handled;
    }

    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    class OnPreDrawListener implements ViewTreeObserver.OnPreDrawListener {
        @Override
        public boolean onPreDraw() {
            dispatchOnDependentViewChanged(false);
            return true;
        }
    }

    /**
     * Sorts child views com higher Z values to the beginning of a collection.
     */
    static class ViewElevationComparator implements Comparator<View> {
        @Override
        public int compare(View lhs, View rhs) {
            final float lz = ViewCompat.getZ(lhs);
            final float rz = ViewCompat.getZ(rhs);
            if (lz > rz) {
                return -1;
            } else if (lz < rz) {
                return 1;
            }
            return 0;
        }
    }

    /**
     * Defines the default {@link Behavior} of a {@link View} class.
     * <p>
     * <p>When writing a custom view, use this annotation to define the default behavior
     * when used as a direct child of an {@link CoordinatorLayout}. The default behavior
     * can be overridden using {@link LayoutParams#setBehavior}.</p>
     * <p>
     * <p>Example: <code>@DefaultBehavior(MyBehavior.class)</code></p>
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface DefaultBehavior {
        Class<? extends Behavior> value();
    }

    /**
     * Interaction behavior plugin for child views of {@link CoordinatorLayout}.
     * <p>
     * <p>A Behavior implements one or more interactions that a user can take on a child view.
     * These interactions may include drags, swipes, flings, or any other gestures.</p>
     *
     * @param <V> The View type that this Behavior operates on
     */
    public static abstract class Behavior<V extends View> {
        /**
         * Default constructor for instantiating Behaviors.
         */
        public Behavior() {
        }

        /**
         * Default constructor for inflating Behaviors from layout. The Behavior will have
         * the opportunity to parse specially defined layout parameters. These parameters will
         * appear on the child view tag.
         *
         * @param context
         * @param attrs
         */
        public Behavior(Context context, AttributeSet attrs) {
        }

        /**
         * Respond to CoordinatorLayout touch events before they are dispatched to child views.
         * <p>
         * <p>Behaviors can use this to monitor inbound touch events until one decides to
         * intercept the rest of the event stream to take an action on its associated child view.
         * This method will return false until it detects the proper intercept conditions, then
         * return true once those conditions have occurred.</p>
         * <p>
         * <p>Once a Behavior intercepts touch events, the rest of the event stream will
         * be sent to the {@link #onTouchEvent} method.</p>
         * <p>
         * <p>The default implementation of this method always returns false.</p>
         *
         * @param parent the parent view currently receiving this touch event
         * @param child  the child view associated com this Behavior
         * @param ev     the MotionEvent describing the touch event being processed
         * @return true if this Behavior would like to intercept and take over the event stream.
         * The default always returns false.
         */
        public boolean onInterceptTouchEvent(CoordinatorLayout parent, V child, MotionEvent ev) {
            return false;
        }

        /**
         * Respond to CoordinatorLayout touch events after this Behavior has started
         * {@link #onInterceptTouchEvent intercepting} them.
         * <p>
         * <p>Behaviors may intercept touch events in order to help the CoordinatorLayout
         * manipulate its child views. For example, a Behavior may allow a user to drag a
         * UI pane open or closed. This method should perform actual mutations of view
         * layout state.</p>
         *
         * @param parent the parent view currently receiving this touch event
         * @param child  the child view associated com this Behavior
         * @param ev     the MotionEvent describing the touch event being processed
         * @return true if this Behavior handled this touch event and would like to continue
         * receiving events in this stream. The default always returns false.
         */
        public boolean onTouchEvent(CoordinatorLayout parent, V child, MotionEvent ev) {
            return false;
        }

        /**
         * Supply a scrim color that will be painted behind the associated child view.
         * <p>
         * <p>A scrim may be used to indicate that the other elements beneath it are not currently
         * interactive or actionable, drawing user focus and attention to the views above the scrim.
         * </p>
         * <p>
         * <p>The default implementation returns {@link Color#BLACK}.</p>
         *
         * @param parent the parent view of the given child
         * @param child  the child view above the scrim
         * @return the desired scrim color in 0xAARRGGBB format. The default return value is
         * {@link Color#BLACK}.
         * @see #getScrimOpacity(CoordinatorLayout, View)
         */
        public final int getScrimColor(CoordinatorLayout parent, V child) {
            return Color.BLACK;
        }

        /**
         * Determine the current opacity of the scrim behind a given child view
         * <p>
         * <p>A scrim may be used to indicate that the other elements beneath it are not currently
         * interactive or actionable, drawing user focus and attention to the views above the scrim.
         * </p>
         * <p>
         * <p>The default implementation returns 0.0f.</p>
         *
         * @param parent the parent view of the given child
         * @param child  the child view above the scrim
         * @return the desired scrim opacity from 0.0f to 1.0f. The default return value is 0.0f.
         */
        public final float getScrimOpacity(CoordinatorLayout parent, V child) {
            return 0.f;
        }

        /**
         * Determine whether interaction com views behind the given child in the child order
         * should be blocked.
         * <p>
         * <p>The default implementation returns true if
         * {@link #getScrimOpacity(CoordinatorLayout, View)} would return > 0.0f.</p>
         *
         * @param parent the parent view of the given child
         * @param child  the child view to test
         * @return true if {@link #getScrimOpacity(CoordinatorLayout, View)} would
         * return > 0.0f.
         */
        public boolean blocksInteractionBelow(CoordinatorLayout parent, V child) {
            return getScrimOpacity(parent, child) > 0.f;
        }

        /**
         * Determine whether the supplied child view has another specific sibling view as a
         * layout dependency.
         * <p>
         * <p>This method will be called at least once in response to a layout request. If it
         * returns true for a given child and dependency view pair, the parent CoordinatorLayout
         * will:</p>
         * <ol>
         * <li>Always lay out this child after the dependent child is laid out, regardless
         * of child order.</li>
         * <li>Call {@link #onDependentViewChanged} when the dependency view's layout or
         * position changes.</li>
         * </ol>
         *
         * @param parent     the parent view of the given child
         * @param child      the child view to test
         * @param dependency the proposed dependency of child
         * @return true if child's layout depends on the proposed dependency's layout,
         * false otherwise
         * @see #onDependentViewChanged(CoordinatorLayout, View, View)
         */
        public boolean layoutDependsOn(CoordinatorLayout parent, V child, View dependency) {
            return false;
        }

        /**
         * Respond to a change in a child's dependent view
         * <p>
         * <p>This method is called whenever a dependent view changes in size or position outside
         * of the standard layout flow. A Behavior may use this method to appropriately update
         * the child view in response.</p>
         * <p>
         * <p>A view's dependency is determined by
         * {@link #layoutDependsOn(CoordinatorLayout, View, View)} or
         * if {@code child} has set another view as it's anchor.</p>
         * <p>
         * <p>Note that if a Behavior changes the layout of a child via this method, it should
         * also be able to reconstruct the correct position in
         * {@link #onLayoutChild(CoordinatorLayout, View, int) onLayoutChild}.
         * <code>onDependentViewChanged</code> will not be called during normal layout since
         * the layout of each child view will always happen in dependency order.</p>
         * <p>
         * <p>If the Behavior changes the child view's size or position, it should return true.
         * The default implementation returns false.</p>
         *
         * @param parent     the parent view of the given child
         * @param child      the child view to manipulate
         * @param dependency the dependent view that changed
         * @return true if the Behavior changed the child view's size or position, false otherwise
         */
        public boolean onDependentViewChanged(CoordinatorLayout parent, V child, View dependency) {
            return false;
        }

        /**
         * Determine whether the given child view should be considered dirty.
         * <p>
         * <p>If a property determined by the Behavior such as other dependent views would change,
         * the Behavior should report a child view as dirty. This will prompt the CoordinatorLayout
         * to re-query Behavior-determined properties as appropriate.</p>
         *
         * @param parent the parent view of the given child
         * @param child  the child view to check
         * @return true if child is dirty
         */
        public boolean isDirty(CoordinatorLayout parent, V child) {
            return false;
        }

        /**
         * Called when the parent CoordinatorLayout is about to measure the given child view.
         * <p>
         * <p>This method can be used to perform custom or modified measurement of a child view
         * in place of the default child measurement behavior. The Behavior's implementation
         * can delegate to the standard CoordinatorLayout measurement behavior by calling
         * {@link CoordinatorLayout#onMeasureChild(View, int, int, int, int)
         * parent.onMeasureChild}.</p>
         *
         * @param parent                  the parent CoordinatorLayout
         * @param child                   the child to measure
         * @param parentWidthMeasureSpec  the width requirements for this view
         * @param widthUsed               extra space that has been used up by the parent
         *                                horizontally (possibly by other children of the parent)
         * @param parentHeightMeasureSpec the height requirements for this view
         * @param heightUsed              extra space that has been used up by the parent
         *                                vertically (possibly by other children of the parent)
         * @return true if the Behavior measured the child view, false if the CoordinatorLayout
         * should perform its default measurement
         */
        public boolean onMeasureChild(CoordinatorLayout parent, V child,
                                      int parentWidthMeasureSpec, int widthUsed,
                                      int parentHeightMeasureSpec, int heightUsed) {
            return false;
        }

        /**
         * Called when the parent CoordinatorLayout is about the lay out the given child view.
         * <p>
         * <p>This method can be used to perform custom or modified layout of a child view
         * in place of the default child layout behavior. The Behavior's implementation can
         * delegate to the standard CoordinatorLayout measurement behavior by calling
         * {@link CoordinatorLayout#onLayoutChild(View, int)
         * parent.onMeasureChild}.</p>
         * <p>
         * <p>If a Behavior implements
         * {@link #onDependentViewChanged(CoordinatorLayout, View, View)}
         * to change the position of a view in response to a dependent view changing, it
         * should also implement <code>onLayoutChild</code> in such a way that respects those
         * dependent views. <code>onLayoutChild</code> will always be called for a dependent view
         * <em>after</em> its dependency has been laid out.</p>
         *
         * @param parent          the parent CoordinatorLayout
         * @param child           child view to lay out
         * @param layoutDirection the resolved layout direction for the CoordinatorLayout, such as
         *                        {@link ViewCompat#LAYOUT_DIRECTION_LTR} or
         *                        {@link ViewCompat#LAYOUT_DIRECTION_RTL}.
         * @return true if the Behavior performed layout of the child view, false to request
         * default layout behavior
         */
        public boolean onLayoutChild(CoordinatorLayout parent, V child, int layoutDirection) {
            return false;
        }
        // Utility methods for accessing child-specific, behavior-modifiable properties.

        /**
         * Associate a Behavior-specific tag object com the given child view.
         * This object will be stored com the child view's LayoutParams.
         *
         * @param child child view to set tag com
         * @param tag   tag object to set
         */
        public static void setTag(View child, Object tag) {
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.mBehaviorTag = tag;
        }

        /**
         * Get the behavior-specific tag object com the given child view.
         * This object is stored com the child view's LayoutParams.
         *
         * @param child child view to get tag com
         * @return the previously stored tag object
         */
        public static Object getTag(View child) {
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            return lp.mBehaviorTag;
        }

        /**
         * Called when a descendant of the CoordinatorLayout attempts to initiate a nested scroll.
         * <p>
         * <p>Any Behavior associated com any direct child of the CoordinatorLayout may respond
         * to this event and return true to indicate that the CoordinatorLayout should act as
         * a nested scrolling parent for this scroll. Only Behaviors that return true from
         * this method will receive subsequent nested scroll events.</p>
         *
         * @param coordinatorLayout the CoordinatorLayout parent of the view this Behavior is
         *                          associated com
         * @param child             the child view of the CoordinatorLayout this Behavior is associated com
         * @param directTargetChild the child view of the CoordinatorLayout that either is or
         *                          contains the target of the nested scroll operation
         * @param target            the descendant view of the CoordinatorLayout initiating the nested scroll
         * @param nestedScrollAxes  the axes that this nested scroll applies to. See
         *                          {@link ViewCompat#SCROLL_AXIS_HORIZONTAL},
         *                          {@link ViewCompat#SCROLL_AXIS_VERTICAL}
         * @return true if the Behavior wishes to accept this nested scroll
         * @see NestedScrollingParent#onStartNestedScroll(View, View, int)
         */
        public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout,
                                           V child, View directTargetChild, View target, int nestedScrollAxes) {
            return false;
        }

        /**
         * Called when a nested scroll has been accepted by the CoordinatorLayout.
         * <p>
         * <p>Any Behavior associated com any direct child of the CoordinatorLayout may elect
         * to accept the nested scroll as part of {@link #onStartNestedScroll}. Each Behavior
         * that returned true will receive subsequent nested scroll events for that nested scroll.
         * </p>
         *
         * @param coordinatorLayout the CoordinatorLayout parent of the view this Behavior is
         *                          associated com
         * @param child             the child view of the CoordinatorLayout this Behavior is associated com
         * @param directTargetChild the child view of the CoordinatorLayout that either is or
         *                          contains the target of the nested scroll operation
         * @param target            the descendant view of the CoordinatorLayout initiating the nested scroll
         * @param nestedScrollAxes  the axes that this nested scroll applies to. See
         *                          {@link ViewCompat#SCROLL_AXIS_HORIZONTAL},
         *                          {@link ViewCompat#SCROLL_AXIS_VERTICAL}
         * @see NestedScrollingParent#onNestedScrollAccepted(View, View, int)
         */
        public void onNestedScrollAccepted(CoordinatorLayout coordinatorLayout, V child,
                                           View directTargetChild, View target, int nestedScrollAxes) {
            // Do nothing
        }

        /**
         * Called when a nested scroll has ended.
         * <p>
         * <p>Any Behavior associated com any direct child of the CoordinatorLayout may elect
         * to accept the nested scroll as part of {@link #onStartNestedScroll}. Each Behavior
         * that returned true will receive subsequent nested scroll events for that nested scroll.
         * </p>
         * <p>
         * <p><code>onStopNestedScroll</code> marks the end of a single nested scroll event
         * sequence. This is a good place to clean up any state related to the nested scroll.
         * </p>
         *
         * @param coordinatorLayout the CoordinatorLayout parent of the view this Behavior is
         *                          associated com
         * @param child             the child view of the CoordinatorLayout this Behavior is associated com
         * @param target            the descendant view of the CoordinatorLayout that initiated
         *                          the nested scroll
         * @see NestedScrollingParent#onStopNestedScroll(View)
         */
        public void onStopNestedScroll(CoordinatorLayout coordinatorLayout, V child, View target) {
            // Do nothing
        }

        /**
         * Called when a nested scroll in progress has updated and the target has scrolled or
         * attempted to scroll.
         * <p>
         * <p>Any Behavior associated com the direct child of the CoordinatorLayout may elect
         * to accept the nested scroll as part of {@link #onStartNestedScroll}. Each Behavior
         * that returned true will receive subsequent nested scroll events for that nested scroll.
         * </p>
         * <p>
         * <p><code>onNestedScroll</code> is called each time the nested scroll is updated by the
         * nested scrolling child, com both consumed and unconsumed components of the scroll
         * supplied in pixels. <em>Each Behavior responding to the nested scroll will receive the
         * same values.</em>
         * </p>
         *
         * @param coordinatorLayout the CoordinatorLayout parent of the view this Behavior is
         *                          associated com
         * @param child             the child view of the CoordinatorLayout this Behavior is associated com
         * @param target            the descendant view of the CoordinatorLayout performing the nested scroll
         * @param dxConsumed        horizontal pixels consumed by the target's own scrolling operation
         * @param dyConsumed        vertical pixels consumed by the target's own scrolling operation
         * @param dxUnconsumed      horizontal pixels not consumed by the target's own scrolling
         *                          operation, but requested by the user
         * @param dyUnconsumed      vertical pixels not consumed by the target's own scrolling operation,
         *                          but requested by the user
         * @see NestedScrollingParent#onNestedScroll(View, int, int, int, int)
         */
        public void onNestedScroll(CoordinatorLayout coordinatorLayout, V child, View target,
                                   int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
            // Do nothing
        }

        /**
         * Called when a nested scroll in progress is about to update, before the target has
         * consumed any of the scrolled distance.
         * <p>
         * <p>Any Behavior associated com the direct child of the CoordinatorLayout may elect
         * to accept the nested scroll as part of {@link #onStartNestedScroll}. Each Behavior
         * that returned true will receive subsequent nested scroll events for that nested scroll.
         * </p>
         * <p>
         * <p><code>onNestedPreScroll</code> is called each time the nested scroll is updated
         * by the nested scrolling child, before the nested scrolling child has consumed the scroll
         * distance itself. <em>Each Behavior responding to the nested scroll will receive the
         * same values.</em> The CoordinatorLayout will report as consumed the maximum number
         * of pixels in either direction that any Behavior responding to the nested scroll reported
         * as consumed.</p>
         *
         * @param coordinatorLayout the CoordinatorLayout parent of the view this Behavior is
         *                          associated com
         * @param child             the child view of the CoordinatorLayout this Behavior is associated com
         * @param target            the descendant view of the CoordinatorLayout performing the nested scroll
         * @param dx                the raw horizontal number of pixels that the user attempted to scroll
         * @param dy                the raw vertical number of pixels that the user attempted to scroll
         * @param consumed          out parameter. consumed[0] should be set to the distance of dx that
         *                          was consumed, consumed[1] should be set to the distance of dy that
         *                          was consumed
         * @see NestedScrollingParent#onNestedPreScroll(View, int, int, int[])
         */
        public void onNestedPreScroll(CoordinatorLayout coordinatorLayout, V child, View target,
                                      int dx, int dy, int[] consumed) {
            // Do nothing
        }

        /**
         * Called when a nested scrolling child is starting a fling or an action that would
         * be a fling.
         * <p>
         * <p>Any Behavior associated com the direct child of the CoordinatorLayout may elect
         * to accept the nested scroll as part of {@link #onStartNestedScroll}. Each Behavior
         * that returned true will receive subsequent nested scroll events for that nested scroll.
         * </p>
         * <p>
         * <p><code>onNestedFling</code> is called when the current nested scrolling child view
         * detects the proper conditions for a fling. It reports if the child itself consumed
         * the fling. If it did not, the child is expected to show some sort of overscroll
         * indication. This method should return true if it consumes the fling, so that a child
         * that did not itself take an action in response can choose not to show an overfling
         * indication.</p>
         *
         * @param coordinatorLayout the CoordinatorLayout parent of the view this Behavior is
         *                          associated com
         * @param child             the child view of the CoordinatorLayout this Behavior is associated com
         * @param target            the descendant view of the CoordinatorLayout performing the nested scroll
         * @param velocityX         horizontal velocity of the attempted fling
         * @param velocityY         vertical velocity of the attempted fling
         * @param consumed          true if the nested child view consumed the fling
         * @return true if the Behavior consumed the fling
         * @see NestedScrollingParent#onNestedFling(View, float, float, boolean)
         */
        public boolean onNestedFling(CoordinatorLayout coordinatorLayout, V child, View target,
                                     float velocityX, float velocityY, boolean consumed) {
            return false;
        }

        /**
         * Called when a nested scrolling child is about to start a fling.
         * <p>
         * <p>Any Behavior associated com the direct child of the CoordinatorLayout may elect
         * to accept the nested scroll as part of {@link #onStartNestedScroll}. Each Behavior
         * that returned true will receive subsequent nested scroll events for that nested scroll.
         * </p>
         * <p>
         * <p><code>onNestedPreFling</code> is called when the current nested scrolling child view
         * detects the proper conditions for a fling, but it has not acted on it yet. A
         * Behavior can return true to indicate that it consumed the fling. If at least one
         * Behavior returns true, the fling should not be acted upon by the child.</p>
         *
         * @param coordinatorLayout the CoordinatorLayout parent of the view this Behavior is
         *                          associated com
         * @param child             the child view of the CoordinatorLayout this Behavior is associated com
         * @param target            the descendant view of the CoordinatorLayout performing the nested scroll
         * @param velocityX         horizontal velocity of the attempted fling
         * @param velocityY         vertical velocity of the attempted fling
         * @return true if the Behavior consumed the fling
         * @see NestedScrollingParent#onNestedPreFling(View, float, float)
         */
        public boolean onNestedPreFling(CoordinatorLayout coordinatorLayout, V child, View target,
                                        float velocityX, float velocityY) {
            return false;
        }
    }

    /**
     * Parameters describing the desired layout for a child of a {@link CoordinatorLayout}.
     */
    public static class LayoutParams extends MarginLayoutParams {
        /**
         * A {@link Behavior} that the child view should obey.
         */
        Behavior mBehavior;
        boolean mBehaviorResolved = false;
        /**
         * A {@link Gravity} value describing how this child view should lay out.
         * If an {@link #setAnchorId(int) anchor} is also specified, the gravity describes
         * how this child view should be positioned relative to its anchored position.
         */
        public int gravity = Gravity.NO_GRAVITY;
        /**
         * A {@link Gravity} value describing which edge of a child view's
         * {@link #getAnchorId() anchor} view the child should position itself relative to.
         */
        public int anchorGravity = Gravity.NO_GRAVITY;
        /**
         * The index of the horizontal keyline specified to the parent CoordinatorLayout that this
         * child should align relative to. If an {@link #setAnchorId(int) anchor} is present the
         * keyline will be ignored.
         */
        public int keyline = -1;
        /**
         * A {@link View#getId() view id} of a descendant view of the CoordinatorLayout that
         * this child should position relative to.
         */
        int mAnchorId = View.NO_ID;
        View mAnchorView;
        View mAnchorDirectChild;
        private boolean mDidBlockInteraction;
        private boolean mDidAcceptNestedScroll;
        private boolean mDidChangeAfterNestedScroll;
        final Rect mLastChildRect = new Rect();
        Object mBehaviorTag;

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        LayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);
            final TypedArray a = context.obtainStyledAttributes(attrs,
                    R.styleable.CoordinatorLayout_LayoutParams);
            this.gravity = a.getInteger(
                    R.styleable.CoordinatorLayout_LayoutParams_android_layout_gravity,
                    Gravity.NO_GRAVITY);
            mAnchorId = a.getResourceId(R.styleable.CoordinatorLayout_LayoutParams_layout_anchor,
                    View.NO_ID);
            this.anchorGravity = a.getInteger(
                    R.styleable.CoordinatorLayout_LayoutParams_layout_anchorGravityy,
                    Gravity.NO_GRAVITY);
            this.keyline = a.getInteger(R.styleable.CoordinatorLayout_LayoutParams_layout_keyline,
                    -1);
            mBehaviorResolved = a.hasValue(
                    R.styleable.CoordinatorLayout_LayoutParams_layout_behavior);
            if (mBehaviorResolved) {
                mBehavior = parseBehavior(context, attrs, a.getString(
                        R.styleable.CoordinatorLayout_LayoutParams_layout_behavior));
            }
            a.recycle();
        }

        public LayoutParams(LayoutParams p) {
            super(p);
        }

        public LayoutParams(MarginLayoutParams p) {
            super(p);
        }

        public LayoutParams(ViewGroup.LayoutParams p) {
            super(p);
        }

        /**
         * Get the id of this view's anchor.
         * <p>
         * <p>The view com this id must be a descendant of the CoordinatorLayout containing
         * the child view this LayoutParams belongs to. It may not be the child view com
         * this LayoutParams or a descendant of it.</p>
         *
         * @return A {@link View#getId() view id} or {@link View#NO_ID} if there is no anchor
         */
        public int getAnchorId() {
            return mAnchorId;
        }

        /**
         * Get the id of this view's anchor.
         * <p>
         * <p>The view com this id must be a descendant of the CoordinatorLayout containing
         * the child view this LayoutParams belongs to. It may not be the child view com
         * this LayoutParams or a descendant of it.</p>
         *
         * @param id The {@link View#getId() view id} of the anchor or
         *           {@link View#NO_ID} if there is no anchor
         */
        public void setAnchorId(int id) {
            invalidateAnchor();
            mAnchorId = id;
        }

        /**
         * Get the behavior governing the layout and interaction of the child view within
         * a parent CoordinatorLayout.
         *
         * @return The current behavior or null if no behavior is specified
         */
        public Behavior getBehavior() {
            return mBehavior;
        }

        /**
         * Set the behavior governing the layout and interaction of the child view within
         * a parent CoordinatorLayout.
         * <p>
         * <p>Setting a new behavior will remove any currently associated
         * {@link Behavior#setTag(View, Object) Behavior tag}.</p>
         *
         * @param behavior The behavior to set or null for no special behavior
         */
        public void setBehavior(Behavior behavior) {
            if (mBehavior != behavior) {
                mBehavior = behavior;
                mBehaviorTag = null;
                mBehaviorResolved = true;
            }
        }

        /**
         * Set the last known position rect for this child view
         *
         * @param r the rect to set
         */
        void setLastChildRect(Rect r) {
            mLastChildRect.set(r);
        }

        /**
         * Get the last known position rect for this child view.
         * Note: do not mutate the result of this call.
         */
        Rect getLastChildRect() {
            return mLastChildRect;
        }

        /**
         * Returns true if the anchor id changed to another valid view id since the anchor view
         * was resolved.
         */
        boolean checkAnchorChanged() {
            return mAnchorView == null && mAnchorId != View.NO_ID;
        }

        /**
         * Returns true if the associated Behavior previously blocked interaction com other views
         * below the associated child since the touch behavior tracking was last
         * {@link #resetTouchBehaviorTracking() reset}.
         *
         * @see #isBlockingInteractionBelow(CoordinatorLayout, View)
         */
        boolean didBlockInteraction() {
            if (mBehavior == null) {
                mDidBlockInteraction = false;
            }
            return mDidBlockInteraction;
        }

        /**
         * Check if the associated Behavior wants to block interaction below the given child
         * view. The given child view should be the child this LayoutParams is associated com.
         * <p>
         * <p>Once interaction is blocked, it will remain blocked until touch interaction tracking
         * is {@link #resetTouchBehaviorTracking() reset}.</p>
         *
         * @param parent the parent CoordinatorLayout
         * @param child  the child view this LayoutParams is associated com
         * @return true to block interaction below the given child
         */
        boolean isBlockingInteractionBelow(CoordinatorLayout parent, View child) {
            if (mDidBlockInteraction) {
                return true;
            }
            return mDidBlockInteraction |= mBehavior != null
                    ? mBehavior.blocksInteractionBelow(parent, child)
                    : false;
        }

        /**
         * Reset tracking of Behavior-specific touch interactions. This includes
         * interaction blocking.
         *
         * @see #isBlockingInteractionBelow(CoordinatorLayout, View)
         * @see #didBlockInteraction()
         */
        void resetTouchBehaviorTracking() {
            mDidBlockInteraction = false;
        }

        void resetNestedScroll() {
            mDidAcceptNestedScroll = false;
        }

        void acceptNestedScroll(boolean accept) {
            mDidAcceptNestedScroll = accept;
        }

        boolean isNestedScrollAccepted() {
            return mDidAcceptNestedScroll;
        }

        boolean getChangedAfterNestedScroll() {
            return mDidChangeAfterNestedScroll;
        }

        void setChangedAfterNestedScroll(boolean changed) {
            mDidChangeAfterNestedScroll = changed;
        }

        void resetChangedAfterNestedScroll() {
            mDidChangeAfterNestedScroll = false;
        }

        /**
         * Check if an associated child view depends on another child view of the CoordinatorLayout.
         *
         * @param parent     the parent CoordinatorLayout
         * @param child      the child to check
         * @param dependency the proposed dependency to check
         * @return true if child depends on dependency
         */
        boolean dependsOn(CoordinatorLayout parent, View child, View dependency) {
            return dependency == mAnchorDirectChild
                    || (mBehavior != null && mBehavior.layoutDependsOn(parent, child, dependency));
        }

        /**
         * Invalidate the cached anchor view and direct child ancestor of that anchor.
         * The anchor will need to be
         * {@link #findAnchorView(CoordinatorLayout, View) found} before
         * being used again.
         */
        void invalidateAnchor() {
            mAnchorView = mAnchorDirectChild = null;
        }

        /**
         * Locate the appropriate anchor view by the current {@link #setAnchorId(int) anchor id}
         * or return the cached anchor view if already known.
         *
         * @param parent   the parent CoordinatorLayout
         * @param forChild the child this LayoutParams is associated com
         * @return the located descendant anchor view, or null if the anchor id is
         * {@link View#NO_ID}.
         */
        View findAnchorView(CoordinatorLayout parent, View forChild) {
            if (mAnchorId == View.NO_ID) {
                mAnchorView = mAnchorDirectChild = null;
                return null;
            }
            if (mAnchorView == null || !verifyAnchorView(forChild, parent)) {
                resolveAnchorView(forChild, parent);
            }
            return mAnchorView;
        }

        /**
         * Check if the child associated com this LayoutParams is currently considered
         * "dirty" and needs to be updated. A Behavior should consider a child dirty
         * whenever a property returned by another Behavior method would have changed,
         * such as dependencies.
         *
         * @param parent the parent CoordinatorLayout
         * @param child  the child view associated com this LayoutParams
         * @return true if this child view should be considered dirty
         */
        boolean isDirty(CoordinatorLayout parent, View child) {
            return mBehavior != null && mBehavior.isDirty(parent, child);
        }

        /**
         * Determine the anchor view for the child view this LayoutParams is assigned to.
         * Assumes mAnchorId is valid.
         */
        private void resolveAnchorView(View forChild, CoordinatorLayout parent) {
            mAnchorView = parent.findViewById(mAnchorId);
            if (mAnchorView != null) {
                View directChild = mAnchorView;
                for (ViewParent p = mAnchorView.getParent();
                     p != parent && p != null;
                     p = p.getParent()) {
                    if (p == forChild) {
                        if (parent.isInEditMode()) {
                            mAnchorView = mAnchorDirectChild = null;
                            return;
                        }
                        throw new IllegalStateException(
                                "Anchor must not be a descendant of the anchored view");
                    }
                    if (p instanceof View) {
                        directChild = (View) p;
                    }
                }
                mAnchorDirectChild = directChild;
            } else {
                if (parent.isInEditMode()) {
                    mAnchorView = mAnchorDirectChild = null;
                    return;
                }
                throw new IllegalStateException("Could not find CoordinatorLayout descendant view"
                        + " com id " + parent.getResources().getResourceName(mAnchorId)
                        + " to anchor view " + forChild);
            }
        }

        /**
         * Verify that the previously resolved anchor view is still valid - that it is still
         * a descendant of the expected parent view, it is not the child this LayoutParams
         * is assigned to or a descendant of it, and it has the expected id.
         */
        private boolean verifyAnchorView(View forChild, CoordinatorLayout parent) {
            if (mAnchorView.getId() != mAnchorId) {
                return false;
            }
            View directChild = mAnchorView;
            for (ViewParent p = mAnchorView.getParent();
                 p != parent;
                 p = p.getParent()) {
                if (p == null || p == forChild) {
                    mAnchorView = mAnchorDirectChild = null;
                    return false;
                }
                if (p instanceof View) {
                    directChild = (View) p;
                }
            }
            mAnchorDirectChild = directChild;
            return true;
        }
    }
}