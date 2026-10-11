(function () {
  'use strict';

  var META = {
    FAIL:  { label: '실패',     fg: '#9F2A14', bg: '#FBE6E1', dot: '#C2341B', issue: true },
    MISS:  { label: '미실행',   fg: '#7F3F00', bg: '#FCE7D2', dot: '#D2690F', issue: true },
    DELAY: { label: '지연',     fg: '#6E5200', bg: '#FFF1C7', dot: '#C99A06', issue: true },
    CHECK: { label: '확인필요', fg: '#5B3291', bg: '#EFE7FA', dot: '#7E4CC2', issue: true },
    RUN:   { label: '진행중',   fg: '#1C4FA0', bg: '#E3ECFA', dot: '#2F6FD6', issue: false },
    WAIT:  { label: '대기',     fg: '#4A5260', bg: '#ECEEF1', dot: '#8A919C', issue: false },
    DONE:  { label: '완료',     fg: '#1F6B3F', bg: '#E2F2E8', dot: '#2E8B57', issue: false },
    NA:    { label: '해당없음', fg: '#6B7280', bg: '#F1F2F4', dot: '#B5BAC2', issue: false }
  };
  var CARD_KEYS = ['ALL', 'FAIL', 'MISS', 'DELAY', 'CHECK', 'RUN', 'WAIT', 'DONE'];
  var DOW = ['일', '월', '화', '수', '목', '금', '토'];
  var REFRESH_MS = 60000;

  // date: 화면에 보이는 영업일, picked: 사용자가 직접 고른 날짜 (없으면 서버가 오늘로 판단)
  var state = { date: null, picked: null, filter: 'ALL', sel: null, data: null };
  var $ = function (id) { return document.getElementById(id); };

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function fmt(n) { return n == null ? '–' : Number(n).toLocaleString('ko-KR'); }
  function pill(status) {
    var m = META[status];
    return '<span class="pill" style="color:' + m.fg + ';background:' + m.bg + ';--dot:' + m.dot + '">' + m.label + '</span>';
  }

  function api(path, opts) {
    return fetch(path, opts).then(function (r) {
      if (!r.ok) throw new Error(r.status + ' ' + path);
      return r.status === 204 || r.headers.get('content-length') === '0' ? null : r.json().catch(function () { return null; });
    });
  }

  // ---------- load ----------

  function load() {
    var q = state.picked ? '?date=' + state.picked : '';
    return api('/api/dashboard' + q).then(function (data) {
      state.data = data;
      state.date = data.bizDate;
      $('date').value = data.bizDate;
      var rows = data.rows;
      if (!rows.some(function (r) { return r.jobId === state.sel; })) {
        state.sel = rows.length ? rows[0].jobId : null;
      }
      document.querySelector('.live').classList.remove('stale');
      render();
      loadDetail();
    }).catch(function (e) {
      console.error(e);
      document.querySelector('.live').classList.add('stale');
    });
  }

  function loadDetail() {
    if (!state.sel) { $('detail').hidden = true; return; }
    api('/api/jobs/' + encodeURIComponent(state.sel) + '/history?date=' + state.date)
      .then(renderDetail)
      .catch(function (e) { console.error(e); });
  }

  // ---------- render ----------

  function render() {
    var d = state.data;
    var dt = new Date(d.bizDate + 'T00:00:00');
    $('bizDateChip').textContent = (d.bizDay ? '영업일' : '휴일') + ' · ' + d.bizDate + ' (' + DOW[dt.getDay()] + ')';
    $('now').textContent = d.now;
    renderCards(d);
    renderRows(d);
    renderChannels(d);
    renderIssues(d);
  }

  function renderCards(d) {
    $('cards').innerHTML = CARD_KEYS.map(function (k) {
      var m = META[k];
      var n = d.counts[k] || 0;
      var hot = m && m.issue && n > 0;
      var style = hot ? 'background:' + m.bg + ';border-color:' + m.dot : '';
      return '<button type="button" class="card' + (state.filter === k ? ' active' : '') + '" data-filter="' + k + '" style="' + style + '">'
        + '<span class="card-label">' + (k === 'ALL' ? '오늘 실행 대상' : m.label) + '</span>'
        + '<span class="card-num" style="color:' + (hot ? m.fg : 'inherit') + '">' + n + '</span>'
        + '</button>';
    }).join('');
  }

  function renderRows(d) {
    var f = state.filter;
    $('filterLabel').textContent = '· ' + (f === 'ALL' ? '전체' : META[f].label);
    var rows = d.rows.filter(function (r) { return f === 'ALL' || r.status === f; });
    if (!rows.length) {
      $('rows').innerHTML = '<p class="empty">해당 상태의 배치가 없습니다.</p>';
      return;
    }
    $('rows').innerHTML = rows.map(function (r) {
      var cls = 'grid-row' + (r.jobId === state.sel ? ' selected' : '') + (r.status === 'NA' ? ' na' : '');
      var diffCls = 'num' + (r.diffAlert ? ' alert' : (r.diffText === '–' ? ' dim' : ''));
      return '<button type="button" class="' + cls + '" data-job="' + esc(r.jobId) + '">'
        + '<span class="name-cell"><b>' + esc(r.name) + '</b><small>' + esc(r.jobId) + '</small></span>'
        + '<span>' + esc(r.channel) + '</span>'
        + '<span class="sub">' + esc(r.rule) + '</span>'
        + '<span class="mono">' + esc(r.plan) + '</span>'
        + '<span class="mono sub">' + esc(r.runText) + '</span>'
        + '<span>' + pill(r.status) + '</span>'
        + '<span class="num">' + fmt(r.processed) + '</span>'
        + '<span class="' + diffCls + '">' + esc(r.diffText) + '</span>'
        + '</button>';
    }).join('');
  }

  function renderChannels(d) {
    $('channels').innerHTML = d.channels.map(function (c) {
      var done = c.success + c.fail;
      var rate = done > 0 ? c.success / done * 100 : null;
      var low = rate !== null && rate < 98;
      var rateColor = rate === null ? '#6B7280' : (low ? '#7F3F00' : '#1F6B3F');
      return '<div class="ch">'
        + '<div class="ch-head"><b>' + esc(c.name) + '</b>'
        + '<span class="ch-rate" style="color:' + rateColor + '">' + (rate === null ? '발송 전' : '성공률 ' + rate.toFixed(1) + '%') + '</span></div>'
        + '<div class="bar"><div style="width:' + (rate || 0) + '%;background:' + (low ? '#D2690F' : '#2E8B57') + '"></div></div>'
        + '<div class="ch-nums"><span>대상 <strong>' + fmt(c.target) + '</strong></span>'
        + '<span>성공 <strong>' + fmt(c.success) + '</strong></span>'
        + '<span>실패 <strong style="color:#9F2A14">' + fmt(c.fail) + '</strong></span></div>'
        + '</div>';
    }).join('');
  }

  function renderIssues(d) {
    var open = d.issues.filter(function (i) { return !i.acked; }).length;
    $('openIssues').textContent = '미확인 ' + open + '건';
    if (!d.issues.length) {
      $('issues').innerHTML = '<p class="no-issue">감지된 이슈가 없습니다.</p>';
      return;
    }
    $('issues').innerHTML = d.issues.map(function (i) {
      return '<div class="issue' + (i.acked ? ' acked' : '') + '">'
        + '<span class="issue-time">' + esc(i.time) + '</span>'
        + '<div class="issue-body"><b>' + esc(i.title) + '</b><span>' + esc(i.desc) + '</span></div>'
        + '<button type="button" class="ack" data-issue="' + i.id + '" data-acked="' + i.acked + '">' + (i.acked ? '확인됨' : '확인') + '</button>'
        + '</div>';
    }).join('');
  }

  function renderDetail(history) {
    var r = state.data.rows.filter(function (x) { return x.jobId === state.sel; })[0];
    if (!r) { $('detail').hidden = true; return; }
    var m = META[r.status];
    var max = Math.max.apply(null, history.map(function (h) { return h.count; }).concat([1]));
    var last = history.length - 1;

    var bars = history.map(function (h, i) {
      var height = h.count > 0 ? Math.max(4, Math.round(h.count / max * 120)) : 2;
      var color = i === last ? (m.issue ? m.dot : '#2F6FD6') : '#B5C3D6';
      var label = h.count >= 10000 ? (h.count / 1000).toFixed(1) + 'k' : fmt(h.count);
      return '<div><span>' + label + '</span><i style="height:' + height + 'px;background:' + color + '"></i></div>';
    }).join('');
    var labels = history.map(function (h, i) {
      return '<span' + (i === last ? ' class="today"' : '') + '>' + esc(h.date) + '</span>';
    }).join('');

    $('detail').innerHTML =
      '<div class="d-info">'
      + '<div class="d-title"><small>' + esc(r.jobId) + ' · ' + esc(r.channel) + '</small><h2>' + esc(r.name) + '</h2><span>' + pill(r.status) + '</span></div>'
      + '<dl>'
      + '<dt>스케줄</dt><dd>' + esc(r.rule) + ' · 예정 ' + esc(r.plan) + '</dd>'
      + '<dt>허용 지연</dt><dd>시작 ' + r.toleranceMin + '분 · 소요 ' + r.expectedMin + '분</dd>'
      + '<dt>실행</dt><dd class="mono">' + esc(r.runText) + '</dd>'
      + '<dt>처리 건수</dt><dd>' + fmt(r.processed) + '건 (평소 ' + fmt(r.avg) + '건)</dd>'
      + '<dt>담당자</dt><dd>' + esc(r.owner) + '</dd>'
      + '</dl>'
      + (r.reason ? '<div class="err"><strong>' + (r.status === 'FAIL' ? '에러 메시지' : '사유') + '</strong><span>' + esc(r.reason) + '</span></div>' : '')
      + '</div>'
      + '<div class="d-chart">'
      + '<div class="d-chart-head"><h3>최근 ' + history.length + '영업일 처리 건수</h3><span class="hint">휴일은 건너뜀</span></div>'
      + '<div class="table-scroll"><div class="bars" style="--n:' + history.length + '">' + bars + '</div>'
      + '<div class="bar-labels" style="--n:' + history.length + '">' + labels + '</div></div>'
      + '</div>';
    $('detail').hidden = false;
  }

  // ---------- events ----------

  $('cards').addEventListener('click', function (e) {
    var b = e.target.closest('[data-filter]');
    if (!b) return;
    state.filter = b.getAttribute('data-filter');
    renderCards(state.data);
    renderRows(state.data);
  });

  $('rows').addEventListener('click', function (e) {
    var b = e.target.closest('[data-job]');
    if (!b) return;
    state.sel = b.getAttribute('data-job');
    renderRows(state.data);
    loadDetail();
  });

  $('issues').addEventListener('click', function (e) {
    var b = e.target.closest('[data-issue]');
    if (!b) return;
    var acked = b.getAttribute('data-acked') !== 'true';
    api('/api/issues/' + b.getAttribute('data-issue') + '/ack', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ acked: acked })
    }).then(load);
  });

  $('date').addEventListener('change', function (e) {
    if (!e.target.value) return;
    state.picked = e.target.value;
    load();
  });

  load();
  setInterval(load, REFRESH_MS);
})();
