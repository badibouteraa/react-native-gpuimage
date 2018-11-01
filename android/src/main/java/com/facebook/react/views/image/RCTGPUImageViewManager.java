package com.facebook.react.views.image;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.controller.AbstractDraweeControllerBuilder;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * Created by lvbingru on 6/24/16.
 */
public class RCTGPUImageViewManager extends SimpleViewManager<RCTGPUImageView> {

    public static final String REACT_CLASS = "RCTGPUImageView";

    private @Nullable AbstractDraweeControllerBuilder mDraweeControllerBuilder;

    public RCTGPUImageViewManager() {
    }

    public AbstractDraweeControllerBuilder getDraweeControllerBuilder() {
        if (mDraweeControllerBuilder == null) {
            mDraweeControllerBuilder = Fresco.newDraweeControllerBuilder();
        }
        return mDraweeControllerBuilder;
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    public RCTGPUImageView createViewInstance(ThemedReactContext context) {
        return new RCTGPUImageView(
                context);
    }

    final static int COMMAND_CAPTURE = 1;

    @Override
    public Map<String,Integer> getCommandsMap() {
        return MapBuilder.of(
                "capture",
                COMMAND_CAPTURE
        );
    }

    @Override
    public void receiveCommand(
            RCTGPUImageView view,
            int commandType,
            @Nullable ReadableArray args) {
        switch (commandType) {
            case COMMAND_CAPTURE: {
                System.out.println("receiveCommand/**************/COMMAND_CAPTURE//"+commandType);
                view.doCapture();
            }
        }
    }


    @ReactProp(name = "filters")
    public void setFilters(RCTGPUImageView view, @Nullable ReadableArray filters) {
        view.setFilters(filters);
    }

    // In JS this is Image.props.source.uri
    @ReactProp(name = "src")
    public void setSource(RCTGPUImageView view, @Nullable String source) {
        view.setSource(source);
    }


    @Override
    public @Nullable Map getExportedCustomDirectEventTypeConstants() {
        return MapBuilder.of(
                "topGetSize", MapBuilder.of("registrationName", "onGetSize"),
                "topCaptureFailed", MapBuilder.of("registrationName", "onCaptureFailed"),
                "topCaptureDone", MapBuilder.of("registrationName", "onCaptureDone")
        );
    }

}
