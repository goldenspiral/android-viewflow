///*
// * Copyright (C) 2011 Patrik Akerfeldt
// * 
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package org.taptwo.android.widget;
//
//import java.util.ArrayList;
//import java.util.LinkedList;
//import java.util.List;
//
//import org.taptwo.android.widget.viewflow.R;
//
//import android.content.Context;
//import android.content.res.Configuration;
//import android.content.res.TypedArray;
//import android.database.DataSetObserver;
//import android.util.AttributeSet;
//import android.util.Log;
//import android.view.MotionEvent;
//import android.view.VelocityTracker;
//import android.view.View;
//import android.view.ViewConfiguration;
//import android.view.ViewGroup;
//import android.view.ViewTreeObserver.OnGlobalLayoutListener;
//import android.widget.AbsListView.RecyclerListener;
//import android.widget.Adapter;
//import android.widget.AdapterView;
//import android.widget.Scroller;
//
///**
// * A horizontally scrollable {@link ViewGroup} with items populated from an
// * {@link Adapter}. The ViewFlow uses a buffer to store loaded {@link View}s in.
// * The default size of the buffer is 3 elements on both sides of the currently
// * visible {@link View}, making up a total buffer size of 3 * 2 + 1 = 7. The
// * buffer size can be changed using the {@code sidebuffer} xml attribute.
// * 
// */
//public class ViewFlow extends AdapterView<Adapter> {
//
//	private static final int SNAP_VELOCITY = 1000;
//	private static final int INVALID_SCREEN = -1;
//	private final static int TOUCH_STATE_REST = 0;
//	private final static int TOUCH_STATE_SCROLLING = 1;
//
//	private LinkedList<View> mLoadedViews;
//	private int mCurrentBufferIndex;
//	private int mCurrentAdapterIndex;
//	private int mSideBuffer = 2;
//	private Scroller mScroller;
//	private VelocityTracker mVelocityTracker;
//	private int mTouchState = TOUCH_STATE_REST;
//	private float mLastMotionX;
//	private int mTouchSlop;
//	private int mMaximumVelocity;
//	private int mCurrentScreen;
//	private int mNextScreen = INVALID_SCREEN;
//	private boolean mFirstLayout = true;
//	private ViewSwitchListener mViewSwitchListener;
//	private Adapter mAdapter;
//	private int mLastScrollDirection;
//	private AdapterDataSetObserver mDataSetObserver;
//	private FlowIndicator mIndicator;
//	private int mLastOrientation = -1;
//
//	private OnGlobalLayoutListener orientationChangeListener = new OnGlobalLayoutListener() {
//
//		@Override
//		public void onGlobalLayout() {
//			getViewTreeObserver().removeGlobalOnLayoutListener(
//					orientationChangeListener);
//			setSelection(mCurrentAdapterIndex);
//		}
//	};
//
//
//	private ViewFlow.RecycleBin mRecycler = new RecycleBin();	/**
//	 * Receives call backs when a new {@link View} has been scrolled to.
//	 */
//	public static interface ViewSwitchListener {
//
//		/**
//		 * This method is called when a new View has been scrolled to.
//		 * 
//		 * @param view
//		 *            the {@link View} currently in focus.
//		 * @param position
//		 *            The position in the adapter of the {@link View} currently in focus.
//		 */
//		void onSwitched(View view, int position);
//
//	}
//
//	public ViewFlow(Context context) {
//		super(context);
//		mSideBuffer = 3;
//		init();
//	}
//
//	public ViewFlow(Context context, int sideBuffer) {
//		super(context);
//		mSideBuffer = sideBuffer;
//		init();
//	}
//
//	public ViewFlow(Context context, AttributeSet attrs) {
//		super(context, attrs);
//		TypedArray styledAttrs = context.obtainStyledAttributes(attrs,
//				R.styleable.ViewFlow);
//		mSideBuffer = styledAttrs.getInt(R.styleable.ViewFlow_sidebuffer, 3);
//		init();
//	}
//
//	private void init() {
//		mLoadedViews = new LinkedList<View>();
//		mScroller = new Scroller(getContext());
//		final ViewConfiguration configuration = ViewConfiguration
//				.get(getContext());
//		mTouchSlop = configuration.getScaledTouchSlop();
//		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
//	}
//
//	public void onConfigurationChanged(Configuration newConfig) {
//		if (newConfig.orientation != mLastOrientation) {
//			mLastOrientation = newConfig.orientation;
//			getViewTreeObserver().addOnGlobalLayoutListener(orientationChangeListener);
//		}
//	}
//
//	public int getViewsCount() {
//		return mAdapter.getCount();
//	}
//
//	@Override
//	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//		
//		final int width = MeasureSpec.getSize(widthMeasureSpec);
//		final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
//		if (widthMode != MeasureSpec.EXACTLY && !isInEditMode()) {
//			throw new IllegalStateException(
//					"ViewFlow can only be used in EXACTLY mode.");
//		}
//
//		final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
//		if (heightMode != MeasureSpec.EXACTLY && !isInEditMode()) {
//			throw new IllegalStateException(
//					"ViewFlow can only be used in EXACTLY mode.");
//		}
//
//		// The children are given the same width and height as the workspace
//		final int count = getChildCount();
//		for (int i = 0; i < count; i++) {
//			getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
//		}
//
//		if (mFirstLayout) {
//			mScroller.startScroll(0, 0, mCurrentScreen * width, 0, 0);
//			mFirstLayout = false;
//		}
//	}
//
//	@Override
//	protected void onLayout(boolean changed, int l, int t, int r, int b) {
//		int childLeft = 0;
//
//		final int count = getChildCount();
//		for (int i = 0; i < count; i++) {
//			final View child = getChildAt(i);
//			if (child.getVisibility() != View.GONE) {
//				final int childWidth = child.getMeasuredWidth();
//				child.layout(childLeft, 0, childLeft + childWidth,
//						child.getMeasuredHeight());
//				childLeft += childWidth;
//			}
//		}
//	}
//
//	@Override
//	public boolean onInterceptTouchEvent(MotionEvent ev) {
//		if (getChildCount() == 0)
//			return false;
//
//		if (mVelocityTracker == null) {
//			mVelocityTracker = VelocityTracker.obtain();
//		}
//		mVelocityTracker.addMovement(ev);
//
//		final int action = ev.getAction();
//		final float x = ev.getX();
//
//		switch (action) {
//		case MotionEvent.ACTION_DOWN:
//			/*
//			 * If being flinged and user touches, stop the fling. isFinished
//			 * will be false if being flinged.
//			 */
//			if (!mScroller.isFinished()) {
//				mScroller.abortAnimation();
//			}
//
//			// Remember where the motion event started
//			mLastMotionX = x;
//
//			mTouchState = mScroller.isFinished() ? TOUCH_STATE_REST
//					: TOUCH_STATE_SCROLLING;
//
//			break;
//
//		case MotionEvent.ACTION_MOVE:
//			final int xDiff = (int) Math.abs(x - mLastMotionX);
//
//			boolean xMoved = xDiff > mTouchSlop;
//
//			if (xMoved) {
//				// Scroll if the user moved far enough along the X axis
//				mTouchState = TOUCH_STATE_SCROLLING;
//			}
//
//			if (mTouchState == TOUCH_STATE_SCROLLING) {
//				// Scroll to follow the motion event
//				final int deltaX = (int) (mLastMotionX - x);
//				mLastMotionX = x;
//
//				final int scrollX = getScrollX();
//				if (deltaX < 0) {
//					if (scrollX > 0) {
//						scrollBy(Math.max(-scrollX, deltaX), 0);
//					}
//				} else if (deltaX > 0) {
//					final int availableToScroll = getChildAt(
//							getChildCount() - 1).getRight()
//							- scrollX - getWidth();
//					if (availableToScroll > 0) {
//						scrollBy(Math.min(availableToScroll, deltaX), 0);
//					}
//				}
//				return true;
//			}
//			break;
//
//		case MotionEvent.ACTION_UP:
//			if (mTouchState == TOUCH_STATE_SCROLLING) {
//				final VelocityTracker velocityTracker = mVelocityTracker;
//				velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
//				int velocityX = (int) velocityTracker.getXVelocity();
//
//				if (velocityX > SNAP_VELOCITY && mCurrentScreen > 0) {
//					// Fling hard enough to move left
//					snapToScreen(mCurrentScreen - 1);
//				} else if (velocityX < -SNAP_VELOCITY
//						&& mCurrentScreen < getChildCount() - 1) {
//					// Fling hard enough to move right
//					snapToScreen(mCurrentScreen + 1);
//				} else {
//					snapToDestination();
//				}
//
//				if (mVelocityTracker != null) {
//					mVelocityTracker.recycle();
//					mVelocityTracker = null;
//				}
//			}
//
//			mTouchState = TOUCH_STATE_REST;
//
//			break;
//		case MotionEvent.ACTION_CANCEL:
//			mTouchState = TOUCH_STATE_REST;
//		}
//		return false;
//	}
//
//	@Override
//	public boolean onTouchEvent(MotionEvent ev) {
//		if (getChildCount() == 0)
//			return false;
//
//		if (mVelocityTracker == null) {
//			mVelocityTracker = VelocityTracker.obtain();
//		}
//		mVelocityTracker.addMovement(ev);
//
//		final int action = ev.getAction();
//		final float x = ev.getX();
//
//		switch (action) {
//		case MotionEvent.ACTION_DOWN:
//			/*
//			 * If being flinged and user touches, stop the fling. isFinished
//			 * will be false if being flinged.
//			 */
//			if (!mScroller.isFinished()) {
//				mScroller.abortAnimation();
//			}
//
//			// Remember where the motion event started
//			mLastMotionX = x;
//
//			mTouchState = mScroller.isFinished() ? TOUCH_STATE_REST
//					: TOUCH_STATE_SCROLLING;
//
//			break;
//
//		case MotionEvent.ACTION_MOVE:
//			final int xDiff = (int) Math.abs(x - mLastMotionX);
//
//			boolean xMoved = xDiff > mTouchSlop;
//
//			if (xMoved) {
//				// Scroll if the user moved far enough along the X axis
//				mTouchState = TOUCH_STATE_SCROLLING;
//			}
//
//			if (mTouchState == TOUCH_STATE_SCROLLING) {
//				// Scroll to follow the motion event
//				final int deltaX = (int) (mLastMotionX - x);
//				mLastMotionX = x;
//
//				final int scrollX = getScrollX();
//				if (deltaX < 0) {
//					if (scrollX > 0) {
//						scrollBy(Math.max(-scrollX, deltaX), 0);
//					}
//				} else if (deltaX > 0) {
//					final int availableToScroll = getChildAt(
//							getChildCount() - 1).getRight()
//							- scrollX - getWidth();
//					if (availableToScroll > 0) {
//						scrollBy(Math.min(availableToScroll, deltaX), 0);
//					}
//				}
//				return true;
//			}
//			break;
//
//		case MotionEvent.ACTION_UP:
//			if (mTouchState == TOUCH_STATE_SCROLLING) {
//				final VelocityTracker velocityTracker = mVelocityTracker;
//				velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
//				int velocityX = (int) velocityTracker.getXVelocity();
//
//				if (velocityX > SNAP_VELOCITY && mCurrentScreen > 0) {
//					// Fling hard enough to move left
//					snapToScreen(mCurrentScreen - 1);
//				} else if (velocityX < -SNAP_VELOCITY
//						&& mCurrentScreen < getChildCount() - 1) {
//					// Fling hard enough to move right
//					snapToScreen(mCurrentScreen + 1);
//				} else {
//					snapToDestination();
//				}
//
//				if (mVelocityTracker != null) {
//					mVelocityTracker.recycle();
//					mVelocityTracker = null;
//				}
//			}
//
//			mTouchState = TOUCH_STATE_REST;
//
//			break;
//		case MotionEvent.ACTION_CANCEL:
//			snapToDestination();
//			mTouchState = TOUCH_STATE_REST;
//		}
//		return true;
//	}
//
//	@Override
//	protected void onScrollChanged(int h, int v, int oldh, int oldv) {
//		super.onScrollChanged(h, v, oldh, oldv);
//		if (mIndicator != null) {
//			/*
//			 * The actual horizontal scroll origin does typically not match the
//			 * perceived one. Therefore, we need to calculate the perceived
//			 * horizontal scroll origin here, since we use a view buffer.
//			 */
//			int hPerceived = h + (mCurrentAdapterIndex - mCurrentBufferIndex)
//					* getWidth();
//			mIndicator.onScrolled(hPerceived, v, oldh, oldv);
//		}
//	}
//
//	private void snapToDestination() {
//		final int screenWidth = getWidth();
//		final int whichScreen = (getScrollX() + (screenWidth / 2))
//				/ screenWidth;
//
//		snapToScreen(whichScreen);
//	}
//
//	private void snapToScreen(int whichScreen) {
//		mLastScrollDirection = whichScreen - mCurrentScreen;
//		if (!mScroller.isFinished())
//			return;
//
//		whichScreen = Math.max(0, Math.min(whichScreen, getChildCount() - 1));
//
//		mNextScreen = whichScreen;
//
//		final int newX = whichScreen * getWidth();
//		final int delta = newX - getScrollX();
//		mScroller.startScroll(getScrollX(), 0, delta, 0, Math.abs(delta) * 2);
//		invalidate();
//	}
//
//	@Override
//	public void computeScroll() {
//		if (mScroller.computeScrollOffset()) {
//			scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
//			postInvalidate();
//		} else if (mNextScreen != INVALID_SCREEN) {
//			mCurrentScreen = Math.max(0,
//					Math.min(mNextScreen, getChildCount() - 1));
//			mNextScreen = INVALID_SCREEN;
//			postViewSwitched(mLastScrollDirection);
//		}
//	}
//
//	/**
//	 * Scroll to the {@link View} in the view buffer specified by the index.
//	 * 
//	 * @param indexInBuffer
//	 *            Index of the view in the view buffer.
//	 */
//	private void setVisibleView(int indexInBuffer, boolean uiThread) {
//		mCurrentScreen = Math.max(0,
//				Math.min(indexInBuffer, getChildCount() - 1));
//		int dx = (mCurrentScreen * getWidth()) - mScroller.getCurrX();
//		mScroller.startScroll(mScroller.getCurrX(), mScroller.getCurrY(), dx,
//				0, 0);
//		if(dx == 0)
//			onScrollChanged(mScroller.getCurrX() + dx, mScroller.getCurrY(), mScroller.getCurrX() + dx, mScroller.getCurrY());
//		if (uiThread)
//			invalidate();
//		else
//			postInvalidate();
//	}
//
//	/**
//	 * Set the listener that will receive notifications every time the {code
//	 * ViewFlow} scrolls.
//	 * 
//	 * @param l
//	 *            the scroll listener
//	 */
//	public void setOnViewSwitchListener(ViewSwitchListener l) {
//		mViewSwitchListener = l;
//	}
//
//	@Override
//	public Adapter getAdapter() {
//		return mAdapter;
//	}
//
//	@Override
//	public void setAdapter(Adapter adapter) {
//		setAdapter(adapter, 0);
//	}
//	
//	public void setAdapter(Adapter adapter, int initialPosition) {
//		if (mAdapter != null) {
//			mAdapter.unregisterDataSetObserver(mDataSetObserver);
//		}
//
//		mAdapter = adapter;
//
//		if (mAdapter != null) {
//			mDataSetObserver = new AdapterDataSetObserver();
//			mAdapter.registerDataSetObserver(mDataSetObserver);
//
//		}
//		
//		if (mAdapter == null || mAdapter.getCount() == 0)
//			return;
//		
//		mRecycler.setViewTypeCount(mAdapter.getViewTypeCount());
//		
//		setSelection(initialPosition);		
//	}
//	
//	@Override
//	public View getSelectedView() {
//		return (mCurrentBufferIndex < mLoadedViews.size() ? mLoadedViews
//				.get(mCurrentBufferIndex) : null);
//	}
//
//    @Override
//    public int getSelectedItemPosition() {
//        return mCurrentAdapterIndex;
//    }
//
//	/**
//	 * Set the FlowIndicator
//	 * 
//	 * @param flowIndicator
//	 */
//	public void setFlowIndicator(FlowIndicator flowIndicator) {
//		mIndicator = flowIndicator;
//		mIndicator.setViewFlow(this);
//	}
//
//	@Override
//	public void setSelection(int position) {
//		mNextScreen = INVALID_SCREEN;
//		mScroller.forceFinished(true);
//		if (mAdapter == null)
//			return;
//		
//		position = Math.max(position, 0);
//		position =  Math.min(position, mAdapter.getCount()-1);
//
//		
//		//recycle all current views
//		mRecycler.scrapActiveViews();
////		ArrayList<View> recycleViews = new ArrayList<View>();
////		View recycleView;
////		while (!mLoadedViews.isEmpty()) {
////			recycleViews.add(recycleView = mLoadedViews.remove());
////			detachViewFromParent(recycleView);
////		}
//
//		// create the current view
////		View currentView = makeAndAddView(position, true,
////				(recycleViews.isEmpty() ? null : recycleViews.remove(0)));
////		mLoadedViews.addLast(currentView);
//		
//		// add views in the sidebuffer
//		mRecycler.fillActiveViews(mSideBuffer * 2 + 1, position - mSideBuffer);
//		
////		for(int offset = 1; mSideBuffer - offset >= 0; offset++) {
////			int leftIndex = position - offset;
////			int rightIndex = position + offset;
////			if(leftIndex >= 0)
////				mLoadedViews.addFirst(makeAndAddView(leftIndex, false,
////						(recycleViews.isEmpty() ? null : recycleViews.remove(0))));
////			if(rightIndex < mAdapter.getCount())
////				mLoadedViews.addLast(makeAndAddView(rightIndex, true,
////						(recycleViews.isEmpty() ? null : recycleViews.remove(0))));
////		}
//		
//		View currentView = mRecycler.getActiveView(position);
//
//		mCurrentBufferIndex = mRecycler.mActiveViews.indexOf(currentView);
//		mCurrentAdapterIndex = position;
//
////		for (View view : recycleViews) {
////			removeDetachedView(view, false);
////		}
//		requestLayout();
//		setVisibleView(mCurrentBufferIndex, false);
//		notifySwitched();
//	}
//	
//	private void notifySwitched(){
//		if (mIndicator != null) {
//			mIndicator.onSwitched(mRecycler.mActiveViews.get(mCurrentBufferIndex),
//					mCurrentAdapterIndex);
//		}
//		if (mViewSwitchListener != null) {
//			mViewSwitchListener
//					.onSwitched(mRecycler.mActiveViews.get(mCurrentBufferIndex),
//							mCurrentAdapterIndex);
//		}
//	}
//
//	private void resetFocus() {
//		logBuffer();
//		mRecycler.clear();
////		mLoadedViews.clear();
//		removeAllViewsInLayout();
//
//		mRecycler.fillActiveViews(2*mSideBuffer+1, Math.max(0, mCurrentAdapterIndex - mSideBuffer));
//		
////		for (int i = Math.max(0, mCurrentAdapterIndex - mSideBuffer); i < Math
////				.min(mAdapter.getCount(), mCurrentAdapterIndex + mSideBuffer
////						+ 1); i++) {
////			mLoadedViews.addLast(makeAndAddView(i, true, null));
////			
////			// TODO REVISIT THIS LINE
////			if (i == mCurrentAdapterIndex)
////				mCurrentBufferIndex = mLoadedViews.size() - 1;
////		}
//		logBuffer();
//		requestLayout();
//	}
//
//	private void postViewSwitched(int direction) {
//		if (direction == 0)
//			return;
//
//		if (direction > 0) { // to the right
//			mCurrentAdapterIndex++;
//			mCurrentBufferIndex++;
//
//			View recycleView = null;
//
//			// Remove view outside buffer range
//			if (mCurrentAdapterIndex > mSideBuffer) {
//				recycleView = mLoadedViews.removeFirst();
//				detachViewFromParent(recycleView);
//				// removeView(recycleView);
//				mCurrentBufferIndex--;
//			}
//
//			// Add new view to buffer
//			int newBufferIndex = mCurrentAdapterIndex + mSideBuffer;
//			if (newBufferIndex < mAdapter.getCount())
//				mLoadedViews.addLast(makeAndAddView(newBufferIndex, true,
//						recycleView));
//
//		} else { // to the left
//			mCurrentAdapterIndex--;
//			mCurrentBufferIndex--;
//			View recycleView = null;
//
//			// Remove view outside buffer range
//			if (mAdapter.getCount() - 1 - mCurrentAdapterIndex > mSideBuffer) {
//				recycleView = mLoadedViews.removeLast();
//				detachViewFromParent(recycleView);
//			}
//
//			// Add new view to buffer
//			int newBufferIndex = mCurrentAdapterIndex - mSideBuffer;
//			if (newBufferIndex > -1) {
//				mLoadedViews.addFirst(makeAndAddView(newBufferIndex, false,
//						recycleView));
//				mCurrentBufferIndex++;
//			}
//
//		}
//
//		requestLayout();
//		setVisibleView(mCurrentBufferIndex, true);
//		notifySwitched();
//		logBuffer();
//	}
//
//	private View setupChild(View child, boolean addToEnd, boolean recycle) {
//		ViewFlow.LayoutParams p = (ViewFlow.LayoutParams) child
//				.getLayoutParams();
//		if (p == null) {
//			p = new ViewFlow.LayoutParams(
//					ViewGroup.LayoutParams.FILL_PARENT,
//					ViewGroup.LayoutParams.WRAP_CONTENT, 0);
//		}
//		
//		if (recycle)
//			attachViewToParent(child, (addToEnd ? -1 : 0), p);
//		else
//			addViewInLayout(child, (addToEnd ? -1 : 0), p, true);
//		return child;
//	}
//
//	private View makeAndAddView(int position, boolean addToEnd, View convertView) {
//		View view = mAdapter.getView(position, convertView, this);
//		
//        View child;
//
//
////        if (!mDataChanged) {
////            // Try to use an existing view for this position
////            child = mRecycler.getActiveView(position);
////            if (child != null) {
////                
////
////                // Found it -- we're using an existing child
////                // This just needs to be positioned
////                setupChild(child, position, y, flow, childrenLeft, selected, true);
////
////                return child;
////            }
////        }
//
//        // Make a new view for this position, or convert an unused view if possible
//        child = obtainView(position, mIsScrap);
//
//        // This needs to be positioned and measured
//        setupChild(child, position, y, flow, childrenLeft, selected, mIsScrap[0]);
//
//        return child;
//		return setupChild(view, addToEnd, convertView != null);
//	}
//
//	class AdapterDataSetObserver extends DataSetObserver {
//
//		@Override
//		public void onChanged() {
//			View v = getChildAt(mCurrentBufferIndex);
//			if (v != null) {
//				for (int index = 0; index < mAdapter.getCount(); index++) {
//					if (v.equals(mAdapter.getItem(index))) {
//						mCurrentAdapterIndex = index;
//						break;
//					}
//				}
//			}
//			resetFocus();
//		}
//
//		@Override
//		public void onInvalidated() {
//			// Not yet implemented!
//		}
//
//	}
//
//	private void logBuffer() {
//
//		Log.d("viewflow", "Size of mLoadedViews: " + mLoadedViews.size() +
//				"X: " + mScroller.getCurrX() + ", Y: " + mScroller.getCurrY());
//		Log.d("viewflow", "IndexInAdapter: " + mCurrentAdapterIndex
//				+ ", IndexInBuffer: " + mCurrentBufferIndex);
//	}
//	
//	
//	/**
//     * Get a view and have it show the data associated with the specified
//     * position. This is called when we have already discovered that the view is
//     * not available for reuse in the recycle bin. The only choices left are
//     * converting an old view or making a new one.
//     *
//     * @param position The position to display
//     * @param isScrap Array of at least 1 boolean, the first entry will become true if
//     *                the returned view was taken from the scrap heap, false if otherwise.
//     *
//     * @return A view displaying the data associated with the specified position
//     */
//    View obtainView(int position, boolean[] isScrap) {
//        isScrap[0] = false;
//        View scrapView;
//
//        scrapView = mRecycler.getScrapView(position);
//
//        View child;
//        if (scrapView != null) {
//
//
//            child = mAdapter.getView(position, scrapView, this);
//
//
//            if (child != scrapView) {
//                mRecycler.addScrapView(scrapView, position);
//            } else {
//                isScrap[0] = true;
////                child.dispatchFinishTemporaryDetach();
//            }
//        } else {
//            child = mAdapter.getView(position, null, this);
//        }
//
//        return child;
//    }
//    
//    /**
//     * AbsListView extends LayoutParams to provide a place to hold the view type.
//     */
//    public static class LayoutParams extends ViewGroup.LayoutParams {
//        /**
//         * View type for this view, as returned by
//         * {@link android.widget.Adapter#getItemViewType(int) }
//         */
//        int viewType;
//
//        /**
//         * When this boolean is set, the view has been added to the AbsListView
//         * at least once. It is used to know whether headers/footers have already
//         * been added to the list view and whether they should be treated as
//         * recycled views or not.
//         */
//        boolean recycledHeaderFooter;
//
//        /**
//         * When an AbsListView is measured with an AT_MOST measure spec, it needs
//         * to obtain children views to measure itself. When doing so, the children
//         * are not attached to the window, but put in the recycler which assumes
//         * they've been attached before. Setting this flag will force the reused
//         * view to be attached to the window rather than just attached to the
//         * parent.
//         */
//        boolean forceAdd;
//
//        /**
//         * The position the view was removed from when pulled out of the
//         * scrap heap.
//         * @hide
//         */
//        int scrappedFromPosition;
//
//        public LayoutParams(Context c, AttributeSet attrs) {
//            super(c, attrs);
//        }
//
//        public LayoutParams(int w, int h) {
//            super(w, h);
//        }
//
//        public LayoutParams(int w, int h, int viewType) {
//            super(w, h);
//            this.viewType = viewType;
//        }
//
//        public LayoutParams(ViewGroup.LayoutParams source) {
//            super(source);
//        }
//    }
//	
//	
//    /**
//     * The RecycleBin facilitates reuse of views across layouts. The RecycleBin has two levels of
//     * storage: ActiveViews and ScrapViews. ActiveViews are those views which were onscreen at the
//     * start of a layout. By construction, they are displaying current information. At the end of
//     * layout, all views in ActiveViews are demoted to ScrapViews. ScrapViews are old views that
//     * could potentially be used by the adapter to avoid allocating views unnecessarily.
//     *
//     * @see android.widget.AbsListView#setRecyclerListener(android.widget.AbsListView.RecyclerListener)
//     * @see android.widget.AbsListView.RecyclerListener
//     */
//    class RecycleBin {
//        private RecyclerListener mRecyclerListener;
//
//        /**
//         * The position of the first view stored in mActiveViews.
//         */
//        private int mFirstActivePosition;
//
//        /**
//         * Views that were on screen at the start of layout. This array is populated at the start of
//         * layout, and at the end of layout all view in mActiveViews are moved to mScrapViews.
//         * Views in mActiveViews represent a contiguous range of Views, with position of the first
//         * view store in mFirstActivePosition.
//         */
//        private ArrayList<View> mActiveViews = new ArrayList<View>(0);
//
//        /**
//         * Unsorted views that can be used by the adapter as a convert view.
//         */
//        private ArrayList<View>[] mScrapViews;
//
//        private int mViewTypeCount;
//
//        private ArrayList<View> mCurrentScrap;
//
//        public void setViewTypeCount(int viewTypeCount) {
//            if (viewTypeCount < 1) {
//                throw new IllegalArgumentException("Can't have a viewTypeCount < 1");
//            }
//            //noinspection unchecked
//            ArrayList<View>[] scrapViews = new ArrayList[viewTypeCount];
//            for (int i = 0; i < viewTypeCount; i++) {
//                scrapViews[i] = new ArrayList<View>();
//            }
//            mViewTypeCount = viewTypeCount;
//            mCurrentScrap = scrapViews[0];
//            mScrapViews = scrapViews;
//        }
//
//        public void markChildrenDirty() {
//            if (mViewTypeCount == 1) {
//                final ArrayList<View> scrap = mCurrentScrap;
//                final int scrapCount = scrap.size();
//                for (int i = 0; i < scrapCount; i++) {
//                    scrap.get(i).forceLayout();
//                }
//            } else {
//                final int typeCount = mViewTypeCount;
//                for (int i = 0; i < typeCount; i++) {
//                    final ArrayList<View> scrap = mScrapViews[i];
//                    final int scrapCount = scrap.size();
//                    for (int j = 0; j < scrapCount; j++) {
//                        scrap.get(j).forceLayout();
//                    }
//                }
//            }
//        }
//
//        public boolean shouldRecycleViewType(int viewType) {
//            return viewType >= 0;
//        }
//
//        /**
//         * Clears the scrap heap.
//         */
//        void clear() {
//            if (mViewTypeCount == 1) {
//                final ArrayList<View> scrap = mCurrentScrap;
//                final int scrapCount = scrap.size();
//                for (int i = 0; i < scrapCount; i++) {
//                    removeDetachedView(scrap.remove(scrapCount - 1 - i), false);
//                }
//            } else {
//                final int typeCount = mViewTypeCount;
//                for (int i = 0; i < typeCount; i++) {
//                    final ArrayList<View> scrap = mScrapViews[i];
//                    final int scrapCount = scrap.size();
//                    for (int j = 0; j < scrapCount; j++) {
//                        removeDetachedView(scrap.remove(scrapCount - 1 - j), false);
//                    }
//                }
//            }
//        }
//
//        /**
//         * Fill ActiveViews with all of the children of the AbsListView.
//         *
//         * @param childCount The minimum number of views mActiveViews should hold
//         * @param firstActivePosition The position of the first view that will be stored in
//         *        mActiveViews
//         */
//        void fillActiveViews(int childCount, int firstActivePosition) {
//            if (mActiveViews.size() < childCount) {
//                mActiveViews = new ArrayList<View>(childCount);
//            }
//            mFirstActivePosition = firstActivePosition;
//
//            final ArrayList<View> activeViews = mActiveViews;
//            for (int i = 0; i < childCount; i++) {
//                View child = getChildAt(i);
//                ViewFlow.LayoutParams lp = (ViewFlow.LayoutParams) child.getLayoutParams();
//                // Don't put header or footer views into the scrap heap
//                if (lp != null && lp.viewType != ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
//                    // Note:  We do place AdapterView.ITEM_VIEW_TYPE_IGNORE in active views.
//                    //        However, we will NOT place them into scrap views.
//                    activeViews.set(i, child);
//                }
//            }
//        }
//
//        /**
//         * Get the view corresponding to the specified position. The view will be removed from
//         * mActiveViews if it is found.
//         *
//         * @param position The position to look up in mActiveViews
//         * @return The view if it is found, null otherwise
//         */
//        View getActiveView(int position) {
//            int index = position - mFirstActivePosition;
//            final ArrayList<View> activeViews = mActiveViews;
//            if (index >=0 && index < activeViews.size()) {
//                final View match = activeViews.get(index);
//                activeViews.set(index, null);
//                return match;
//            }
//            return null;
//        }
//
//        /**
//         * @return A view from the ScrapViews collection. These are unordered.
//         */
//        View getScrapView(int position) {
//            if (mViewTypeCount == 1) {
//                return retrieveFromScrap(mCurrentScrap, position);
//            } else {
//                int whichScrap = mAdapter.getItemViewType(position);
//                if (whichScrap >= 0 && whichScrap < mScrapViews.length) {
//                    return retrieveFromScrap(mScrapViews[whichScrap], position);
//                }
//            }
//            return null;
//        }
//
//        /**
//         * Put a view into the ScapViews list. These views are unordered.
//         *
//         * @param scrap The view to add
//         */
//        void addScrapView(View scrap, int position) {
//        	ViewFlow.LayoutParams lp = (ViewFlow.LayoutParams) scrap.getLayoutParams();
//            if (lp == null) {
//                return;
//            }
//
//            // Don't put header or footer views or views that should be ignored
//            // into the scrap heap
//            int viewType = lp.viewType;
//            if (!shouldRecycleViewType(viewType)) {
//                if (viewType != ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
//                    removeDetachedView(scrap, false);
//                }
//                return;
//            }
//
//            lp.scrappedFromPosition = position;
//
//            if (mViewTypeCount == 1) {
////                scrap.dispatchStartTemporaryDetach();
//                mCurrentScrap.add(scrap);
//            } else {
////                scrap.dispatchStartTemporaryDetach();
//                mScrapViews[viewType].add(scrap);
//            }
//
//            if (mRecyclerListener != null) {
//                mRecyclerListener.onMovedToScrapHeap(scrap);
//            }
//        }
//
//        /**
//         * Move all views remaining in mActiveViews to mScrapViews.
//         */
//        void scrapActiveViews() {
//            final ArrayList<View> activeViews = mActiveViews;
//            final boolean hasListener = mRecyclerListener != null;
//            final boolean multipleScraps = mViewTypeCount > 1;
//
//            ArrayList<View> scrapViews = mCurrentScrap;
//            final int count = activeViews.size();
//            for (int i = count - 1; i >= 0; i--) {
//                final View victim = activeViews.get(i);
//                if (victim != null) {
//                    final ViewFlow.LayoutParams lp
//                            = (ViewFlow.LayoutParams) victim.getLayoutParams();
//                    int whichScrap = lp.viewType;
//
//                    activeViews.set(i, null);
//
//                    if (!shouldRecycleViewType(whichScrap)) {
//                        // Do not move views that should be ignored
//                        if (whichScrap != ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
//                            removeDetachedView(victim, false);
//                        }
//                        continue;
//                    }
//
//                    if (multipleScraps) {
//                        scrapViews = mScrapViews[whichScrap];
//                    }
////                    victim.dispatchStartTemporaryDetach();
//                    lp.scrappedFromPosition = mFirstActivePosition + i;
//                    scrapViews.add(victim);
//
//                    if (hasListener) {
//                        mRecyclerListener.onMovedToScrapHeap(victim);
//                    }
//                }
//            }
//
//            pruneScrapViews();
//        }
//
//        /**
//         * Makes sure that the size of mScrapViews does not exceed the size of mActiveViews.
//         * (This can happen if an adapter does not recycle its views).
//         */
//        private void pruneScrapViews() {
//            final int maxViews = mActiveViews.size();
//            final int viewTypeCount = mViewTypeCount;
//            final ArrayList<View>[] scrapViews = mScrapViews;
//            for (int i = 0; i < viewTypeCount; ++i) {
//                final ArrayList<View> scrapPile = scrapViews[i];
//                int size = scrapPile.size();
//                final int extras = size - maxViews;
//                size--;
//                for (int j = 0; j < extras; j++) {
//                    removeDetachedView(scrapPile.remove(size--), false);
//                }
//            }
//        }
//
//        /**
//         * Puts all views in the scrap heap into the supplied list.
//         */
//        void reclaimScrapViews(List<View> views) {
//            if (mViewTypeCount == 1) {
//                views.addAll(mCurrentScrap);
//            } else {
//                final int viewTypeCount = mViewTypeCount;
//                final ArrayList<View>[] scrapViews = mScrapViews;
//                for (int i = 0; i < viewTypeCount; ++i) {
//                    final ArrayList<View> scrapPile = scrapViews[i];
//                    views.addAll(scrapPile);
//                }
//            }
//        }
//
//        /**
//         * Updates the cache color hint of all known views.
//         *
//         * @param color The new cache color hint.
//         */
//        void setCacheColorHint(int color) {
//            if (mViewTypeCount == 1) {
//                final ArrayList<View> scrap = mCurrentScrap;
//                final int scrapCount = scrap.size();
//                for (int i = 0; i < scrapCount; i++) {
//                    scrap.get(i).setDrawingCacheBackgroundColor(color);
//                }
//            } else {
//                final int typeCount = mViewTypeCount;
//                for (int i = 0; i < typeCount; i++) {
//                    final ArrayList<View> scrap = mScrapViews[i];
//                    final int scrapCount = scrap.size();
//                    for (int j = 0; j < scrapCount; j++) {
//                        scrap.get(j).setDrawingCacheBackgroundColor(color);
//                    }
//                }
//            }
//            // Just in case this is called during a layout pass
//            final ArrayList<View> activeViews = mActiveViews;
//            final int count = activeViews.size();
//            for (int i = 0; i < count; ++i) {
//                final View victim = activeViews.get(i);
//                if (victim != null) {
//                    victim.setDrawingCacheBackgroundColor(color);
//                }
//            }
//        }
//    }
//    
//    static View retrieveFromScrap(ArrayList<View> scrapViews, int position) {
//        int size = scrapViews.size();
//        if (size > 0) {
//            // See if we still have a view for this position.
//            for (int i=0; i<size; i++) {
//                View view = scrapViews.get(i);
//                if (((ViewFlow.LayoutParams)view.getLayoutParams())
//                        .scrappedFromPosition == position) {
//                    scrapViews.remove(i);
//                    return view;
//                }
//            }
//            return scrapViews.remove(size - 1);
//        } else {
//            return null;
//        }
//    }
//}
