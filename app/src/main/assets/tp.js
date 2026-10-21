(function () {
  if (window.__tp) { window.__tp.scan(); return; }

  const ARTICLE = 'article[data-testid="tweet"]';
  const sel = new Set();

  const style = document.createElement('style');
  style.textContent = `
    .tp-btn{position:absolute;right:10px;bottom:10px;z-index:5;width:30px;height:30px;
      border-radius:15px;border:none;padding:0;background:#f4212e;
      color:#fff;font:bold 20px/30px sans-serif;text-align:center;
      box-shadow:0 1px 3px rgba(0,0,0,.35);animation:tp-pulse 2.8s ease-out infinite}
    @keyframes tp-pulse{
      0%{box-shadow:0 1px 3px rgba(0,0,0,.35),0 0 0 0 rgba(244,33,46,.45)}
      45%,100%{box-shadow:0 1px 3px rgba(0,0,0,.35),0 0 0 10px rgba(244,33,46,0)}}
    @media (prefers-reduced-motion:reduce){.tp-btn{animation:none}}
    .tp-sel{outline:3px solid #f4212e !important;outline-offset:-3px}
    .tp-sel .tp-btn{animation:none;transform:scale(1.08)}
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

  // X draws a reply chain as a thin vertical line under the upper tweet's avatar.
  function connectsDown(a) {
    const av = a.querySelector('[data-testid="Tweet-User-Avatar"]');
    if (!av) return false;
    const r = av.getBoundingClientRect();
    return [...a.querySelectorAll('div')].some(d => {
      const q = d.getBoundingClientRect();
      return q.width > 0 && q.width <= 4 && q.height > 8 &&
        q.top >= r.bottom - 2 && q.left >= r.left && q.right <= r.right;
    });
  }

  // One link per conversation: the last selected tweet of each reply chain.
  function chainLinks(arts) {
    if (/\/status\/\d+/.test(location.pathname)) return [urlOf(arts[arts.length - 1])];
    const all = [...document.querySelectorAll(ARTICLE)];
    const links = [];
    arts.forEach((a, i) => {
      if (i > 0) {
        const from = all.indexOf(arts[i - 1]), to = all.indexOf(a);
        if (all.slice(from, to).every(connectsDown)) links.pop();
      }
      links.push(urlOf(a));
    });
    return links;
  }

  function swallow(e) { e.stopPropagation(); }

  function scan() {
    document.querySelectorAll(ARTICLE).forEach(a => {
      const u = urlOf(a);
      const on = !!u && sel.has(u);
      a.classList.toggle('tp-sel', on);
      const old = a.querySelector(':scope > .tp-btn');
      if (old) { old.textContent = on ? '✓' : '+'; return; }
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
    });
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
      dpr: devicePixelRatio, urls: chainLinks(arts),
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
