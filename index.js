/**
 * Created by lvbingru on 6/21/16.
 */
// GPUImageView.js

import React, {
  Component,
} from 'react';
import PropTypes from 'prop-types';

import {
  requireNativeComponent, Platform, NativeModules, Image,
  UIManager,
  findNodeHandle,
} from 'react-native'
const GPUImageViewManager = NativeModules.GPUImageViewManager;

class GPUImageView extends Component {

  render() {
    if (Platform.OS === 'ios') {
      return <RCTGPUImageView {...this.props} />;
    }
    else {
      var source = this.props.source;
      var loadingIndicatorSource = this.props.loadingIndicatorSource;

      // As opposed to the ios version, here it render `null`
      // when no source or source.uri... so let's not break that.

      if (source && source.uri === '') {
        console.warn('source.uri should not be an empty string');
      }

      if (source && source.uri) {
        var style = this.props.style;
        var {onLoadStart, onLoad, onLoadEnd} = this.props;

        var nativeProps = {...this.props,
          style,
          shouldNotifyLoadEvents: !!(onLoadStart || onLoad || onLoadEnd),
          src: source.uri,
          loadingIndicatorSrc: loadingIndicatorSource ? loadingIndicatorSource.uri : null,
        };
        return <RCTGPUImageView {...nativeProps} />;
      }
      else {
        return null
      }
    }
  }

  componentDidUpdate(prevProps) {
    if(this.props.master && (prevProps.filters[0].name !== this.props.filters[0].name)) {
      this.capture();
    }
  }

  async capture() {
    if (Platform.OS === 'ios'){
      await GPUImageViewManager.capture(findNodeHandle(this));
    } else {
      await UIManager.dispatchViewManagerCommand(findNodeHandle(this), 1, null);
    }
  }
}

GPUImageView.propTypes = {
  onCaptureDone: PropTypes.func,
  onCaptureFailed: PropTypes.func,
  master: PropTypes.bool,
  filters: PropTypes.array,
  onGetSize : PropTypes.func,
  ...Image.propTypes,
};

var cfg = {
  nativeOnly: {
  },
};

if (Platform.OS === 'android') {
  cfg.nativeOnly = {
    ...cfg.nativeOnly,
    src: true,
    loadingIndicatorSrc: true,
    defaultImageSrc: true,
    imageTag: true,
    progressHandlerRegistered: true,
    shouldNotifyLoadEvents: true,
    onCaptureDone: true,
    onCaptureFailed: true,
    onGetSize : true,
  }
}

var RCTGPUImageView = requireNativeComponent('RCTGPUImageView', GPUImageView, cfg)


export default GPUImageView;