/*
 * Copyright (C) 2011 Patrik Akerfeldt
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.taptwo.android.widget;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.taptwo.android.widget.viewflow.R;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.View.BaseSavedState;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.Scroller;

/**
 * A horizontally scrollable {@link ViewGroup} with items populated from an
 * {@link Adapter}. The ViewFlow uses a buffer to store loaded {@link View}s in.
 * The default size of the buffer is 3 elements on both sides of the currently
 * visible {@link View}, making up a total buffer size of 3 * 2 + 1 = 7. The
 * buffer size can be changed using the {@code sidebuffer} xml attribute.
 * 
 */
public class ViewFlow extends AdapterView<Adapter> {

	private static final int SNAP_VELOCITY = 1000;
	private static final int INVALID_SCREEN = -1;
	private final static int TOUCH_STATE_REST = 0;
	private final static int TOUCH_STATE_SCROLLING = 1;

	private LinkedList<View> mLoadedViews;
	private int mCurrentBufferIndex;
	private int mCurrentAdapterIndex;
	private int mSideBuffer = 2;
	private Scroller mScroller;
	private VelocityTracker mVelocityTracker;
	private int mTouchState = TOUCH_STATE_REST;
	private float mLastMotionX;
	private int mTouchSlop;
	private int mMaximumVelocity;
	private int mCurrentScreen;
	private int mNextScreen = INVALID_SCREEN;
	private boolean mFirstLayout = true;
	private ViewSwitchListener mViewSwitchListener;
	private Adapter mAdapter;
    private boolean indeterminate = false;
	private int mLastScrollDirection;
	private AdapterDataSetObserver mDataSetObserver;
	private FlowIndicator mIndicator;
	private int mLastOrientation = -1;
	private int numVisibleViews = 1;

	private OnGlobalLayoutListener orientationChangeListener = new OnGlobalLayoutListener() {

		@Override
		public void onGlobalLayout() {
			getViewTreeObserver().removeGlobalOnLayoutListener(
					orientationChangeListener);
			setSelection(mCurrentAdapterIndex);
		}
	};
	private int mNumberOfViewTypes;
	private ArrayList<View>[] mRecycledViews;
	private long mCurrentId;

	/**
	 * Receives call backs when a new {@link View} has been scrolled to.
	 */
	public static interface ViewSwitchListener {

		/**
		 * This method is called when a new View has been scrolled to.
		 * 
		 * @param view
		 *            the {@link View} currently in focus.
		 * @param position
		 *            The position in the adapter of the {@link View} currently in focus.
		 * @param direction 
		 */
		void onSwitched(View view, int position, int direction);

	}

	public ViewFlow(Context context) {
		super(context);
		mSideBuffer = 3;
		init();
	}

	public ViewFlow(Context context, int sideBuffer) {
		super(context);
		mSideBuffer = sideBuffer;
		init();
	}

	public ViewFlow(Context context, AttributeSet attrs) {
		super(context, attrs);
		TypedArray styledAttrs = context.obtainStyledAttributes(attrs,
				R.styleable.ViewFlow);
		mSideBuffer = styledAttrs.getInt(R.styleable.ViewFlow_sidebuffer, 3);
		init();
	}
	
	/**
	 * Return the parceable instance to be saved
	 */
	@Override
	public Parcelable onSaveInstanceState() {
		final SavedState state = new SavedState(super.onSaveInstanceState());
		state.currentScreen = mCurrentScreen;
		return state;
	}

	/**
	 * Restore the previous saved current screen
	 */
	@Override
	public void onRestoreInstanceState(Parcelable state) {
		SavedState savedState = (SavedState) state;
		super.onRestoreInstanceState(savedState.getSuperState());
		if (savedState.currentScreen != -1) {
			setSelection(savedState.currentScreen);
//			mCurrentScreen = savedState.currentScreen;
		}
	}

	private void init() {
		mLoadedViews = new LinkedList<View>();
		mScroller = new Scroller(getContext());
		final ViewConfiguration configuration = ViewConfiguration
				.get(getContext());
		mTouchSlop = configuration.getScaledTouchSlop();
		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
	}

	public void onConfigurationChanged(Configuration newConfig) {
		if (newConfig.orientation != mLastOrientation) {
			mLastOrientation = newConfig.orientation;
			getViewTreeObserver().addOnGlobalLayoutListener(orientationChangeListener);
		}
	}

	public int getViewsCount() {
		if(mAdapter != null){
			return mAdapter.getCount();
		} else {
			return 1;
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		
		final int width = MeasureSpec.getSize(widthMeasureSpec);
		final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		if (widthMode != MeasureSpec.EXACTLY && !isInEditMode()) {
			throw new IllegalStateException(
					"ViewFlow can only be used in EXACTLY mode.");
		}

		final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		if (heightMode != MeasureSpec.EXACTLY && !isInEditMode()) {
			throw new IllegalStateException(
					"ViewFlow can only be used in EXACTLY mode.");
		}

		// The children are given the same width and height as the workspace
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
		}

		if (mFirstLayout) {
			mScroller.startScroll(0, 0, mCurrentScreen * width, 0, 0);
			mFirstLayout = false;
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		int childLeft = 0;

		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);
			if (child.getVisibility() != View.GONE) {
				final int childWidth = child.getMeasuredWidth();
				child.layout(childLeft, 0, childLeft + childWidth,
						child.getMeasuredHeight());
				childLeft += childWidth;
			}
		}
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (getChildCount() == 0)
			return false;

		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
		mVelocityTracker.addMovement(ev);

		final int action = ev.getAction();
		final float x = ev.getX();

		switch (action) {
		case MotionEvent.ACTION_DOWN:
			/*
			 * If being flinged and user touches, stop the fling. isFinished
			 * will be false if being flinged.
			 */
			if (!mScroller.isFinished()) {
				mScroller.abortAnimation();
			}

			// Remember where the motion event started
			mLastMotionX = x;

			mTouchState = mScroller.isFinished() ? TOUCH_STATE_REST
					: TOUCH_STATE_SCROLLING;

			break;

		case MotionEvent.ACTION_MOVE:
			final int xDiff = (int) Math.abs(x - mLastMotionX);

			boolean xMoved = xDiff > mTouchSlop;

			if (xMoved) {
				// Scroll if the user moved far enough along the X axis
				mTouchState = TOUCH_STATE_SCROLLING;
			}

			if (mTouchState == TOUCH_STATE_SCROLLING) {
				// Scroll to follow the motion event
				final int deltaX = (int) (mLastMotionX - x);
				mLastMotionX = x;

				final int scrollX = getScrollX();
				if (deltaX < 0) {
					if (scrollX > 0) {
						scrollBy(Math.max(-scrollX, deltaX), 0);
					}
				} else if (deltaX > 0) {
					final int availableToScroll = getChildAt(
							getChildCount() - 1).getRight()
							- scrollX - getWidth();
					if (availableToScroll > 0) {
						scrollBy(Math.min(availableToScroll, deltaX), 0);
					}
				}
				return true;
			}
			break;

		case MotionEvent.ACTION_UP:
			if (mTouchState == TOUCH_STATE_SCROLLING) {
				final VelocityTracker velocityTracker = mVelocityTracker;
				velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
				int velocityX = (int) velocityTracker.getXVelocity();

				if (velocityX > SNAP_VELOCITY && mCurrentScreen > 0) {
					// Fling hard enough to move left
					snapToScreen(mCurrentScreen - 1);
				} else if (velocityX < -SNAP_VELOCITY
						&& mCurrentScreen < getChildCount() - 1) {
					// Fling hard enough to move right
					snapToScreen(mCurrentScreen + 1);
				} else {
					snapToDestination();
				}

				if (mVelocityTracker != null) {
					mVelocityTracker.recycle();
					mVelocityTracker = null;
				}
			}

			mTouchState = TOUCH_STATE_REST;

			break;
		case MotionEvent.ACTION_CANCEL:
			mTouchState = TOUCH_STATE_REST;
		}
		return false;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (getChildCount() == 0)
			return false;

		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
		mVelocityTracker.addMovement(ev);

		final int action = ev.getAction();
		final float x = ev.getX();

		switch (action) {
		case MotionEvent.ACTION_DOWN:
			/*
			 * If being flinged and user touches, stop the fling. isFinished
			 * will be false if being flinged.
			 */
			if (!mScroller.isFinished()) {
				mScroller.abortAnimation();
			}

			// Remember where the motion event started
			mLastMotionX = x;

			mTouchState = mScroller.isFinished() ? TOUCH_STATE_REST
					: TOUCH_STATE_SCROLLING;

			break;

		case MotionEvent.ACTION_MOVE:
			final int xDiff = (int) Math.abs(x - mLastMotionX);

			boolean xMoved = xDiff > mTouchSlop;

			if (xMoved) {
				// Scroll if the user moved far enough along the X axis
				mTouchState = TOUCH_STATE_SCROLLING;
			}

			if (mTouchState == TOUCH_STATE_SCROLLING) {
				// Scroll to follow the motion event
				final int deltaX = (int) (mLastMotionX - x);
				mLastMotionX = x;

				final int scrollX = getScrollX();
				if (deltaX < 0) {
					if (scrollX > 0) {
						scrollBy(Math.max(-scrollX, deltaX), 0);
					}
				} else if (deltaX > 0) {
					final int availableToScroll = getChildAt(
							getChildCount() - 1).getRight()
							- scrollX - getWidth();
					if (availableToScroll > 0) {
						scrollBy(Math.min(availableToScroll, deltaX), 0);
					}
				}
				return true;
			}
			break;

		case MotionEvent.ACTION_UP:
			if (mTouchState == TOUCH_STATE_SCROLLING) {
				final VelocityTracker velocityTracker = mVelocityTracker;
				velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
				int velocityX = (int) velocityTracker.getXVelocity();

				if (velocityX > SNAP_VELOCITY && mCurrentScreen > 0) {
					// Fling hard enough to move left
					snapToScreen(mCurrentScreen - 1);
				} else if (velocityX < -SNAP_VELOCITY
						&& mCurrentScreen < getChildCount() - 1) {
					// Fling hard enough to move right
					snapToScreen(mCurrentScreen + 1);
				} else {
					snapToDestination();
				}

				if (mVelocityTracker != null) {
					mVelocityTracker.recycle();
					mVelocityTracker = null;
				}
			}

			mTouchState = TOUCH_STATE_REST;

			break;
		case MotionEvent.ACTION_CANCEL:
			snapToDestination();
			mTouchState = TOUCH_STATE_REST;
		}
		return true;
	}

	@Override
	protected void onScrollChanged(int h, int v, int oldh, int oldv) {
		super.onScrollChanged(h, v, oldh, oldv);
		if (mIndicator != null) {
			/*
			 * The actual horizontal scroll origin does typically not match the
			 * perceived one. Therefore, we need to calculate the perceived
			 * horizontal scroll origin here, since we use a view buffer.
			 */
            int fromOrigin = indeterminate ? ((IndeterminateAdapter)mAdapter).getLeftMostIndex() : 0;
			int hPerceived = h + (mCurrentAdapterIndex - fromOrigin - mCurrentBufferIndex) * getWidth();
			
			mIndicator.onScrolled(hPerceived, v, oldh, oldv);
		}
	}

	private void snapToDestination() {
		final int screenWidth = getWidth();
		final int whichScreen = (getScrollX() + (screenWidth / 2))
				/ screenWidth;

		snapToScreen(whichScreen);
	}

	private void snapToScreen(int whichScreen) {
		mLastScrollDirection = whichScreen - mCurrentScreen;
		if (!mScroller.isFinished())
			return;

		whichScreen = Math.max(0, Math.min(whichScreen, getChildCount() - 1));

		mNextScreen = whichScreen;

		final int newX = whichScreen * getWidth();
		final int delta = newX - getScrollX();
		mScroller.startScroll(getScrollX(), 0, delta, 0, Math.abs(delta) * 2);
		invalidate();
	}

	@Override
	public void computeScroll() {
		if (mScroller.computeScrollOffset()) {
			scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
			postInvalidate();
		} else if (mNextScreen != INVALID_SCREEN) {
			mCurrentScreen = Math.max(0,
					Math.min(mNextScreen, getChildCount() - 1));
			mNextScreen = INVALID_SCREEN;
			postViewSwitched(mLastScrollDirection);
		}
	}

	/**
	 * Scroll to the {@link View} in the view buffer specified by the index.
	 * 
	 * @param indexInBuffer
	 *            Index of the view in the view buffer.
	 */
	private void setVisibleView(int indexInBuffer, boolean uiThread) {
		mCurrentScreen = Math.max(0,
				Math.min(indexInBuffer, getChildCount() - 1));
		int dx = (mCurrentScreen * getWidth()) - mScroller.getCurrX();
		mScroller.startScroll(mScroller.getCurrX(), mScroller.getCurrY(), dx,
				0, 0);
		if(dx == 0)
			onScrollChanged(mScroller.getCurrX() + dx, mScroller.getCurrY(), mScroller.getCurrX() + dx, mScroller.getCurrY());
		if (uiThread)
			invalidate();
		else
			postInvalidate();
	}

	/**
	 * Set the listener that will receive notifications every time the {code
	 * ViewFlow} scrolls.
	 * 
	 * @param l
	 *            the scroll listener
	 */
	public void setOnViewSwitchListener(ViewSwitchListener l) {
		mViewSwitchListener = l;
	}

	@Override
	public Adapter getAdapter() {
		return mAdapter;
	}

	@Override
	public void setAdapter(Adapter adapter) {
		setAdapter(adapter, 0);
	}
	
	@SuppressWarnings("unchecked")
	public void setAdapter(Adapter adapter, int initialPosition) {
		if (mAdapter != null) {
			mAdapter.unregisterDataSetObserver(mDataSetObserver);
			
			for(ArrayList<View> views : mRecycledViews){
				for(View v : views){
					removeDetachedView(v, false);
				}
			}			
		}

		mAdapter = adapter;
        indeterminate = IndeterminateAdapter.class.isAssignableFrom(mAdapter.getClass());

        if (mAdapter != null) {
			mDataSetObserver = new AdapterDataSetObserver();
			mAdapter.registerDataSetObserver(mDataSetObserver);
			mNumberOfViewTypes = mAdapter.getViewTypeCount();
		}
		
		mRecycledViews = new ArrayList[mNumberOfViewTypes];
		for (int i = 0; i < mNumberOfViewTypes; i++) {
			mRecycledViews[i] = new ArrayList<View>();
        }
		
		if (mAdapter == null || mAdapter.getCount() == 0)
			return;
		
		setSelection(initialPosition);		
	}
	
	@Override
	public View getSelectedView() {
		return (mCurrentBufferIndex < mLoadedViews.size() ? mLoadedViews.get(mCurrentBufferIndex) : null);
	}

    @Override
    public int getSelectedItemPosition() {
        return mCurrentAdapterIndex;
    }

	/**
	 * Set the FlowIndicator
	 * 
	 * @param flowIndicator
	 */
	public void setFlowIndicator(FlowIndicator flowIndicator) {
		mIndicator = flowIndicator;
		mIndicator.setViewFlow(this);
	}

	@Override
	public void setSelection(int position) {
		mNextScreen = INVALID_SCREEN;
		mScroller.forceFinished(true);
		if (mAdapter == null)
			return;

        position = Math.max(position, 0);
        if(!indeterminate)
    		position =  Math.min(position, mAdapter.getCount()-1);

		ArrayList<View> recycleViews = new ArrayList<View>();
		View recycleView;
        while (!mLoadedViews.isEmpty()) {
            recycleViews.add(recycleView = mLoadedViews.remove());
			detachViewFromParent(recycleView);
			mRecycledViews[((ViewFlow.LayoutParams)recycleView.getLayoutParams()).viewType].add(recycleView);
        }

        View currentView = makeAndAddView(position, true);//,
//				(recycleViews.isEmpty() ? null : recycleViews.remove(0)));
        mLoadedViews.addLast(currentView);

        for(int offset = 1; mSideBuffer - offset >= 0; offset++) {
            int leftIndex = position - offset;
			int rightIndex = position + offset;
            if(indeterminate)
            {
                if(leftIndex >= ((IndeterminateAdapter)mAdapter).getLeftMostIndex())
                    mLoadedViews.addFirst(makeAndAddView(leftIndex, false));

                else if(rightIndex <= ((IndeterminateAdapter)mAdapter).getRightMostIndex())
                    mLoadedViews.addLast(makeAndAddView(rightIndex, true));
            }else{
                if(leftIndex >= 0)
                    mLoadedViews.addFirst(makeAndAddView(leftIndex, false));//,
    //						(recycleViews.isEmpty() ? null : recycleViews.remove(0))));
                if(rightIndex < mAdapter.getCount())
                    mLoadedViews.addLast(makeAndAddView(rightIndex, true));//,
    //						(recycleViews.isEmpty() ? null : recycleViews.remove(0))));
            }
		}

        mCurrentBufferIndex = mLoadedViews.indexOf(currentView);
		mCurrentAdapterIndex = position;
		
		mCurrentId = mAdapter.getItemId(mCurrentAdapterIndex);
//		Log.d("FetLife","Current ID = " + mCurrentId);

		// TODO make sure we don't keep too many recycled views.
//		for (View view : recycleViews) {
//			removeDetachedView(view, false);
//		}
        pruneRecycledViews();
        requestLayout();
        setVisibleView(mCurrentBufferIndex, false);
		if (mIndicator != null) {
			mIndicator.onSwitched(mLoadedViews.get(mCurrentBufferIndex),
					mCurrentAdapterIndex,0);
		}
		if (mViewSwitchListener != null) {
			mViewSwitchListener
					.onSwitched(mLoadedViews.get(mCurrentBufferIndex),
							mCurrentAdapterIndex,0);
		}
    }

	// For a full-screen (one item at a time) ViewFlow, we really only need
	// one view of any given type in reserve... but looking forward to expandability,
	// we're going to use # view types + 1 
	// TODO we should adjust this based on number of visible views
	private void pruneRecycledViews() {
		for(ArrayList<View> views : mRecycledViews){
			int numViews = views.size();
			if(numViews > numVisibleViews){
				for(int i=numViews-1; i > numVisibleViews; i--){
					removeDetachedView(views.get(i),false);
					views.remove(0);
				}
			}
		}
	}

	private void resetFocus() {
		logBuffer();
		mLoadedViews.clear();
		if(mRecycledViews != null){
			for(ArrayList<View> views : mRecycledViews){
				for(View v : views){
					removeDetachedView(v, false);
				}
			}
		}
		
		removeAllViewsInLayout();

        IndeterminateAdapter iAdapter = indeterminate ? (IndeterminateAdapter)mAdapter : null;
        int leftEdge = indeterminate ? iAdapter.getLeftMostIndex() : 0;
        int rightEdge = indeterminate ? iAdapter.getRightMostIndex() : mAdapter.getCount() - 1;
//        int leftOffset = mCurrentAdapterIndex - leftEdge;
//        int rightOffset =  rightEdge - mCurrentAdapterIndex;

        for(int i=Math.max(leftEdge, mCurrentAdapterIndex - mSideBuffer);
                i<Math.min(rightEdge, mCurrentAdapterIndex + mSideBuffer);
                i++)
        {
            mLoadedViews.addLast(makeAndAddView(i, true));//, null));
            if (i == mCurrentAdapterIndex)
                mCurrentBufferIndex = mLoadedViews.size() - 1;
        }


//		setVisibleView(mCurrentBufferIndex,false);
		
		if(mIndicator != null){
			// force an invalidate on the flow indicator when the data set changes
			mIndicator.setViewFlow(this);
		}
		logBuffer();
		requestLayout();
	}

	private void postViewSwitched(int direction) {
		if (direction == 0)
			return;

        if(indeterminate)
        {
            IndeterminateAdapter iAdapter = (IndeterminateAdapter)mAdapter;

            if(direction < 0) // to the left
            {
                mCurrentAdapterIndex--;
                mCurrentBufferIndex--;
                if((iAdapter.getRightMostIndex() - mCurrentAdapterIndex) > mSideBuffer)
                {
                    recycleView(mLoadedViews.removeLast());
//                    mCurrentBufferIndex++;
                }

                int newAdapterIndex = mCurrentAdapterIndex - mSideBuffer;
                if(newAdapterIndex >= iAdapter.getLeftMostIndex())
                    mLoadedViews.addFirst(makeAndAddView(newAdapterIndex, false));
                else{
//                    mCurrentBufferIndex++;
//                    mCurrentAdapterIndex++;
                }
            }else{ // to the right
                mCurrentAdapterIndex++;
                mCurrentBufferIndex++;
                if((mCurrentAdapterIndex - iAdapter.getLeftMostIndex()) > mSideBuffer)
                {
                    recycleView(mLoadedViews.removeFirst());
//                    mCurrentBufferIndex--;
                }

                int newAdapterIndex = mCurrentAdapterIndex + mSideBuffer;
                if(newAdapterIndex <= iAdapter.getRightMostIndex())
                    mLoadedViews.addLast(makeAndAddView(newAdapterIndex, true));
                else{
//                    mCurrentBufferIndex--;
//                    mCurrentAdapterIndex--;
                }
            }
        }else if (direction > 0) { // to the right
			mCurrentAdapterIndex++;
			mCurrentBufferIndex++;

//			View recycleView = null;

			// Remove view outside buffer range
			if (mCurrentAdapterIndex > mSideBuffer) {
				recycleView(mLoadedViews.removeFirst());
				// removeView(recycleView);
				mCurrentBufferIndex--;
			}

			// Add new view to buffer
			int newBufferIndex = mCurrentAdapterIndex + mSideBuffer;
            if (newBufferIndex < mAdapter.getCount())
                mLoadedViews.addLast(makeAndAddView(newBufferIndex, true));//,
//						recycleView));

		} else { // to the left
			mCurrentAdapterIndex--;
			mCurrentBufferIndex--;
//			View recycleView = null;

			// Remove view outside buffer range
			if (mAdapter.getCount() - 1 - mCurrentAdapterIndex > mSideBuffer) {
				recycleView(mLoadedViews.removeLast());
			}

			// Add new view to buffer
			int newBufferIndex = mCurrentAdapterIndex - mSideBuffer;
            if (newBufferIndex > -1) {
                mLoadedViews.addFirst(makeAndAddView(newBufferIndex, false));//,
//						recycleView));
            }
            mCurrentBufferIndex++;
		}

		mCurrentId = mAdapter.getItemId(mCurrentAdapterIndex);
//		Log.d("FetLife","Current ID = " + mCurrentId);
		
		requestLayout();
		setVisibleView(mCurrentBufferIndex, true);
		if (mIndicator != null) {
            Log.d("MALACHI", "mCurrentBufferIndex = " + mCurrentBufferIndex + ", but mLoadedViews.size()=" + mLoadedViews.size());
			mIndicator.onSwitched(mLoadedViews.get(mCurrentBufferIndex),
					mCurrentAdapterIndex,direction);
		}
		if (mViewSwitchListener != null) {
            mViewSwitchListener
					.onSwitched(mLoadedViews.get(mCurrentBufferIndex),
							mCurrentAdapterIndex,direction);
		}
		logBuffer();
	}

	private void recycleView(View toRecycle) {
		int viewType = ((ViewFlow.LayoutParams)toRecycle.getLayoutParams()).viewType;
		detachViewFromParent(toRecycle);
		List<View> viewsOfLikeType = mRecycledViews[viewType];
		if(viewsOfLikeType.size() < numVisibleViews){
			// TODO maybe store "removed from index"
			mRecycledViews[viewType].add(toRecycle);
		} else {
			removeDetachedView(toRecycle,false);
		}
	}

	private View setupChild(View child, boolean addToEnd, boolean recycle, int viewType) {
		ViewGroup.LayoutParams p = (ViewGroup.LayoutParams) child
				.getLayoutParams();
		if (p == null) {
			p = new ViewFlow.LayoutParams(
					ViewFlow.LayoutParams.FILL_PARENT,
					ViewFlow.LayoutParams.WRAP_CONTENT, viewType);
		} else {
			if(!(p instanceof ViewFlow.LayoutParams)){
				p = new ViewFlow.LayoutParams(p,viewType);
			}
		}
		
		if (recycle)
			attachViewToParent(child, (addToEnd ? -1 : 0), p);
		else
			addViewInLayout(child, (addToEnd ? -1 : 0), p, true);
		return child;
	}

	private View makeAndAddView(int position, boolean addToEnd) {
		
		// pull a recycled view of like type, or null if none
		View convertView = null;
		ArrayList<View> viewsOfLikeType = mRecycledViews[mAdapter.getItemViewType(position)];
		
		// TODO come up with a smarter way of retaining these views
		// since there is a significant chance that it's identical
		// to the one we are adding
		if(viewsOfLikeType.size() > 0){
			convertView = viewsOfLikeType.get(0);
			viewsOfLikeType.remove(0);
		}
		
		// pass the recycled view
		View view = mAdapter.getView(position, convertView, this);
		
		return setupChild(view, addToEnd, convertView != null, mAdapter.getItemViewType(position));
	}

	class AdapterDataSetObserver extends DataSetObserver {

		@Override
		public void onChanged() {
            if(indeterminate)
            {
//                IndeterminateAdapter iAdapter = (IndeterminateAdapter)mAdapter;
//                for(int index = iAdapter.getLeftMostIndex(); index <= iAdapter.getRightMostIndex(); index++)
//                {
//                    if(mCurrentId == mAdapter.getItemId(index))
//                    {
//                        mCurrentAdapterIndex = index;
//                        setSelection(index);
//                        break;
//                    }
//                }

//                resetFocus();
                setSelection(mCurrentAdapterIndex);
                return;
            }

//			View v = getChildAt(mCurrentBufferIndex);
//			if (v != null) {
//				for (int index = 0; index < mAdapter.getCount(); index++) {
//					if (v.equals(mAdapter.getItem(index))) {
//						mCurrentAdapterIndex = index;
//						break;
//					}
//				}
//			}
			
//			Log.d("FetLife","Data set changed");
			if(mAdapter.hasStableIds()){
				// use the ID from where we currently are in the (old) adapter.
				for(int index = 0; index < mAdapter.getCount(); index++) {
					if(mCurrentId == mAdapter.getItemId(index)){
//						Log.d("FetLife","Found matching ID at adapter index " + index);
						mCurrentAdapterIndex = index;
						
						
						setSelection(index);
						break;
					}
				}
				
				resetFocus();
				setSelection(mCurrentAdapterIndex);
				
			} else {
				// not sure of precisely what this logic does.  looks like it attempts
				// what we do above, based on the adapter, but comparing a getItem to a 
				// view doesn't make much sense.
				View v = getChildAt(mCurrentBufferIndex);
				if (v != null) {
					for (int index = 0; index < mAdapter.getCount(); index++) {
						if (v.equals(mAdapter.getItem(index))) {
							mCurrentAdapterIndex = index;
							break;
						}
					}
				}
				resetFocus();
			}
			
		}

		@Override
		public void onInvalidated() {
			// Not yet implemented!
		}

	}

	private void logBuffer() {

		Log.d("viewflow", "Size of mLoadedViews: " + mLoadedViews.size() +
				"X: " + mScroller.getCurrX() + ", Y: " + mScroller.getCurrY());
		Log.d("viewflow", "IndexInAdapter: " + mCurrentAdapterIndex
				+ ", IndexInBuffer: " + mCurrentBufferIndex);
	}
	
  /**
  * ViewFlow extends LayoutParams to provide a place to hold the view type.
  */
 public static class LayoutParams extends ViewGroup.LayoutParams {
     /**
      * View type for this view, as returned by
      * {@link android.widget.Adapter#getItemViewType(int) }
      */
     int viewType;

     /**
      * When an AbsListView is measured with an AT_MOST measure spec, it needs
      * to obtain children views to measure itself. When doing so, the children
      * are not attached to the window, but put in the recycler which assumes
      * they've been attached before. Setting this flag will force the reused
      * view to be attached to the window rather than just attached to the
      * parent.
      */
     boolean forceAdd;

     /**
      * The position the view was removed from when pulled out of the
      * scrap heap.
      * @hide
      */
     int scrappedFromPosition;

     public LayoutParams(Context c, AttributeSet attrs) {
         super(c, attrs);
     }

     public LayoutParams(int w, int h) {
         super(w, h);
     }

     public LayoutParams(int w, int h, int viewType) {
         super(w, h);
         this.viewType = viewType;
     }

     public LayoutParams(ViewGroup.LayoutParams source) {
         super(source);
     }
     
     public LayoutParams(ViewGroup.LayoutParams source, int viewType) {
         super(source);
         this.viewType = viewType;
     }
 }
 
	/**
	 * A SavedState which save and load the current screen
	 */
	public static class SavedState extends BaseSavedState {
		int currentScreen = -1;

		/**
		 * Internal constructor
		 * 
		 * @param superState
		 */
		SavedState(Parcelable superState) {
			super(superState);
		}

		/**
		 * Private constructor
		 * 
		 * @param in
		 */
		private SavedState(Parcel in) {
			super(in);
			currentScreen = in.readInt();
		}

		/**
		 * Save the current screen
		 */
		@Override
		public void writeToParcel(Parcel out, int flags) {
			super.writeToParcel(out, flags);
			out.writeInt(currentScreen);
		}

		/**
		 * Return a Parcelable creator
		 */
		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			@Override
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			@Override
			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}

}
