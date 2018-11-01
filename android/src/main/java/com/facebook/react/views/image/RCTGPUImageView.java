/**
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 * <p>
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.react.views.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewTreeObserver;

import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.references.CloseableReference;
import com.facebook.common.util.UriUtil;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.util.Date;
import java.util.concurrent.Semaphore;

import javax.annotation.Nullable;

import jp.co.cyberagent.android.gpuimage.GPUImageFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageFilterGroup;
import jp.co.cyberagent.android.gpuimage.GPUImageTransformFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageView;

/**
 * Wrapper class around Fresco's GenericDraweeView, enabling persisting props across multiple view
 * update and consistent processing of both static and network images.
 */
public class RCTGPUImageView extends GPUImageView {

    private static final String TEMP_FILE_PREFIX = "gpuimage_capture_";

    private GPUImageFilterGroup mFilterGroup;

    private Bitmap savedBitmap;
    private String lastImageUri;

    /**
     * Instantiates a new GPUImage object.
     *
     * @param context the context
     */
    public RCTGPUImageView(Context context) {
        super(context);
    }

    private static File createTempFile(Context context)
            throws IOException {
        File externalCacheDir = context.getExternalCacheDir();
        File internalCacheDir = context.getCacheDir();
        System.out.println("externalCacheDir/**************/==>//" + externalCacheDir);
        System.out.println("internalCacheDir/**************/==>//" + internalCacheDir);
        File cacheDir;
        if (externalCacheDir == null && internalCacheDir == null) {
            throw new IOException("No cache directory available");
        }
        if (externalCacheDir == null) {
            cacheDir = internalCacheDir;
        } else if (internalCacheDir == null) {
            cacheDir = externalCacheDir;
        } else {
            cacheDir = externalCacheDir.getFreeSpace() > internalCacheDir.getFreeSpace() ?
                    externalCacheDir : internalCacheDir;
        }
        System.out.println("cacheDir/**************/==>//" + cacheDir);
        return File.createTempFile(TEMP_FILE_PREFIX, ".jpg", cacheDir);
    }

    private static Uri getResourceDrawableUri(Context context, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        name = name.toLowerCase().replace("-", "_");
        int resId = context.getResources().getIdentifier(
                name,
                "drawable",
                context.getPackageName());

        if (resId == 0) {
            return null;
        } else {
            return new Uri.Builder()
                    .scheme(UriUtil.LOCAL_RESOURCE_SCHEME)
                    .path(String.valueOf(resId))
                    .build();
        }
    }

    public static void savePic(Bitmap b, String strFileName) {
        FileOutputStream out;
        try {
            out = new FileOutputStream(strFileName);
            b.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setFilters(ReadableArray filters) {
        boolean needUpdate = false;
        int count = filters.size();

        ReadableMap[] filters0 = new ReadableMap[count];
        for (int i = 0; i < count; i++) {
            ReadableMap filterMap = filters.getMap(i);
            filters0[i] = filterMap;
        }

        if (mFilterGroup == null || mFilterGroup.getFilters().size() != count) {
            needUpdate = true;
        } else {
            for (int i = 0; i < count; i++) {
                ReadableMap filterMap = filters0[i];
                String name = filterMap.getString("name");
                GPUImageFilter filter = mFilterGroup.getFilters().get(i);
                if (!filter.getClass().getName().equals(name)) {
                    needUpdate = true;
                    break;
                }
            }
        }

        if (needUpdate) {
            mFilterGroup = new GPUImageFilterGroup();
            for (int i = 0; i < count; i++) {
                ReadableMap filterMap = filters0[i];
                try {
                    String name = filterMap.getString("name");
                    Class c = Class.forName("jp.co.cyberagent.android.gpuimage." + name);
                    GPUImageFilter imageFilter;
                    if (name.startsWith("IF")) {
                        Class[] cArg = new Class[1];
                        cArg[0] = Context.class;
                        imageFilter = (GPUImageFilter) c.getDeclaredConstructor(cArg).newInstance(getContext());
                    } else {
                        imageFilter = (GPUImageFilter) c.newInstance();
                    }
                    mFilterGroup.addFilter(imageFilter);
                } catch (Exception e) {
                    GPUImageFilter imageFilter = new GPUImageFilter();
                    mFilterGroup.addFilter(imageFilter);
                }
            }
            this.setFilter(mFilterGroup);
        }

        for (int i = 0; i < count; i++) {
            ReadableMap filterMap = filters0[i];
            if (filterMap.hasKey("params")) {
                ReadableMap params = filterMap.getMap("params");
                GPUImageFilter filter = mFilterGroup.getFilters().get(i);
                if (filter instanceof GPUImageTransformFilter) {
                    try {
                        ReadableArray transformA = params.getArray("transform3D");
                        int size = transformA.size();
                        float[] transform = new float[size];
                        for (int item = 0; item < size; item++) {
                            transform[item] = (float) transformA.getDouble(item);
                        }
                        ((GPUImageTransformFilter) filter).setTransform3D(transform);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    ReadableMapKeySetIterator interator = params.keySetIterator();
                    while (interator.hasNextKey()) {
                        String key = interator.nextKey();
                        String setter = "set" + key.substring(0, 1).toUpperCase() + key.substring(1);
                        try {
                            ReadableType type = params.getType(key);
                            if (type == ReadableType.Number) {
                                Method field = filter.getClass().getMethod(setter, Float.TYPE);
                                double number = params.getDouble(key);
                                field.invoke(filter, (float) number);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                }
            }
        }
    }

    public void setSource(final String imageUri) {
        lastImageUri = imageUri;
        if (imageUri != null) {
            _getImage(Uri.parse(imageUri), null, new ImageCallback() {
                @Override
                public void invoke(@Nullable Bitmap bitmap) {
                    if (imageUri.equals(lastImageUri)) {
                        savedBitmap = bitmap;
                        setImage(bitmap);
                        if (bitmap != null) {
                            int width = bitmap.getWidth();
                            int height = bitmap.getHeight();
                            WritableMap event = Arguments.createMap();
                            WritableMap size = Arguments.createMap();
                            size.putInt("width", width);
                            size.putInt("height", height);
                            event.putMap("size", size);
                            ReactContext reactContext = (ReactContext) getContext();
                            reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                                    getId(),
                                    "topGetSize",
                                    event);
                        }
                    }
                }
            });
        }
    }

    private void onCaptureSucceeded(String uri, int width, int height) {
        WritableMap event = Arguments.createMap();
        event.putString("uri", uri);
        event.putInt("width", width);
        event.putInt("height", height);
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                "topCaptureDone",
                event);
    }

    private void onCaptureFailed(String message) {
        WritableMap event = Arguments.createMap();
        event.putString("message", message);
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                "topCaptureFailed",
                event);
    }

    public void doCapture() {
        final int reactTag = getId();
        try {
            File externalCacheDir = getContext().getExternalCacheDir();
            File internalCacheDir = getContext().getCacheDir();
            File cacheDir;
            if (externalCacheDir == null && internalCacheDir == null) {
                throw new IOException("No cache directory available");
            }
            if (externalCacheDir == null) {
                cacheDir = internalCacheDir;
            } else if (internalCacheDir == null) {
                cacheDir = externalCacheDir;
            } else {
                cacheDir = externalCacheDir.getFreeSpace() > internalCacheDir.getFreeSpace() ?
                        externalCacheDir : internalCacheDir;
            }
            Bitmap bitmap = this.getGPUImage().getBitmapWithFilterApplied();
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            int narrowSize = Math.min(width, height);
            int differ = (int) Math.abs((bitmap.getHeight() - bitmap.getWidth()) / 2.0f);
            width = (width == narrowSize) ? 0 : differ;
            height = (width == 0) ? differ : 0;

            Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, width, height, narrowSize, narrowSize);
            bitmap.recycle();

            try {
                long timestamp = new Date().getTime();
                final String uri = cacheDir.toString() + "/capture_" + timestamp + ".jpg";
                savePic(resizedBitmap, uri);
                onCaptureSucceeded(uri, width, height);
            } catch (Throwable e) {
                onCaptureFailed(e.getMessage());
            }
        } catch (Throwable e) {
            onCaptureFailed(e.getMessage());
        }

    }

    private void _getImage(Uri uri, ResizeOptions resizeOptions, final ImageCallback imageCallback) {
        BaseBitmapDataSubscriber dataSubscriber = new BaseBitmapDataSubscriber() {
            @Override
            protected void onNewResultImpl(Bitmap bitmap) {
                bitmap = bitmap.copy(bitmap.getConfig(), true);
                imageCallback.invoke(bitmap);
            }

            @Override
            protected void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                imageCallback.invoke(null);
            }
        };

        ImageRequestBuilder builder = ImageRequestBuilder.newBuilderWithSource(uri);
        if (resizeOptions != null) {
            builder = builder.setResizeOptions(resizeOptions);
        }
        ImageRequest imageRequest = builder.build();

        ImagePipeline imagePipeline = Fresco.getImagePipeline();
        DataSource<CloseableReference<CloseableImage>> dataSource = imagePipeline.fetchDecodedImage(imageRequest, null);
        dataSource.subscribe(dataSubscriber, UiThreadImmediateExecutorService.getInstance());
    }

    private interface ImageCallback {
        void invoke(@Nullable Bitmap bitmap);
    }
}
