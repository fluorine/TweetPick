(function () {
  if (window.__tp) { window.__tp.scan(); return; }

  const ARTICLE = 'article[data-testid="tweet"]';
  const sel = new Set();

  const style = document.createElement('style');
  style.textContent = `
    .tp-btn{position:absolute;top:8px;left:8px;z-index:5;width:26px;height:26px;
      border-radius:13px;border:2px solid #fff;padding:0;background:#1d9bf0;
      color:#fff;font:bold 18px/22px sans-serif;text-align:center;
      box-shadow:0 1px 3px rgba(0,0,0,.4)}
    .tp-sel{outline:3px solid #1d9bf0 !important;outline-offset:-3px}
    .tp-sel .tp-btn{background:#f4212e}
    html.tp-cap .tp-btn{display:none !important}
    html.tp-cap .tp-sel{outline:none !important}
    .tp-hide{visibility:hidden !important}`;
  document.documentElement.appendChild(style);

  function urlOf(a) {
    const t = a.querySelector('a[href*="/status/"] time');
    const link = t ? t.closest('a') : a.querySelector('a[href*="/status/"]');
    if (!link) return null;
    const m = link.getAttribute('href').match(/^\/([^/]+)\/status\/(\d+)/);
    return m ? 'https://x.com/' + m[1] + '/status/' + m[2] : null;
  }

  function selected() {
    return [...document.querySelectorAll(ARTICLE)].filter(a => sel.has(urlOf(a)));
  }

  function swallow(e) { e.stopPropagation(); }

  function scan() {
    document.querySelectorAll(ARTICLE).forEach(a => {
      const u = urlOf(a);
      a.classList.toggle('tp-sel', !!u && sel.has(u));
      const old = a.querySelector(':scope > .tp-btn');
      if (old) return place(a, old);
      if (getComputedStyle(a).position === 'static') a.style.position = 'relative';
      const b = document.createElement('button');
      b.className = 'tp-btn';
      b.textContent = '+';
      ['pointerdown', 'pointerup', 'mousedown', 'mouseup', 'touchstart', 'touchend']
        .forEach(t => b.addEventListener(t, swallow));
      b.addEventListener('click', e => {
        e.preventDefault();
        e.stopPropagation();
        const url = urlOf(a);
        if (!url) return;
        sel.has(url) ? sel.delete(url) : sel.add(url);
        scan();
        Android.onSelection(sel.size);
      });
      a.appendChild(b);
      place(a, b);
    });
  }

  // Badge on the avatar's lower-right corner: the one spot never under text or buttons.
  function place(a, b) {
    const av = a.querySelector('[data-testid="Tweet-User-Avatar"]');
    if (!av) return;
    const ar = a.getBoundingClientRect(), r = av.getBoundingClientRect();
    if (!r.width) return;
    b.style.left = (r.right - ar.left - 18) + 'px';
    b.style.top = (r.bottom - ar.top - 18) + 'px';
  }

  function restore() {
    document.documentElement.classList.remove('tp-cap');
    document.querySelectorAll('.tp-hide').forEach(e => e.classList.remove('tp-hide'));
  }

  function prepare() {
    let arts = selected();
    if (!sel.size) return Android.onCaptureError('Tap + on one or more tweets first.');
    if (arts.length < sel.size)
      return Android.onCaptureError('Some selected tweets are not loaded. Scroll to them.');
    const first = arts[0];
    document.documentElement.classList.add('tp-cap');
    document.querySelectorAll('body *').forEach(el => {
      if (el.contains(first)) return;
      const p = getComputedStyle(el).position;
      if (p === 'fixed' || p === 'sticky') el.classList.add('tp-hide');
    });
    let t = Infinity, b = -Infinity, l = Infinity, r = -Infinity;
    arts.forEach(a => {
      const q = a.getBoundingClientRect();
      t = Math.min(t, q.top); b = Math.max(b, q.bottom);
      l = Math.min(l, q.left); r = Math.max(r, q.right);
    });
    Android.onRect(JSON.stringify({
      x: l + scrollX, y: t + scrollY, w: r - l, h: b - t,
      dpr: devicePixelRatio, urls: arts.map(urlOf),
      bg: getComputedStyle(document.body).backgroundColor
    }));
  }

  // Reports the real scroll position, which the page clamps at its bottom.
  function scrollToY(y) {
    window.scrollTo(0, y);
    requestAnimationFrame(() => requestAnimationFrame(() =>
      Android.onScrolled(window.scrollY, window.innerHeight)));
  }

  function done(clear) {
    restore();
    if (clear) sel.clear();
    scan();
    Android.onSelection(sel.size);
  }

  function clearSel() { sel.clear(); scan(); Android.onSelection(0); }

  function handle() {
    const a = document.querySelector('a[data-testid="AppTabBar_Profile_Link"]');
    const m = a && a.getAttribute('href').match(/^\/([^/?]+)/);
    return m ? m[1] : '';
  }

  let queued = false;
  new MutationObserver(() => {
    if (queued) return;
    queued = true;
    requestAnimationFrame(() => { queued = false; scan(); });
  }).observe(document.documentElement, { childList: true, subtree: true });
  window.__tp = { scan, prepare, scrollToY, done, clearSel, handle };
  scan();
  Android.onSelection(sel.size);
})();
