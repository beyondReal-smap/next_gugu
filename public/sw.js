// 구 next-pwa 서비스워커 킬 스위치 (self-destructing SW)
// 과거 버전이 사용자 기기에 등록해 둔 서비스워커가 이 파일로 업데이트되는 순간,
// 모든 캐시를 삭제하고 스스로 등록 해제한 뒤 열린 탭을 새로고침해
// 구버전 캐시가 서빙되는 문제를 영구히 청산한다. (원본은 smap_gugu/.archive/2026-07-05_legacy-sw/ 보존)
self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      try {
        const keys = await caches.keys();
        await Promise.all(keys.map((key) => caches.delete(key)));
      } catch (e) {
        // 캐시 삭제가 실패해도 등록 해제는 진행
      }
      await self.registration.unregister();
      const clients = await self.clients.matchAll({ type: 'window' });
      clients.forEach((client) => client.navigate(client.url));
    })()
  );
});
