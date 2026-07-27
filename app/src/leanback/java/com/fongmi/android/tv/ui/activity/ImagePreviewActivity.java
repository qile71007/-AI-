
package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityImagePreviewBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;

import java.io.File;

public class ImagePreviewActivity extends BaseActivity {

    private ActivityImagePreviewBinding mBinding;
    private ImageView mImageView;
    private Matrix mMatrix = new Matrix();
    private Matrix mSavedMatrix = new Matrix();
    private PointF mStartPoint = new PointF();
    private PointF mMidPoint = new PointF();
    private float mOldDist = 1f;
    private static final int NONE = 0, DRAG = 1, ZOOM = 2;
    private int mMode = NONE;

    public static void start(Activity activity, String url) {
        Intent intent = new Intent(activity, ImagePreviewActivity.class);
        intent.putExtra("url", url);
        activity.startActivity(intent);
    }

    @Override protected ViewBinding getBinding() { return mBinding = ActivityImagePreviewBinding.inflate(getLayoutInflater()); }

    @Override protected void initView(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mImageView = mBinding.image;
        mImageView.setScaleType(ImageView.ScaleType.MATRIX);
        mImageView.setOnClickListener(v -> finish());

        String url = getIntent().getStringExtra("url");
        if (url != null) {
            if (url.startsWith("http")) Glide.with(this).load(url).into(mImageView);
            else Glide.with(this).load(new File(url)).into(mImageView);
        }

        mImageView.setOnTouchListener((v, event) -> {
            ImageView view = (ImageView) v;
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    mSavedMatrix.set(mMatrix);
                    mStartPoint.set(event.getX(), event.getY());
                    mMode = DRAG;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    mOldDist = spacing(event);
                    if (mOldDist > 10f) {
                        mSavedMatrix.set(mMatrix);
                        midPoint(mMidPoint, event);
                        mMode = ZOOM;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    mMode = NONE;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mMode == DRAG) {
                        mMatrix.set(mSavedMatrix);
                        mMatrix.postTranslate(event.getX() - mStartPoint.x, event.getY() - mStartPoint.y);
                    } else if (mMode == ZOOM) {
                        float newDist = spacing(event);
                        if (newDist > 10f) {
                            mMatrix.set(mSavedMatrix);
                            float scale = newDist / mOldDist;
                            mMatrix.postScale(scale, scale, mMidPoint.x, mMidPoint.y);
                        }
                    }
                    break;
            }
            view.setImageMatrix(mMatrix);
            return true;
        });
    }

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, MotionEvent event) {
        point.set((event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2);
    }
}
