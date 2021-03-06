/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.glrenderer;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.opengl.GLUtils;
import android.util.Log;

import com.mediatek.gallery3d.util.TraceHelper;

import junit.framework.Assert;

import java.util.HashMap;

import javax.microedition.khronos.opengles.GL11;

// UploadedTextures use a Bitmap for the content of the texture.
//
// Subclasses should implement onGetBitmap() to provide the Bitmap and
// implement onFreeBitmap(mBitmap) which will be called when the Bitmap
// is not needed anymore.
//
// isContentValid() is meaningful only when the isLoaded() returns true.
// It means whether the content needs to be updated.
//
// The user of this class should call recycle() when the texture is not
// needed anymore.
//
// By default an UploadedTexture is opaque (so it can be drawn faster without
// blending). The user or subclass can override it using setOpaque().
public abstract class UploadedTexture extends BasicTexture {

    // To prevent keeping allocation the borders, we store those used borders here.
    // Since the length will be power of two, it won't use too much memory.
    private static HashMap<BorderKey, Bitmap> sBorderLines =
            new HashMap<BorderKey, Bitmap>();
    private static BorderKey sBorderKey = new BorderKey();

    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/Texture";
    private boolean mContentValid = true;

    // indicate this textures is being uploaded in background
    private boolean mIsUploading = false;
    private boolean mOpaque = true;
    private boolean mThrottled = false;
    private static int sUploadedCount;
    private static final int UPLOAD_LIMIT = 100;

    protected Bitmap mBitmap;
    private int mBorder;

    protected UploadedTexture() {
        this(false);
    }

    protected UploadedTexture(boolean hasBorder) {
        super(null, 0, STATE_UNLOADED);
        if (hasBorder) {
            setBorder(true);
            mBorder = 1;
        }
    }

    protected void setIsUploading(boolean uploading) {
        mIsUploading = uploading;
    }

    public boolean isUploading() {
        return mIsUploading;
    }

    private static class BorderKey implements Cloneable {
        public boolean vertical;
        public Config config;
        public int length;

        @Override
        public int hashCode() {
            int x = config.hashCode() ^ length;
            return vertical ? x : -x;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof BorderKey)) return false;
            BorderKey o = (BorderKey) object;
            return vertical == o.vertical
                    && config == o.config && length == o.length;
        }

        @Override
        public BorderKey clone() {
            try {
                return (BorderKey) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    protected void setThrottled(boolean throttled) {
        mThrottled = throttled;
    }

    private static Bitmap getBorderLine(
            boolean vertical, Config config, int length) {
        BorderKey key = sBorderKey;
        key.vertical = vertical;
        key.config = config;
        key.length = length;
        Bitmap bitmap = sBorderLines.get(key);
        if (bitmap == null) {
            bitmap = vertical
                    ? Bitmap.createBitmap(1, length, config)
                    : Bitmap.createBitmap(length, 1, config);
            sBorderLines.put(key.clone(), bitmap);
        }
        return bitmap;
    }

    private Bitmap getBitmap() {
        if (mBitmap == null) {
            mBitmap = onGetBitmap();
            if (mBitmap == null) return null;
            int w = mBitmap.getWidth() + mBorder * 2;
            int h = mBitmap.getHeight() + mBorder * 2;
            if (mWidth == UNSPECIFIED) {
                setSize(w, h);
            }
        }
        return mBitmap;
    }

    private void freeBitmap() {
    	/*prize 17884 wanzhijuan 2016-6-29 start*/
//        Assert.assertTrue(mBitmap != null);
        if (mBitmap == null) {
        	return;
        }
        /*prize 17884 wanzhijuan 2016-6-29 end*/
        onFreeBitmap(mBitmap);
        mBitmap = null;
    }

    @Override
    public int getWidth() {
        if (mWidth == UNSPECIFIED) getBitmap();
        return mWidth;
    }

    @Override
    public int getHeight() {
        if (mWidth == UNSPECIFIED) getBitmap();
        return mHeight;
    }

    protected abstract Bitmap onGetBitmap();

    protected abstract void onFreeBitmap(Bitmap bitmap);

    protected void invalidateContent() {
        if (mBitmap != null) freeBitmap();
        mContentValid = false;
        mWidth = UNSPECIFIED;
        mHeight = UNSPECIFIED;
    }

    /**
     * Whether the content on GPU is valid.
     */
    public boolean isContentValid() {
        return isLoaded() && mContentValid;
    }

    /**
     * Updates the content on GPU's memory.
     * @param canvas
     */
    public void updateContent(GLCanvas canvas) {
        if (!isLoaded()) {
            if (mThrottled && ++sUploadedCount > UPLOAD_LIMIT) {
                return;
            }
            /// M: [DEBUG.ADD] @{
            TraceHelper.traceBegin(">>>>UploadedTexture-uploadToCanvas");
            /// @}
            try {
                uploadToCanvas(canvas);
            } catch (RuntimeException e) {
                mContentValid = true;
                e.printStackTrace();
            }
            /// M: [DEBUG.ADD] @{
            TraceHelper.traceEnd();
            /// @}
        } else if (!mContentValid) {
            Bitmap bitmap = getBitmap();
            int format = GLUtils.getInternalFormat(bitmap);
            int type = GLUtils.getType(bitmap);
            canvas.texSubImage2D(this, mBorder, mBorder, bitmap, format, type);
            freeBitmap();
            mContentValid = true;
        }
    }

    public static void resetUploadLimit() {
        sUploadedCount = 0;
    }

    public static boolean uploadLimitReached() {
        return sUploadedCount > UPLOAD_LIMIT;
    }

    private void uploadToCanvas(GLCanvas canvas) {

        Bitmap bitmap = getBitmap();
        if (bitmap != null && !bitmap.isRecycled()) {
            try {
                int bWidth = bitmap.getWidth();
                int bHeight = bitmap.getHeight();
                int width = bWidth + mBorder * 2;
                int height = bHeight + mBorder * 2;
                int texWidth = getTextureWidth();
                int texHeight = getTextureHeight();

                /// M: [DEBUG.ADD] @{
                if (bWidth > texWidth || bHeight > texHeight) {
                    Log.d(TAG, "<uploadToCanvas> bWidth=" + bWidth
                            + ", bHeight=" + bHeight + ", texWidth=" + texWidth
                            + ", texHeight=" + texHeight);
                }
                /// @}
                Assert.assertTrue(bWidth <= texWidth && bHeight <= texHeight);

                // Null pointer check here is to avoid monkey test failure.
                if (canvas.getGLId() != null) {
                    // Upload the bitmap to a new texture.
                    mId = canvas.getGLId().generateTexture();
                }
                canvas.setTextureParameters(this);

                if (bWidth == texWidth && bHeight == texHeight) {
                    /// M: [DEBUG.ADD] @{
                    TraceHelper.traceBegin(">>>>UploadedTexture-initializeTexture");
                    /// @}
                    canvas.initializeTexture(this, bitmap);
                    /// M: [DEBUG.ADD] @{
                    TraceHelper.traceEnd();
                    /// @}
                } else {
                    int format = GLUtils.getInternalFormat(bitmap);
                    int type = GLUtils.getType(bitmap);
                    Config config = bitmap.getConfig();

                    canvas.initializeTextureSize(this, format, type);
                    /// M: [DEBUG.ADD] @{
                    TraceHelper.traceBegin(">>>>UploadedTexture-texSubImage2D");
                    /// @}
                    canvas.texSubImage2D(this, mBorder, mBorder, bitmap, format, type);
                    /// M: [DEBUG.ADD] @{
                    TraceHelper.traceEnd();
                    /// @}

                    /*prize modify for gallery GL NE crash by liuwei 2018-06-05 begin*/
                    //if (mBorder > 0) {
                    if (false && mBorder > 0) {
                    /*prize modify for gallery GL NE crash by liuwei 2018-06-05 end*/

                        // Left border
                        Bitmap line = getBorderLine(true, config, texHeight);
                        /// M: [DEBUG.ADD] @{
                        TraceHelper.traceBegin(">>>>UploadedTexture-texSubImage2D");
                        /// @}
                        canvas.texSubImage2D(this, 0, 0, line, format, type);
                        /// M: [DEBUG.ADD] @{
                        TraceHelper.traceEnd();
                        /// @}

                        // Top border
                        line = getBorderLine(false, config, texWidth);
                        /// M: [DEBUG.ADD] @{
                        TraceHelper.traceBegin(">>>>UploadedTexture-texSubImage2D");
                        /// @}
                        canvas.texSubImage2D(this, 0, 0, line, format, type);
                        /// M: [DEBUG.ADD] @{
                        TraceHelper.traceEnd();
                        /// @}
                    }

                    // Right border
                    /*prize modify for gallery GL NE crash by liuwei 2018-06-05 begin*/
                    //if (mBorder + bWidth < texWidth) {
                    if (false && mBorder + bWidth < texWidth) {
                    /*prize modify for gallery GL NE crash by liuwei 2018-06-05 end*/

                        Bitmap line = getBorderLine(true, config, texHeight);
                        /// M: [DEBUG.ADD] @{
                        TraceHelper.traceBegin(">>>>UploadedTexture-texSubImage2D");
                        /// @}
                        canvas.texSubImage2D(this, mBorder + bWidth, 0, line, format, type);
                        /// M: [DEBUG.ADD] @{
                        TraceHelper.traceEnd();
                        /// @}
                    }

                    // Bottom border
                    /*prize modify for gallery GL NE crash by liuwei 2018-06-05 begin*/
                    //if (mBorder + bHeight < texHeight) {
                    if (false && mBorder + bHeight < texHeight) {
                    /*prize modify for gallery GL NE crash by liuwei 2018-06-05 end*/

                        Bitmap line = getBorderLine(false, config, texWidth);
                        /// M: [DEBUG.ADD] @{
                        TraceHelper.traceBegin(">>>>UploadedTexture-texSubImage2D");
                        /// @}
                        canvas.texSubImage2D(this, 0, mBorder + bHeight, line, format, type);
                        /// M: [DEBUG.ADD] @{
                        TraceHelper.traceEnd();
                        /// @}
                    }
                }
            } finally {
                /// M: [BUG.MODIFY] @{
                /* freeBitmap(); */
                // It's possible that mBitmap has been set as null in recycle() when
                // uploadToCanvas running, so we need to check if mBitmap == null before
                // freeBitmap, and synchronize it with recycle()
                synchronized (this) {
                    if (mBitmap != null) {
                        freeBitmap();
                    }
                }
                /// @}
            }
            // Update texture state.
            setAssociatedCanvas(canvas);
            mState = STATE_LOADED;
            mContentValid = true;
        } else {
            mState = STATE_ERROR;
            if (bitmap == null) {
                throw new RuntimeException("Texture load fail, no bitmap");
            }
        }
    }

    @Override
    protected boolean onBind(GLCanvas canvas) {
        updateContent(canvas);
        return isContentValid();
    }

    @Override
    protected int getTarget() {
        return GL11.GL_TEXTURE_2D;
    }

    public void setOpaque(boolean isOpaque) {
        mOpaque = isOpaque;
    }

    @Override
    public boolean isOpaque() {
        return mOpaque;
    }

    @Override
    public void recycle() {
        super.recycle();
        /// M: [BUG.ADD] @{
        synchronized (this) {
        /// @}
            if (mBitmap != null) {
                freeBitmap();
            }
        /// M: [BUG.ADD] @{
        }
        /// @}
    }
}
