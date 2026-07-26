import {
  ResponsiveContainer,
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from "recharts";

const PALETTE = ["#3b82f6", "#ef4444", "#10b981", "#f59e0b", "#8b5cf6"];

/** chart.series[].points[]({label,value})를 Recharts용 단일 데이터셋으로 합친다 */
function toChartRows(series) {
  const labels = [...new Set(series.flatMap((s) => s.points.map((p) => p.label)))];
  return labels.map((label) => {
    const row = { label };
    for (const s of series) {
      const point = s.points.find((p) => p.label === label);
      row[s.name] = point ? point.value : null;
    }
    return row;
  });
}

export default function ChartPanel({ chart }) {
  const rows = toChartRows(chart.series);
  const Chart = chart.chartType === "bar" ? BarChart : LineChart;

  return (
    <div className="rounded-xl border border-slate-200 p-3 lg:p-4">
      <p className="mb-2 text-xs font-medium text-slate-500 lg:text-sm">
        {chart.chartType === "bar" ? "비교" : "추이"}
      </p>
      <div className="h-56 w-full lg:h-72">
        <ResponsiveContainer width="100%" height="100%">
          {/*
            Y축 눈금은 축 폭 안에 오른쪽 정렬로 그려진다. 왼쪽 여백을 음수로 당기거나 폭을
            좁게 잡으면 라벨 앞글자가 잘려나가는데, 잘리는 첫 글자가 하필 마이너스 부호다
            (괴리율 -0.4 → "0.4"). 종가처럼 여섯 자리 수도 담아야 하므로 넉넉히 잡는다.
          */}
          <Chart data={rows} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
            <XAxis
              dataKey="label"
              tick={{ fontSize: 11, fill: "#94a3b8" }}
              axisLine={{ stroke: "#e2e8f0" }}
              tickLine={false}
            />
            <YAxis
              tick={{ fontSize: 11, fill: "#94a3b8" }}
              axisLine={false}
              tickLine={false}
              width={64}
            />
            <Tooltip
              contentStyle={{
                fontSize: 12,
                borderRadius: 8,
                border: "1px solid #e2e8f0",
              }}
            />
            {chart.series.length > 1 && <Legend wrapperStyle={{ fontSize: 12 }} />}
            {chart.series.map((s, i) =>
              chart.chartType === "bar" ? (
                <Bar key={s.name} dataKey={s.name} fill={PALETTE[i % PALETTE.length]} />
              ) : (
                <Line
                  key={s.name}
                  type="monotone"
                  dataKey={s.name}
                  stroke={PALETTE[i % PALETTE.length]}
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 4 }}
                />
              )
            )}
          </Chart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
