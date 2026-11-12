import { useCallback, useEffect, useState } from 'react';
import { interestsApi } from '../lib/api';

export default function useUnreadCount(enabled = true) {
  const [count, setCount] = useState(0);

  const refresh = useCallback(async () => {
    if (!enabled) return;
    try {
      const response = await interestsApi.unreadCount();
      setCount(Number(response?.data?.count ?? 0));
    } catch {
      /* a badge should never break the page */
    }
  }, [enabled]);

  useEffect(() => {
    if (!enabled) {
      setCount(0);
      return undefined;
    }

    refresh();
    const interval = window.setInterval(refresh, 60000);
    const onChange = () => refresh();
    window.addEventListener('eexchange:interests-changed', onChange);

    return () => {
      window.clearInterval(interval);
      window.removeEventListener('eexchange:interests-changed', onChange);
    };
  }, [enabled, refresh]);

  return { count, refresh, setCount };
}
