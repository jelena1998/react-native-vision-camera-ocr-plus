import { VisionCameraProxy } from 'react-native-vision-camera';
import type { Frame, TextRecognitionPlugin, TextRecognitionOptions, Text } from './types';

const LINKING_ERROR = `Can't load plugin scanText. Try cleaning cache or reinstalling plugin.`;

export type Point = { x: number; y: number };

export function createTextRecognitionPlugin(
  options?: TextRecognitionOptions
): TextRecognitionPlugin {
  const plugin = VisionCameraProxy.initFrameProcessorPlugin('scanText', {
    ...options,
  });

  if (!plugin) {
    throw new Error(LINKING_ERROR);
  }

  return {
    scanText: (
      frame: Frame,
      points?: {
        topLeft?: Point;
        topRight?: Point;
        bottomRight?: Point;
        bottomLeft?: Point;
      }
    ): Text[] => {
      'worklet';
      if (
        !points?.topLeft ||
        !points?.topRight ||
        !points?.bottomRight ||
        !points?.bottomLeft
      ) {
        // @ts-ignore
        return plugin.call(frame) as Text[];
      }

      // @ts-ignore
      return plugin.call(frame, {
        topLeft: points.topLeft,
        topRight: points.topRight,
        bottomRight: points.bottomRight,
        bottomLeft: points.bottomLeft,
      }) as Text[];
    },
  };
}