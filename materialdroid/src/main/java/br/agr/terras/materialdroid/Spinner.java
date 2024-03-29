package br.agr.terras.materialdroid;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListPopupWindow;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Spinner extends android.support.v7.widget.AppCompatSpinner implements ValueAnimator.AnimatorUpdateListener {

    private Context context;
    private TypedArray typedArray;
    private List<String> nomes;
    private List<Integer> valores;
    private List<Object> tags;
    private HintAdapter adapter;
    private SelectionListener selectionListener;
    private boolean enableLoading = false;
    private int positionSelected;

    public static final int DEFAULT_ARROW_WIDTH_DP = 12;

    private static final String TAG = Spinner.class.getSimpleName();

    //Paint objects
    private Paint paint;
    private TextPaint textPaint;
    private StaticLayout staticLayout;


    private Path selectorPath;
    private Point[] selectorPoints;

    //Inner padding = "Normal" android padding
    private int innerPaddingLeft;
    private int innerPaddingRight;
    private int innerPaddingTop;
    private int innerPaddingBottom;

    //Private padding to add space for FloatingLabel and ErrorLabel
    private int extraPaddingTop;
    private int extraPaddingBottom;

    //@see dimens.xml
    private int underlineTopSpacing;
    private int underlineBottomSpacing;
    private int errorLabelSpacing;
    private int floatingLabelTopSpacing;
    private int floatingLabelBottomSpacing;
    private int floatingLabelInsideSpacing;
    private int rightLeftSpinnerPadding;
    private int minContentHeight;

    //Properties about Error Label
    private int lastPosition;
    private ObjectAnimator errorLabelAnimator;
    private int errorLabelPosX;
    private int minNbErrorLines;
    private float currentNbErrorLines;


    //Properties about Floating Label (
    private float floatingLabelPercent;
    private ObjectAnimator floatingLabelAnimator;
    private boolean isSelected;
    private boolean floatingLabelVisible;
    private int baseAlpha;
    private boolean isFloatingLabelAnimationEnabled;


    //AttributeSet
    private int baseColor;
    private int highlightColor;
    private int errorColor;
    private int disabledColor;
    private CharSequence error;
    private CharSequence hint;
    private int hintColor;
    private int textColor;
    private CharSequence floatingLabelText;
    private int floatingLabelColor;
    private boolean multiline;
    private Typeface typeface;
    private boolean alignLabels;
    private float thickness;
    private float thicknessError;
    private int arrowColor;
    private float arrowSize;
    private boolean enableErrorLabel;
    private boolean enableFloatingLabel;


    /*
    * **********************************************************************************
    * CONSTRUCTORS
    * **********************************************************************************
    */

    public Spinner(Context context) {
        super(context);
        init(context, null);
    }

    public Spinner(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);

    }

    public Spinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    /*
    * **********************************************************************************
    * INITIALISATION METHODS
    * **********************************************************************************
    */

    private void init(Context context, AttributeSet attrs) {
        this.context = context;
        initAttributes(context, attrs);
        initPaintObjects();
        initDimensions();
        initPadding();
        initFloatingLabelAnimator();
        initOnItemSelectedListener();
        setMinimumHeight(getPaddingTop() + getPaddingBottom() + minContentHeight);
        //Erase the drawable selector not to be affected by new size (extra paddings)
        setBackgroundResource(R.drawable.my_background);
        configureAdapter();
        configureListener();
    }

    private void initAttributes(Context context, AttributeSet attrs) {

        TypedArray defaultArray = context.obtainStyledAttributes(new int[]{R.attr.colorControlNormal, R.attr.colorAccent});
        int defaultBaseColor = defaultArray.getColor(0, 0);
        int defaultHighlightColor = defaultArray.getColor(1, 0);
        int defaultErrorColor = context.getResources().getColor(R.color.error_color);
        defaultArray.recycle();

        typedArray = context.obtainStyledAttributes(attrs, R.styleable.MaterialSpinner);
        baseColor = typedArray.getColor(R.styleable.MaterialSpinner_ms_baseColor, defaultBaseColor);
        highlightColor = typedArray.getColor(R.styleable.MaterialSpinner_ms_highlightColor, defaultHighlightColor);
        errorColor = typedArray.getColor(R.styleable.MaterialSpinner_ms_errorColor, defaultErrorColor);
        disabledColor = context.getResources().getColor(R.color.disabled_color);
        error = typedArray.getString(R.styleable.MaterialSpinner_ms_error);
        hint = typedArray.getString(R.styleable.MaterialSpinner_ms_hint);
        hintColor = typedArray.getColor(R.styleable.MaterialSpinner_ms_hintColor, baseColor);
        textColor = typedArray.getColor(R.styleable.MaterialSpinner_ms_textColor, getResources().getColor(android.R.color.black));
        floatingLabelText = typedArray.getString(R.styleable.MaterialSpinner_ms_floatingLabelText);
        floatingLabelColor = typedArray.getColor(R.styleable.MaterialSpinner_ms_floatingLabelColor, baseColor);
        multiline = typedArray.getBoolean(R.styleable.MaterialSpinner_ms_multiline, true);
        minNbErrorLines = typedArray.getInt(R.styleable.MaterialSpinner_ms_nbErrorLines, 1);
        alignLabels = typedArray.getBoolean(R.styleable.MaterialSpinner_ms_alignLabels, true);
        thickness = typedArray.getDimension(R.styleable.MaterialSpinner_ms_thickness, 1);
        thicknessError = typedArray.getDimension(R.styleable.MaterialSpinner_ms_thickness_error, 2);
        arrowColor = typedArray.getColor(R.styleable.MaterialSpinner_ms_arrowColor, baseColor);
        arrowSize = typedArray.getDimension(R.styleable.MaterialSpinner_ms_arrowSize, dpToPx(DEFAULT_ARROW_WIDTH_DP));
        enableErrorLabel = typedArray.getBoolean(R.styleable.MaterialSpinner_ms_enableErrorLabel, true);
        enableFloatingLabel = typedArray.getBoolean(R.styleable.MaterialSpinner_ms_enableFloatingLabel, true);
        isFloatingLabelAnimationEnabled = typedArray.getBoolean(R.styleable.MaterialSpinner_ms_enableFloatingLabelAnimation, true);
        configureNomes();
        configureValores();

        String typefacePath = typedArray.getString(R.styleable.MaterialSpinner_ms_typeface);
        if (typefacePath != null) {
            typeface = Typeface.createFromAsset(getContext().getAssets(), typefacePath);
        }

        typedArray.recycle();

        floatingLabelPercent = 0f;
        errorLabelPosX = 0;
        isSelected = false;
        floatingLabelVisible = false;
        lastPosition = -1;
        currentNbErrorLines = minNbErrorLines;

    }

    private void configureNomes() {
        if (typedArray.getString(R.styleable.MaterialSpinner_ms_nomes) != null) {
            nomes = Arrays.asList(typedArray.getString(R.styleable.MaterialSpinner_ms_nomes).split("\\s*;\\s*"));
        } else {
            nomes = new ArrayList<>();
        }
    }

    private void configureValores() {
        valores = new ArrayList<>();
        if (typedArray.getString(R.styleable.MaterialSpinner_ms_valores) != null) {
            List<String> valoresString = Arrays.asList(typedArray.getString(R.styleable.MaterialSpinner_ms_valores).split("\\s*;\\s*"));
            for (String valor : valoresString) {
                valores.add(Integer.parseInt(valor));
            }
        }
    }

    private void configureAdapter() {
        if (nomes != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, nomes);
            this.adapter = new HintAdapter(adapter, getContext());
            super.setAdapter(this.adapter);
        }
    }

    private void configureListener() {
        setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                if (adapterView.getChildAt(0) != null)
                    ((TextView) adapterView.getChildAt(0)).setTextColor(position >= 0 ? textColor : hintColor);
                positionSelected = position;
                if (selectionListener != null) {
                    if (enableLoading) {
                        load();
                    } else {
                        if (position >= 0)
                            selectionListener.onItemSelected(Spinner.this, position);
                        else
                            selectionListener.onNothingSelected();
                        if (nomes != null)
                            for (int i = 0; i < nomes.size(); i++)
                                if (position != i)
                                    selectionListener.onItemUnselected(Spinner.this, i);
                    }
                }
                setError(null);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    public Integer getSelectedItemValor() {
        try {
            return valores.get((getSelectedItemPosition() - 1));
        } catch (Exception e) {
            return null;
        }
    }

    public String getSelectedItemNome() {
        try {
            return nomes.get((getSelectedItemPosition() - 1));
        } catch (Exception e) {
            return null;
        }
    }

    public Object getSelectedItemTag() {
        try {
            return tags.get(getSelectedItemPosition() - 1);
        } catch (Exception e) {
            return null;
        }
    }

    public void setNomes(List<String> nomes) {
        this.nomes = nomes;
        if (nomes != null) {
            configureAdapter();
        }
    }

    public void setValores(List<Integer> valores) {
        this.valores = valores;
    }

    public void setTags(List<Object> tags) {
        this.tags = tags;
    }

    public List<Object> getTags(){
        return tags;
    }

    public void setSelectionListener(SelectionListener selectionListener) {
        this.selectionListener = selectionListener;
    }

    public void setPosition(Integer position) {
        if (position == null)
            setSelection(-1);
        else
            setSelection(position + 1, true);
    }

    public void showError() {
        setError(context.getString(R.string.campo_obrigatorio));
    }

    public void setPositionByTag(Object tag) {
        if (tags == null || tag == null || !tags.contains(tag))
            setPosition(null);
        else
            for (int i = 0; i < tags.size(); i++)
                if (tags.get(i).equals(tag)) {
                    setPosition(i);
                    return;
                }
    }

    public void setPositionByValor(int valor) {
        if (valores == null || !valores.contains(valor))
            setPosition(null);
        else
            for (int i = 0; i < valores.size(); i++)
                if (valores.get(i) == valor) {
                    setPosition(i);
                    return;
                }
    }

    public void remove(Object tag) {
        if (tag == null || tags == null || nomes == null || tags.isEmpty() || nomes.isEmpty() || adapter == null)
            return;
        Object selectedTag = getSelectedItemTag();
        int position = -1;
        for (int i = 0; i < tags.size(); i++) {
            Object pos = tags.get(i);
            if (pos.equals(tag)) {
                position = i;
            }
        }
        if (position != -1) {
            tags.remove(position);
            if (selectedTag != null && tag.equals(selectedTag)) {
                nomes.remove(position);
                configureAdapter();
            } else {
                adapter.remove(position);
            }
        }
    }

    public void remove(int position) {
        if (tags == null || nomes == null || tags.isEmpty() || nomes.isEmpty() || adapter == null)
            return;
        int selecionado = getSelectedItemPosition() - 1;
        tags.remove(position);
        if (selecionado == position) {
            nomes.remove(position);
            configureAdapter();
        } else {
            adapter.remove(position);
//            setPosition(selecionado+1);TODO Testar essa aplicação, se não resolveer, deletar
        }
    }

    public void enableLoading(boolean enableLoading) {
        this.enableLoading = enableLoading;
    }

    public static abstract class SelectionListener {
        public void onNothingSelected() {

        }

        public void onItemSelected(Spinner spinner, int position) {

        }

        public void onItemUnselected(Spinner spinner, int position) {

        }
    }

    private void load() {
        LoadSpinnerTask loadSpinnerTask = new LoadSpinnerTask();
        loadSpinnerTask.execute();
    }

    private class LoadSpinnerTask extends AsyncTask<Void, Void, Void> {
        private Dialog dialog;

        @Override
        protected void onPreExecute() {
            dialog = new Dialog.Builder(context)
                    .autoDismiss(false)
                    .cancelable(false)
                    .content(context.getString(R.string.carregando))
                    .build();
            dialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            SystemClock.sleep(500);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (positionSelected >= 0)
                selectionListener.onItemSelected(Spinner.this, positionSelected);
            else
                selectionListener.onNothingSelected();
            if (nomes != null)
                for (int i = 0; i < nomes.size(); i++)
                    if (positionSelected != i)
                        selectionListener.onItemUnselected(Spinner.this, i);
            dialog.dismiss();
        }
    }

    @Override
    public void setSelection(final int position) {
        this.post(new Runnable() {
            @Override
            public void run() {
                Spinner.super.setSelection(position);
            }
        });
    }

    private void initPaintObjects() {

        int labelTextSize = getResources().getDimensionPixelSize(R.dimen.label_text_size);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(labelTextSize);
        if (typeface != null) {
            textPaint.setTypeface(typeface);
        }
        textPaint.setColor(baseColor);
        baseAlpha = textPaint.getAlpha();

        selectorPath = new Path();
        selectorPath.setFillType(Path.FillType.EVEN_ODD);

        selectorPoints = new Point[3];
        for (int i = 0; i < 3; i++) {
            selectorPoints[i] = new Point();
        }
    }

    @Override
    public int getSelectedItemPosition() {
        return super.getSelectedItemPosition();
    }

    private void initPadding() {

        innerPaddingTop = getPaddingTop();
        innerPaddingLeft = getPaddingLeft();
        innerPaddingRight = getPaddingRight();
        innerPaddingBottom = getPaddingBottom();

        extraPaddingTop = enableFloatingLabel ? floatingLabelTopSpacing + floatingLabelInsideSpacing + floatingLabelBottomSpacing : floatingLabelBottomSpacing;
        updateBottomPadding();

    }

    private void updateBottomPadding() {
        Paint.FontMetrics textMetrics = textPaint.getFontMetrics();
        extraPaddingBottom = underlineTopSpacing + underlineBottomSpacing;
        if (enableErrorLabel) {
            extraPaddingBottom += (int) ((textMetrics.descent - textMetrics.ascent) * currentNbErrorLines);
        }
        updatePadding();
    }

    private void initDimensions() {
        underlineTopSpacing = getResources().getDimensionPixelSize(R.dimen.underline_top_spacing);
        underlineBottomSpacing = getResources().getDimensionPixelSize(R.dimen.underline_bottom_spacing);
        floatingLabelTopSpacing = getResources().getDimensionPixelSize(R.dimen.floating_label_top_spacing);
        floatingLabelBottomSpacing = getResources().getDimensionPixelSize(R.dimen.floating_label_bottom_spacing);
        rightLeftSpinnerPadding = alignLabels ? getResources().getDimensionPixelSize(R.dimen.right_left_spinner_padding) : 0;
        floatingLabelInsideSpacing = getResources().getDimensionPixelSize(R.dimen.floating_label_inside_spacing);
        errorLabelSpacing = (int) getResources().getDimension(R.dimen.error_label_spacing);
        minContentHeight = (int) getResources().getDimension(R.dimen.min_content_height);
    }

    private void initOnItemSelectedListener() {
        setOnItemSelectedListener(null);
    }

    /*
    * **********************************************************************************
    * ANIMATION METHODS
    * **********************************************************************************
    */

    private void initFloatingLabelAnimator() {
        if (floatingLabelAnimator == null) {
            floatingLabelAnimator = ObjectAnimator.ofFloat(this, "floatingLabelPercent", 0F, 1F);
            floatingLabelAnimator.setDuration(isFloatingLabelAnimationEnabled?300:10);
            floatingLabelAnimator.addUpdateListener(this);
        }
    }

    private void showFloatingLabel() {
        if (floatingLabelAnimator != null) {
            floatingLabelVisible = true;
            if (floatingLabelAnimator.isRunning()) {
                floatingLabelAnimator.reverse();
            } else {
                floatingLabelAnimator.start();
            }
        }
    }

    private void hideFloatingLabel() {
        if (floatingLabelAnimator != null) {
            floatingLabelVisible = false;
            floatingLabelAnimator.reverse();
        }
    }

    private void startErrorScrollingAnimator() {
        if (Build.VERSION.SDK_INT == 15)
            return;
        int textWidth = Math.round(textPaint.measureText(error.toString()));
        if (errorLabelAnimator == null) {
            errorLabelAnimator = ObjectAnimator.ofInt(this, "errorLabelPosX", 0, textWidth + getWidth() / 2);
            errorLabelAnimator.setStartDelay(1000);
            errorLabelAnimator.setInterpolator(new LinearInterpolator());
            errorLabelAnimator.setDuration(150 * error.length());
            errorLabelAnimator.addUpdateListener(this);
            errorLabelAnimator.setRepeatCount(ValueAnimator.INFINITE);
        } else {
            errorLabelAnimator.setIntValues(0, textWidth + getWidth() / 2);
        }
        errorLabelAnimator.start();
    }


    private void startErrorMultilineAnimator(float destLines) {
        if (Build.VERSION.SDK_INT == 15)
            return;
        if (errorLabelAnimator == null) {
            errorLabelAnimator = ObjectAnimator.ofFloat(this, "currentNbErrorLines", destLines);

        } else {
            errorLabelAnimator.setFloatValues(destLines);
        }
        errorLabelAnimator.start();
    }


    /*
     * **********************************************************************************
     * UTILITY METHODS
     * **********************************************************************************
    */

    private int dpToPx(float dp) {
        final DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics);
        return Math.round(px);
    }

    private float pxToDp(float px) {
        final DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        return px * displayMetrics.density;
    }

    private void updatePadding() {
        int left = innerPaddingLeft;
        int top = innerPaddingTop + extraPaddingTop;
        int right = innerPaddingRight;
        int bottom = innerPaddingBottom + extraPaddingBottom;
        super.setPadding(left, top, right, bottom);
        setMinimumHeight(top + bottom + minContentHeight);
    }

    private boolean needScrollingAnimation() {
        if (error != null) {
            float screenWidth = getWidth() - rightLeftSpinnerPadding;
            float errorTextWidth = textPaint.measureText(error.toString(), 0, error.length());
            return errorTextWidth > screenWidth ? true : false;
        }
        return false;
    }

    private int prepareBottomPadding() {

        int targetNbLines = minNbErrorLines;
        if (error != null) {
            staticLayout = new StaticLayout(error, textPaint, getWidth() - getPaddingRight() - getPaddingLeft(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
            int nbErrorLines = staticLayout.getLineCount();
            targetNbLines = Math.max(minNbErrorLines, nbErrorLines);
        }
        return targetNbLines;
    }

    private boolean isSpinnerEmpty() {
        return (getAdapter().getCount() == 0 && hint == null) || (getAdapter().getCount() == 1 && hint != null);
    }

    /*
     * **********************************************************************************
     * DRAWING METHODS
     * **********************************************************************************
    */


    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);
        int startX = 0;
        int endX = getWidth();
        int lineHeight;

        int startYLine = getHeight() - getPaddingBottom() + underlineTopSpacing;
        int startYFloatingLabel = (int) (getPaddingTop() - floatingLabelPercent * floatingLabelBottomSpacing);


        if (error != null && enableErrorLabel) {
            lineHeight = dpToPx(thicknessError);
            int startYErrorLabel = startYLine + errorLabelSpacing + lineHeight;
            paint.setColor(errorColor);
            textPaint.setColor(errorColor);
            //Error Label Drawing
            if (multiline) {
                canvas.save();
                canvas.translate(startX + rightLeftSpinnerPadding, startYErrorLabel - errorLabelSpacing);
                staticLayout.draw(canvas);
                canvas.restore();

            } else {
                //scrolling
                canvas.drawText(error.toString(), startX + rightLeftSpinnerPadding - errorLabelPosX, startYErrorLabel, textPaint);
                if (errorLabelPosX > 0) {
                    canvas.save();
                    canvas.translate(textPaint.measureText(error.toString()) + getWidth() / 2, 0);
                    canvas.drawText(error.toString(), startX + rightLeftSpinnerPadding - errorLabelPosX, startYErrorLabel, textPaint);
                    canvas.restore();
                }
            }

        } else {
            lineHeight = dpToPx(thickness);
            if (isSelected || hasFocus()) {
                paint.setColor(highlightColor);
            } else {
                paint.setColor(isEnabled() ? baseColor : disabledColor);
            }
        }

        // Underline Drawing
        canvas.drawRect(startX, startYLine, endX, startYLine + lineHeight, paint);

        //Floating Label Drawing
        if ((hint != null || floatingLabelText != null) && enableFloatingLabel) {
            if (isSelected || hasFocus()) {
                textPaint.setColor(highlightColor);
            } else {
                textPaint.setColor(isEnabled() ? floatingLabelColor : disabledColor);
            }
            if (floatingLabelAnimator.isRunning() || !floatingLabelVisible) {
                textPaint.setAlpha((int) ((0.8 * floatingLabelPercent + 0.2) * baseAlpha * floatingLabelPercent));
            }
            String textToDraw = floatingLabelText != null ? floatingLabelText.toString() : hint.toString();
            canvas.drawText(textToDraw, startX + rightLeftSpinnerPadding, startYFloatingLabel, textPaint);
        }

        drawSelector(canvas, getWidth() - rightLeftSpinnerPadding, getPaddingTop() + dpToPx(8));


    }

    private void drawSelector(Canvas canvas, int posX, int posY) {
        if (isSelected || hasFocus()) {
            paint.setColor(highlightColor);
        } else {
            paint.setColor(isEnabled() ? arrowColor : disabledColor);
        }

        Point point1 = selectorPoints[0];
        Point point2 = selectorPoints[1];
        Point point3 = selectorPoints[2];

        point1.set(posX, posY);
        point2.set((int) (posX - (arrowSize)), posY);
        point3.set((int) (posX - (arrowSize / 2)), (int) (posY + (arrowSize / 2)));

        selectorPath.reset();
        selectorPath.moveTo(point1.x, point1.y);
        selectorPath.lineTo(point2.x, point2.y);
        selectorPath.lineTo(point3.x, point3.y);
        selectorPath.close();
        canvas.drawPath(selectorPath, paint);
    }

    /*
     * **********************************************************************************
     * LISTENER METHODS
     * **********************************************************************************
    */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isEnabled()) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isSelected = true;
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isSelected = false;
                    break;
            }
            invalidate();
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void setOnItemSelectedListener(final OnItemSelectedListener listener) {

        final OnItemSelectedListener selectionListener = new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                if (hint != null || floatingLabelText != null) {
                    if (!floatingLabelVisible && position != 0) {
                        showFloatingLabel();
                    } else if (floatingLabelVisible && position == 0) {
                        hideFloatingLabel();
                    }
                }

                if (position != lastPosition && error != null) {
                    setError(null);
                }
                lastPosition = position;

                if (listener != null) {
                    position = hint != null ? position - 1 : position;
                    listener.onItemSelected(parent, view, position, id);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (listener != null) {
                    listener.onNothingSelected(parent);
                }
            }
        };

        super.setOnItemSelectedListener(selectionListener);
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        invalidate();
    }


    /*
    * **********************************************************************************
    * GETTERS AND SETTERS
    * **********************************************************************************
    */

    public int getBaseColor() {
        return baseColor;
    }

    public void setBaseColor(int baseColor) {
        this.baseColor = baseColor;
        textPaint.setColor(baseColor);
        baseAlpha = textPaint.getAlpha();
        invalidate();
    }

    public int getHighlightColor() {
        return highlightColor;
    }

    public void setHighlightColor(int highlightColor) {
        this.highlightColor = highlightColor;
        invalidate();
    }

    public int getHintColor() {
        return hintColor;
    }

    public void setHintColor(int hintColor) {
        this.hintColor = hintColor;
        invalidate();
    }

    public int getTextColor() {
        return textColor;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
        invalidate();
    }

    public int getErrorColor() {
        return errorColor;
    }

    public void setErrorColor(int errorColor) {
        this.errorColor = errorColor;
        invalidate();
    }

    public void setHint(CharSequence hint) {
        this.hint = hint;
        invalidate();
    }

    public void setHint(int resid) {
        CharSequence hint = getResources().getString(resid);
        setHint(hint);
    }

    public CharSequence getHint() {
        return hint;
    }

    public void setFloatingLabelText(CharSequence floatingLabelText) {
        this.floatingLabelText = floatingLabelText;
        invalidate();
    }

    public void setFloatingLabelText(int resid) {
        String floatingLabelText = getResources().getString(resid);
        setFloatingLabelText(floatingLabelText);
    }

    public CharSequence getFloatingLabelText() {
        return this.floatingLabelText;
    }

    public void setError(CharSequence error) {
        this.error = error;
        if (errorLabelAnimator != null) {
            errorLabelAnimator.end();
        }

        if (multiline) {
            startErrorMultilineAnimator(prepareBottomPadding());
        } else if (needScrollingAnimation()) {
            startErrorScrollingAnimator();
        }
        requestLayout();
    }

    public void setError(int resid) {
        CharSequence error = getResources().getString(resid);
        setError(error);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled) {
            isSelected = false;
            invalidate();
        }
        super.setEnabled(enabled);
    }

    public CharSequence getError() {
        return this.error;
    }

    /**
     * @deprecated {use @link #setPaddingSafe(int, int, int, int)} to keep internal computation OK
     */
    @Deprecated
    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
    }


    public void setPaddingSafe(int left, int top, int right, int bottom) {
        innerPaddingRight = right;
        innerPaddingLeft = left;
        innerPaddingTop = top;
        innerPaddingBottom = bottom;

        updatePadding();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setAdapter(SpinnerAdapter adapter) {
        if (adapter == null)
            this.adapter = null;
        else
            this.adapter = new HintAdapter(adapter, getContext());
        super.setAdapter(this.adapter);
    }

    private float getFloatingLabelPercent() {
        return floatingLabelPercent;
    }

    private void setFloatingLabelPercent(float floatingLabelPercent) {
        this.floatingLabelPercent = floatingLabelPercent;
    }

    private int getErrorLabelPosX() {
        return errorLabelPosX;
    }

    private void setErrorLabelPosX(int errorLabelPosX) {
        this.errorLabelPosX = errorLabelPosX;
    }

    private float getCurrentNbErrorLines() {
        return currentNbErrorLines;
    }

    private void setCurrentNbErrorLines(float currentNbErrorLines) {
        this.currentNbErrorLines = currentNbErrorLines;
        updateBottomPadding();
    }

    @Override
    public Object getItemAtPosition(int position) {
        final SpinnerAdapter adapter = getAdapter();
        if (hint != null) {
            position++;
        }
        return (adapter == null || position < 0) ? null : adapter.getItem(position);
    }

    @Override
    public long getItemIdAtPosition(int position) {
        final SpinnerAdapter adapter = getAdapter();
        if (hint != null) {
            position++;
        }
        return (adapter == null || position < 0) ? INVALID_ROW_ID : adapter.getItemId(position);
    }

    public ListPopupWindow getPopup() {
        ListPopupWindow popupWindow = null;
        try {
            Field field = super.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            popupWindow = (ListPopupWindow) field.get(this);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return popupWindow;
    }

    /*
     * **********************************************************************************
     * INNER CLASS
     * **********************************************************************************
     */

    private class HintAdapter extends BaseAdapter {

        private static final int HINT_TYPE = -1;

        private ArrayAdapter mSpinnerAdapter;
        private Context mContext;

        HintAdapter(SpinnerAdapter spinnerAdapter, Context context) {
            List<Object> objects = new ArrayList<>();
            for (int i = 0; i < spinnerAdapter.getCount(); i++)
                objects.add(spinnerAdapter.getItem(i));
            mSpinnerAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, objects);
            mContext = context;
        }

        HintAdapter(ArrayAdapter spinnerAdapter, Context context) {
            mSpinnerAdapter = spinnerAdapter;
            mContext = context;
        }

        void remove(int position) {
            mSpinnerAdapter.remove(mSpinnerAdapter.getItem(position));
            mSpinnerAdapter.notifyDataSetChanged();
            notifyDataSetChanged();
        }

        @Override
        public int getViewTypeCount() {
            //Workaround waiting for a Google correction (https://code.google.com/p/android/issues/detail?id=79011)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return 1;
            }
            int viewTypeCount = mSpinnerAdapter.getViewTypeCount();
            return viewTypeCount;
        }

        @Override
        public int getItemViewType(int position) {
            position = hint != null ? position - 1 : position;
            return (position == -1) ? HINT_TYPE : mSpinnerAdapter.getItemViewType(position);
        }

        @Override
        public int getCount() {
            int count = mSpinnerAdapter.getCount();
            return hint != null ? count + 1 : count;
        }

        @Override
        public Object getItem(int position) {
            position = hint != null ? position - 1 : position;
            return (position == -1) ? hint : mSpinnerAdapter.getItem(position);
        }

        @Override
        public long getItemId(int position) {
            position = hint != null ? position - 1 : position;
            return (position == -1) ? 0 : mSpinnerAdapter.getItemId(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return buildView(position, convertView, parent, false);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return buildView(position, convertView, parent, true);
        }

        private View buildView(int position, View convertView, ViewGroup parent, boolean isDropDownView) {
            if (getItemViewType(position) == HINT_TYPE) {
                return getHintView(convertView, parent, isDropDownView);
            }
            //workaround to have multiple types in spinner
            if (convertView != null) {
                convertView = (convertView.getTag() != null && convertView.getTag() instanceof Integer && (Integer) convertView.getTag() != HINT_TYPE) ? convertView : null;
            }
            position = hint != null ? position - 1 : position;
            return isDropDownView ? mSpinnerAdapter.getDropDownView(position, convertView, parent) : mSpinnerAdapter.getView(position, convertView, parent);
        }

        private View getHintView(final View convertView, final ViewGroup parent, final boolean isDropDownView) {

            final LayoutInflater inflater = LayoutInflater.from(mContext);
            final int resid = isDropDownView ? android.R.layout.simple_spinner_dropdown_item : android.R.layout.simple_spinner_item;
            final TextView textView = (TextView) inflater.inflate(resid, parent, false);
            textView.setText(hint);
            textView.setTextColor(Spinner.this.isEnabled() ? hintColor : disabledColor);
            textView.setTag(HINT_TYPE);
            return textView;
        }
    }

    private class SpinnerArrayAdapter<T> extends ArrayAdapter<T> {

        public SpinnerArrayAdapter(Context ctx, List<T> objects) {
            super(ctx, android.R.layout.simple_spinner_item, objects);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView text = (TextView) view.findViewById(android.R.id.text1);
            text.setTextColor(textColor);

            return view;

        }


    }
}