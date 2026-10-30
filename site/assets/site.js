/* tv-hero website: reveal on scroll, hub spokes, and a light dotted wave behind the hero. No libraries, no tracking. */
(function () {
  var root = document.documentElement;
  root.classList.add('js');
  var still = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;

  // Nav hairline once the page scrolls.
  var nav = document.querySelector('nav.top');
  if (nav) { var onScroll = function () { nav.classList.toggle('scrolled', scrollY > 8); }; addEventListener('scroll', onScroll, { passive: true }); onScroll(); }

  // Reveal sections (and the time ring) as they come into view.
  var items = document.querySelectorAll('.reveal, .hub, .panel');
  if ('IntersectionObserver' in window && !still) {
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } });
    }, { threshold: 0.14, rootMargin: '0px 0px -40px 0px' });
    items.forEach(function (el) { io.observe(el); });
  } else items.forEach(function (el) { el.classList.add('in'); });

  // Hub: dashed spokes from the core to every module, with a light pulse travelling along some of them.
  var hub = document.querySelector('.hub'), spokes = hub && hub.querySelector('.spokes');
  if (hub && spokes) {
    var ns = 'http://www.w3.org/2000/svg';
    hub.querySelectorAll('.mod').forEach(function (m, i) {
      var x = parseFloat(m.style.getPropertyValue('--x')) * 10, y = parseFloat(m.style.getPropertyValue('--y')) * 5.6;
      [false, i % 2 === 0].forEach(function (pulse) {
        if (pulse === false || pulse) {
          var l = document.createElementNS(ns, 'line');
          l.setAttribute('x1', 500); l.setAttribute('y1', 280); l.setAttribute('x2', x); l.setAttribute('y2', y);
          if (pulse) { l.setAttribute('class', 'pulse'); l.style.animationDelay = (-i * 0.7) + 's'; }
          spokes.appendChild(l);
        }
      });
      m.style.transitionDelay = (0.15 + i * 0.06) + 's';
    });
  }

  // Dotted wave: a perspective grid of dots drawn on a 2D canvas, paused when off screen.
  var c = document.getElementById('dots');
  if (!c || !c.getContext) return;
  var ctx = c.getContext('2d'), w = 0, h = 0, dpr = Math.min(devicePixelRatio || 1, 2), t = 0, visible = true, raf = 0;
  function size() { w = c.clientWidth; h = c.clientHeight; c.width = w * dpr; c.height = h * dpr; ctx.setTransform(dpr, 0, 0, dpr, 0, 0); }
  function draw() {
    ctx.clearRect(0, 0, w, h);
    var cols = w < 700 ? 46 : 80, rows = 26, horizon = h * 0.06;
    for (var r = 0; r < rows; r++) {
      var z = (r + 1) / rows, persp = z * z;                   // 0 far → 1 near
      var y0 = horizon + persp * (h - horizon) * 1.05;
      var spread = 0.35 + persp * 1.35;
      for (var k = 0; k < cols; k++) {
        var u = k / (cols - 1) - 0.5;
        var x = w / 2 + u * w * spread;
        if (x < -10 || x > w + 10) continue;
        var wave = Math.sin(u * 9 + t * 0.9 + r * 0.45) * Math.cos(r * 0.35 - t * 0.6) * (4 + persp * 16);
        var y = y0 + wave;
        var a = 0.05 + persp * 0.42;
        ctx.fillStyle = 'rgba(165,180,255,' + a.toFixed(3) + ')';
        ctx.beginPath(); ctx.arc(x, y, 0.5 + persp * 1.5, 0, 6.283); ctx.fill();
      }
    }
  }
  function loop() { t += 0.016; draw(); raf = visible ? requestAnimationFrame(loop) : 0; }
  size(); draw();
  addEventListener('resize', function () { size(); draw(); });
  if (still) return;
  if ('IntersectionObserver' in window) {
    new IntersectionObserver(function (es) { visible = es[0].isIntersecting; if (visible && !raf) loop(); }).observe(c);
  }
  loop();
})();
