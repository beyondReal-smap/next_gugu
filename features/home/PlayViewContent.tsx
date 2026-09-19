"use client";

import { useEffect } from 'react';
import { trackMetaEvent } from '@/src/utils/metaPixel';

/** /play/ 진입 시 표준 ViewContent 1회. */
export function PlayViewContent() {
  useEffect(() => {
    trackMetaEvent('ViewContent', {
      content_name: 'web_play',
      content_category: 'gugu_adventure',
    });
  }, []);
  return null;
}
