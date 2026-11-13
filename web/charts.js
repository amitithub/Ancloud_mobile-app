// Custom interactive SVG charts matching Android Canvas CashflowChart & CategoryDonut
import { formatCurrency } from "./analytics.js";

function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

export function renderCashflowChart(container, dailyTrend, currency) {
  if (!container) return;
  if (!dailyTrend || dailyTrend.length === 0) {
    container.innerHTML = `
      <div class="empty-state-box">
        <span class="empty-icon" aria-hidden="true">📊</span>
        <p>Add transactions to visualize cashflow trends.</p>
      </div>
    `;
    return;
  }

  const points = dailyTrend.slice(-14);
  const maxVal = Math.max(
    10,
    ...points.map((p) => Math.max(p.income || 0, p.expense || 0))
  );

  const width = 560;
  const height = 220;
  const padLeft = 10;
  const padRight = 10;
  const padTop = 16;
  const padBottom = 30;
  const plotW = width - padLeft - padRight;
  const plotH = height - padTop - padBottom;
  const slotW = plotW / points.length;
  const barW = Math.min(18, Math.max(6, slotW * 0.3));
  const gap = Math.max(2, barW * 0.25);

  const gridLines = [0.25, 0.5, 0.75, 1]
    .map((frac) => {
      const y = padTop + plotH * (1 - frac);
      return `<line x1="${padLeft}" y1="${y.toFixed(1)}" x2="${width - padRight}" y2="${y.toFixed(1)}" class="chart-grid-line" />`;
    })
    .join("");

  const barsSvg = points
    .map((pt, idx) => {
      const cx = padLeft + slotW * idx + slotW / 2;
      const incH = Math.max(pt.income > 0 ? 3 : 0, (pt.income / maxVal) * plotH);
      const expH = Math.max(pt.expense > 0 ? 3 : 0, (pt.expense / maxVal) * plotH);
      const incX = cx - barW - gap / 2;
      const expX = cx + gap / 2;
      const incY = padTop + plotH - incH;
      const expY = padTop + plotH - expH;
      const dayLabel = pt.date.slice(5);
      const showLabel = points.length <= 8 || idx % 2 === 0 || idx === points.length - 1;

      return `
        <g class="cashflow-group" data-idx="${idx}" tabindex="0" role="button" aria-label="Date ${escapeHtml(pt.date)}: Income ${escapeHtml(formatCurrency(pt.income, currency))}, Expense ${escapeHtml(formatCurrency(pt.expense, currency))}">
          <rect x="${(padLeft + slotW * idx).toFixed(1)}" y="${padTop}" width="${slotW.toFixed(1)}" height="${plotH}" fill="transparent" />
          <rect x="${incX.toFixed(1)}" y="${incY.toFixed(1)}" width="${barW.toFixed(1)}" height="${incH.toFixed(1)}" rx="4" class="bar-income" />
          <rect x="${expX.toFixed(1)}" y="${expY.toFixed(1)}" width="${barW.toFixed(1)}" height="${expH.toFixed(1)}" rx="4" class="bar-expense" />
          ${
            showLabel
              ? `<text x="${cx.toFixed(1)}" y="${height - 8}" text-anchor="middle" class="chart-axis-label">${escapeHtml(dayLabel)}</text>`
              : ""
          }
        </g>
      `;
    })
    .join("");

  container.innerHTML = `
    <div class="cashflow-wrap">
      <div class="cashflow-tooltip" id="cashflow-tooltip" hidden></div>
      <svg viewBox="0 0 ${width} ${height}" class="cashflow-svg" role="img" aria-label="Cashflow trend grouped bar chart">
        ${gridLines}
        <line x1="${padLeft}" y1="${padTop + plotH}" x2="${width - padRight}" y2="${padTop + plotH}" class="chart-base-line" />
        ${barsSvg}
      </svg>
    </div>
  `;

  const tooltip = container.querySelector("#cashflow-tooltip");
  const groups = container.querySelectorAll(".cashflow-group");

  const showPoint = (idx) => {
    const pt = points[idx];
    if (!pt || !tooltip) return;
    groups.forEach((g) => g.classList.toggle("active", Number(g.dataset.idx) === idx));
    tooltip.hidden = false;
    tooltip.innerHTML = `
      <span class="tt-date">${escapeHtml(pt.date)}</span>
      <span class="tt-inc">+${escapeHtml(formatCurrency(pt.income, currency))}</span>
      <span class="tt-exp">-${escapeHtml(formatCurrency(pt.expense, currency))}</span>
    `;
  };

  groups.forEach((group) => {
    const idx = Number(group.dataset.idx);
    group.addEventListener("mouseenter", () => showPoint(idx));
    group.addEventListener("click", () => showPoint(idx));
    group.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        showPoint(idx);
      }
    });
  });

  container.addEventListener("mouseleave", () => {
    groups.forEach((g) => g.classList.remove("active"));
    if (tooltip) tooltip.hidden = true;
  });
}

export function renderCategoryDonut(container, breakdown, totalExpense, currency) {
  if (!container) return;
  if (!breakdown || breakdown.length === 0 || totalExpense <= 0) {
    container.innerHTML = `
      <div class="empty-state-box">
        <span class="empty-icon" aria-hidden="true">🍩</span>
        <p>No expense data for this period yet.</p>
      </div>
    `;
    return;
  }

  let selectedIdx = 0;
  const size = 200;
  const cx = size / 2;
  const cy = size / 2;
  const radius = 74;
  const circumference = 2 * Math.PI * radius;

  let offsetAcc = 0;
  const slices = breakdown.map((item, idx) => {
    const frac = Math.max(0, item.amount / totalExpense);
    const dashLen = frac * circumference;
    const gapLen = circumference - dashLen;
    const strokeOffset = -offsetAcc;
    offsetAcc += dashLen;
    return { ...item, idx, dashLen, gapLen, strokeOffset };
  });

  const renderInner = () => {
    const active = slices[selectedIdx] || slices[0];
    const arcsSvg = slices
      .map(
        (s) => `
        <circle
          cx="${cx}"
          cy="${cy}"
          r="${radius}"
          fill="none"
          stroke="${escapeHtml(s.color)}"
          stroke-width="${s.idx === selectedIdx ? 26 : 20}"
          stroke-dasharray="${s.dashLen.toFixed(2)} ${s.gapLen.toFixed(2)}"
          stroke-dashoffset="${s.strokeOffset.toFixed(2)}"
          class="donut-slice ${s.idx === selectedIdx ? "active" : ""}"
          data-idx="${s.idx}"
          tabindex="0"
          role="button"
          aria-label="${escapeHtml(s.label)}: ${escapeHtml(formatCurrency(s.amount, currency))} (${s.percentage}%)"
        />`
      )
      .join("");

    const legendHtml = slices
      .map(
        (s) => `
        <button type="button" class="donut-legend-item ${s.idx === selectedIdx ? "active" : ""}" data-idx="${s.idx}">
          <span class="legend-left">
            <span class="legend-dot" style="background:${escapeHtml(s.color)}"></span>
            <span class="legend-name">${escapeHtml(s.label)}</span>
          </span>
          <span class="legend-right mono">
            <strong>${escapeHtml(formatCurrency(s.amount, currency))}</strong>
            <span class="muted">${s.percentage.toFixed(1)}%</span>
          </span>
        </button>`
      )
      .join("");

    container.innerHTML = `
      <div class="donut-layout">
        <div class="donut-stage">
          <svg viewBox="0 0 ${size} ${size}" class="donut-svg" role="img" aria-label="Spending by category donut chart">
            <circle cx="${cx}" cy="${cy}" r="${radius}" fill="none" class="donut-track" stroke-width="20" />
            <g transform="rotate(-90 ${cx} ${cy})">
              ${arcsSvg}
            </g>
          </svg>
          <div class="donut-center">
            <span class="donut-center-label">${escapeHtml(active.label)}</span>
            <strong class="donut-center-value mono">${escapeHtml(formatCurrency(active.amount, currency))}</strong>
            <span class="donut-center-pct mono">${active.percentage.toFixed(1)}% of spend</span>
          </div>
        </div>
        <div class="donut-legend">${legendHtml}</div>
      </div>
    `;

    container.querySelectorAll("[data-idx]").forEach((el) => {
      const idx = Number(el.dataset.idx);
      const activate = () => {
        selectedIdx = idx;
        renderInner();
      };
      el.addEventListener("click", activate);
      el.addEventListener("mouseenter", () => {
        if (selectedIdx !== idx) {
          selectedIdx = idx;
          renderInner();
        }
      });
    });
  };

  renderInner();
}
